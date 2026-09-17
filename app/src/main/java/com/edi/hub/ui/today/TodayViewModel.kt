package com.edi.hub.ui.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edi.hub.data.dao.PantryDao
import com.edi.hub.data.dao.ProductDao
import com.edi.hub.data.dao.RunOutCandidate
import com.edi.hub.data.model.Disposition
import com.edi.hub.data.model.PantryItem
import com.edi.hub.domain.Insight
import com.edi.hub.domain.Queries
import com.edi.hub.domain.insightRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class TodayUiState(
    val queue: List<Insight> = emptyList(),
    val cleared: Int = 0,
    val loaded: Boolean = false,
    /** Whether the kitchen itself is empty, which is a different screen from an empty queue. */
    val pantryEmpty: Boolean = true,
    val today: LocalDate = LocalDate.now(),
) {
    val current: Insight? get() = queue.firstOrNull()
    val next: Insight? get() = queue.getOrNull(1)

    /** Two tallies under the check, so the cleared board is not a void. */
    val total: Int get() = queue.size + cleared
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val pantry: PantryDao,
    private val products: ProductDao,
) : ViewModel() {

    private val queries = object : Queries {
        override suspend fun expiringThrough(through: LocalDate): List<PantryItem> =
            pantry.expiringThrough(through.toEpochDay())

        override suspend fun ranOutSince(since: Instant): List<RunOutCandidate> =
            pantry.runOutCandidates(since.toEpochMilli())
    }

    var state by mutableStateOf(TodayUiState())
        private set

    /** Hidden for the rest of today, in memory only — "Not now" means tomorrow, not never. */
    private val notNow = mutableSetOf<String>()

    /**
     * Re-read on every visit. Without it, resolving something from the pantry leaves its card
     * sitting on Today: the ViewModel outlives the tab switch, so nothing else would notice.
     */
    // ponytail: the rules are pulled on every visit rather than driven by the DAO Flows. A Flow
    // pipeline would keep Today live while it is on screen; nothing in phase 1 needs that, because
    // the only writes that matter happen on a different tab. Upgrade if it starts to feel stale.
    fun reload() {
        viewModelScope.launch {
            val insights = insightRules
                .flatMap { rule -> rule(queries) }
                .filterNot { it.id in notNow }
            state = state.copy(
                queue = insights,
                pantryEmpty = pantry.observeActive().first().isEmpty(),
                cleared = 0,
                loaded = true,
            )
        }
    }

    /** Resolving from Today is the same write as the swipe, so the pantry agrees with the card. */
    fun resolve(item: PantryItem, disposition: Disposition) {
        viewModelScope.launch {
            pantry.resolve(item.id, Instant.now().toEpochMilli(), disposition)
            clearCurrent()
        }
    }

    fun gotIt(candidate: RunOutCandidate) {
        viewModelScope.launch {
            products.dismissRunOut(candidate.barcode, Instant.now().toEpochMilli())
            clearCurrent()
        }
    }

    /**
     * Left for tomorrow. An in-memory hide ships nothing and is the assumption until the owner says
     * otherwise; a `snoozedUntil` column is the alternative and is listed in `docs/plan.md` §8.
     */
    fun notNow() {
        state.current?.let { notNow += it.id }
        clearCurrent()
    }

    /**
     * The card that was on top is done with, whichever way. The rules are not re-run here: the write
     * that just happened is exactly what would remove it, and re-deriving mid-animation would make
     * the queue flicker under the card that is rising.
     */
    private fun clearCurrent() {
        state = state.copy(queue = state.queue.drop(1), cleared = state.cleared + 1)
    }

}
