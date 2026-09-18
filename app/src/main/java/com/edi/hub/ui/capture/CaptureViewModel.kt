package com.edi.hub.ui.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edi.hub.data.dao.PantryCard
import com.edi.hub.data.dao.PantryDao
import com.edi.hub.data.dao.ProductDao
import com.edi.hub.data.model.PantryItem
import com.edi.hub.data.model.PantryLocation
import com.edi.hub.data.model.Product
import com.edi.hub.data.model.ProductSource
import com.edi.hub.data.off.OpenFoodFacts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Where step 1 ended up. Three outcomes, and only one of them asks the user for a decision. */
sealed interface Identified {
    data object Scanning : Identified

    /** The lookup is in flight. Drawn as a skeleton of the card that is coming. */
    data object LookingUp : Identified

    /**
     * The barcode is already on a shelf. Hub stops here and waits for a tap, because this is the
     * one branch that needs a decision rather than a confirmation.
     */
    data class AlreadyHere(val card: PantryCard, val learnedShelfLifeDays: Int?) : Identified

    /** A hit on something new. Auto-advances, spending no tap on confirmation. */
    data class Found(val name: String, val brand: String?, val imageUrl: String?) : Identified

    /** A miss, a timeout, or a scan the user backed out of. Not an error; the naming step exists anyway. */
    data object Unknown : Identified
}

/** What the three steps are filling in between them. */
data class Draft(
    val barcode: String? = null,
    val name: String = "",
    val brand: String = "",
    val description: String = "",
    val imageUrl: String? = null,
    val location: PantryLocation = PantryLocation.FRIDGE,
    val expiresOn: LocalDate? = null,
    /** What Hub pre-filled. Only a change from these teaches it anything. */
    val prefilledExpiresOn: LocalDate? = null,
    val prefilledDescription: String? = null,
    val knownShelfLifeDays: Int? = null,
)

/** One saved item, and what it takes to undo it from the snackbar. */
data class Saved(val id: Long, val name: String, val restocked: Boolean)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val pantry: PantryDao,
    private val products: ProductDao,
    private val off: OpenFoodFacts,
) : ViewModel() {

    var identified by mutableStateOf<Identified>(Identified.Scanning)
        private set

    var draft by mutableStateOf(Draft())
        private set

    var saved by mutableStateOf<Saved?>(null)
        private set

    /** Set when a correction taught Hub something, and acknowledged in place rather than in a snackbar. */
    var learnedFromCorrection by mutableStateOf(false)
        private set

    fun identify(barcode: String?) {
        if (barcode == null) {
            // Backed out of the scanner. Step 2 is still a sensible place to land.
            identified = Identified.Unknown
            return
        }
        draft = draft.copy(barcode = barcode)
        identified = Identified.LookingUp
        viewModelScope.launch {
            val known = products.find(barcode)
            val open = pantry.cardsForBarcode(barcode)
            if (open.isNotEmpty()) {
                identified = Identified.AlreadyHere(open.first(), known?.defaultShelfLifeDays)
                return@launch
            }

            val remote = if (known == null) off.lookUp(barcode) else null
            val name = known?.name ?: remote?.name
            if (name == null) {
                identified = Identified.Unknown
                return@launch
            }

            val brand = known?.brand ?: remote?.brand
            draft = draft.copy(
                name = name,
                brand = brand.orEmpty(),
                description = known?.defaultDescription.orEmpty(),
                prefilledDescription = known?.defaultDescription,
                imageUrl = known?.imageUrl ?: remote?.imageUrl,
                knownShelfLifeDays = known?.defaultShelfLifeDays,
            )
            identified = Identified.Found(name, brand, draft.imageUrl)
        }
    }

    /** The escape hatch out of the lookup: name it yourself rather than wait. */
    fun skipLookup() {
        identified = Identified.Unknown
    }

    fun edit(block: Draft.() -> Draft) {
        draft = draft.block()
    }

    /**
     * Step 3 opens pre-filled when Hub has learned a shelf life for this product and the product is
     * no longer on a shelf. A first scan opens empty, and the calendar or the field fills it in.
     */
    fun prepareDateStep(today: LocalDate = LocalDate.now()) {
        val days = draft.knownShelfLifeDays ?: return
        if (draft.expiresOn != null) return
        val learned = today.plusDays(days.toLong())
        draft = draft.copy(expiresOn = learned, prefilledExpiresOn = learned)
    }

    /**
     * The three-tap restock. The new row copies everything from the group it joins and takes the
     * learned shelf life for its date, or no date where nothing has been learned. No date step, and
     * no chance to get the shelf wrong.
     */
    fun restock(card: PantryCard, learnedShelfLifeDays: Int?, today: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            val id = pantry.insert(
                PantryItem(
                    barcode = card.barcode,
                    name = card.name,
                    brand = card.brand,
                    description = card.description,
                    location = card.location,
                    addedAt = Instant.now(),
                    expiresOn = learnedShelfLifeDays?.let { today.plusDays(it.toLong()) },
                ),
            )
            saved = Saved(id, card.name, restocked = true)
        }
    }

    /**
     * Saves the item and, only where the user changed something Hub had filled in, learns from it.
     * Accepting a pre-filled value teaches nothing — that is what keeps a wrong guess from being
     * confirmed into a fact.
     */
    fun save(today: LocalDate = LocalDate.now()) {
        val draft = draft
        if (draft.name.isBlank()) return
        viewModelScope.launch {
            val id = pantry.insert(
                PantryItem(
                    barcode = draft.barcode,
                    name = draft.name.trim(),
                    brand = draft.brand.trim().takeIf(String::isNotEmpty),
                    description = draft.description.trim().takeIf(String::isNotEmpty),
                    location = draft.location,
                    addedAt = Instant.now(),
                    expiresOn = draft.expiresOn,
                ),
            )
            draft.barcode?.let { learn(it, draft, today) }
            saved = Saved(id, draft.name.trim(), restocked = false)
        }
    }

    private suspend fun learn(barcode: String, draft: Draft, today: LocalDate) {
        val now = Instant.now()
        val existing = products.find(barcode)
        if (existing == null) {
            products.upsert(
                Product(
                    barcode = barcode,
                    name = draft.name.trim(),
                    brand = draft.brand.trim().takeIf(String::isNotEmpty),
                    defaultShelfLifeDays = draft.expiresOn?.let { ChronoUnit.DAYS.between(today, it).toInt() },
                    defaultDescription = draft.description.trim().takeIf(String::isNotEmpty),
                    imageUrl = draft.imageUrl,
                    source = if (draft.imageUrl == null) ProductSource.USER else ProductSource.OFF,
                    updatedAt = now,
                ),
            )
            return
        }

        val correctedDate = draft.expiresOn != null && draft.expiresOn != draft.prefilledExpiresOn
        val correctedDescription = draft.description.trim().takeIf(String::isNotEmpty)
            ?.takeIf { it != draft.prefilledDescription }
        if (!correctedDate && correctedDescription == null) return

        products.learn(
            barcode = barcode,
            days = if (correctedDate) ChronoUnit.DAYS.between(today, draft.expiresOn).toInt() else null,
            description = correctedDescription,
            updatedAt = now.toEpochMilli(),
        )
        // Only the date correction is acknowledged. Step 2 draws no callout, so a changed
        // description has nowhere to be acknowledged and is written silently.
        if (correctedDate && draft.prefilledExpiresOn != null) learnedFromCorrection = true
    }

    fun undo(saved: Saved) {
        viewModelScope.launch { pantry.delete(saved.id) }
    }
}
