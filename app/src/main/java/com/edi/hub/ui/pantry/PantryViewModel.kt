package com.edi.hub.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edi.hub.data.dao.PantryCard
import com.edi.hub.data.dao.PantryDao
import com.edi.hub.data.model.Disposition
import com.edi.hub.data.model.PantryLocation
import com.edi.hub.ui.theme.Urgency
import com.edi.hub.ui.theme.urgencyOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The filters the chips offer. Every one of them narrows by date, because that is the only axis there is. */
enum class PantryFilter(val label: String) {
    ALL("Everything"),
    EXPIRED("Expired"),
    SOON("This week"),
    NO_DATE("No date"),
}

enum class PantrySort(val label: String) { BY_DATE("By date"), BY_NAME("By name") }

/**
 * Three different empty screens, because they are three different situations and one shared layout
 * would answer none of them well. `design/spec.md` §5.
 */
enum class PantryEmptiness { NONE, FIRST_LAUNCH, EMPTY_LOCATION, FILTER_MATCHES_NOTHING }

/** What the undo snackbar needs to put the entry back. */
data class Resolved(val id: Long, val name: String, val disposition: Disposition)

data class PantryUiState(
    val location: PantryLocation = PantryLocation.FRIDGE,
    val counts: Map<PantryLocation, Int> = emptyMap(),
    val cards: List<PantryCard> = emptyList(),
    val filter: PantryFilter = PantryFilter.ALL,
    val sort: PantrySort = PantrySort.BY_DATE,
    val emptiness: PantryEmptiness = PantryEmptiness.NONE,
    val today: LocalDate = LocalDate.now(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PantryViewModel @Inject constructor(
    private val pantry: PantryDao,
) : ViewModel() {

    private val location = MutableStateFlow(PantryLocation.FRIDGE)
    private val filter = MutableStateFlow(PantryFilter.ALL)
    private val sort = MutableStateFlow(PantrySort.BY_DATE)

    val state = combine(
        location,
        filter,
        sort,
        pantry.observeLocationCounts(),
        location.flatMapLatest(pantry::observeCards),
    ) { location, filter, sort, counts, cards ->
        val today = LocalDate.now()
        val byLocation = counts.associate { it.location to it.cards }
        val shown = cards.filter { filter.matches(it.expiresOn, today) }.let { matched ->
            when (sort) {
                PantrySort.BY_DATE -> matched
                PantrySort.BY_NAME -> matched.sortedBy { it.name.lowercase() }
            }
        }
        PantryUiState(
            location = location,
            counts = byLocation,
            cards = shown,
            filter = filter,
            sort = sort,
            emptiness = when {
                byLocation.values.sum() == 0 -> PantryEmptiness.FIRST_LAUNCH
                cards.isEmpty() -> PantryEmptiness.EMPTY_LOCATION
                shown.isEmpty() -> PantryEmptiness.FILTER_MATCHES_NOTHING
                else -> PantryEmptiness.NONE
            },
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PantryUiState())

    fun show(location: PantryLocation) {
        this.location.value = location
    }

    fun apply(filter: PantryFilter) {
        this.filter.value = filter
    }

    fun apply(sort: PantrySort) {
        this.sort.value = sort
    }

    /**
     * Resolves one entry of a card and hands back what is needed to undo it. Within a group it takes
     * the row with the earliest date, which is the date the card was showing — that is the property
     * the whole count model rests on.
     */
    fun resolve(card: PantryCard, disposition: Disposition, onResolved: (Resolved) -> Unit) {
        viewModelScope.launch {
            val id = pantry.resolveNext(card.location, card.groupKey, disposition) ?: return@launch
            onResolved(Resolved(id, card.name, disposition))
        }
    }

    fun undo(resolved: Resolved) {
        viewModelScope.launch { pantry.unresolve(resolved.id) }
    }
}

private fun PantryFilter.matches(expiresOn: LocalDate?, today: LocalDate): Boolean = when (this) {
    PantryFilter.ALL -> true
    PantryFilter.EXPIRED -> urgencyOf(expiresOn, today) == Urgency.EXPIRED
    PantryFilter.SOON -> urgencyOf(expiresOn, today) in setOf(Urgency.EXPIRED, Urgency.CRITICAL, Urgency.SOON)
    PantryFilter.NO_DATE -> expiresOn == null
}
