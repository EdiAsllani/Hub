package com.edi.hub.ui.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edi.hub.data.dao.DeadlineDao
import com.edi.hub.data.dao.PantryDao
import com.edi.hub.data.dao.ProductDao
import com.edi.hub.data.dao.RunOutCandidate
import com.edi.hub.data.model.Disposition
import com.edi.hub.data.model.PantryItem
import com.edi.hub.domain.Insight
import com.edi.hub.domain.Queries
import com.edi.hub.domain.insightRules
import com.edi.hub.domain.isSnoozed
import com.edi.hub.domain.rankAndCap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class TodayUiState(
    val queue: List<Insight> = emptyList(),
    /** Put aside until tomorrow, and counted so the board can offer them back. */
    val snoozed: List<Insight> = emptyList(),
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
    private val deadlines: DeadlineDao,
) : ViewModel() {

    private val queries = object : Queries {
        override suspend fun expiringThrough(through: LocalDate): List<PantryItem> =
            pantry.expiringThrough(through.toEpochDay())

        override suspend fun ranOutSince(since: Instant): List<RunOutCandidate> =
            pantry.runOutCandidates(since.toEpochMilli())

        override suspend fun deadlinesThrough(through: LocalDate) =
            deadlines.dueThrough(through.toEpochDay())
    }

    var state by mutableStateOf(TodayUiState())
        private set

    /**
     * Set by [revealSnoozed] and never unset, because [reload] runs on every visit to the tab and
     * would otherwise put the revealed cards straight back down on the way back from the pantry.
     * Dies with the ViewModel, so a cold start honours the snooze again.
     */
    private var revealed = false

    /**
     * Re-read on every visit. Without it, resolving something from the pantry leaves its card
     * sitting on Today: the ViewModel outlives the tab switch, so nothing else would notice.
     */
    // ponytail: the rules are pulled on every visit rather than driven by the DAO Flows. A Flow
    // pipeline would keep Today live while it is on screen; nothing in phase 1 needs that, because
    // the only writes that matter happen on a different tab. Upgrade if it starts to feel stale.
    fun reload() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val all = insightRules.flatMap { rule -> rule(queries) }
            val snoozed = if (revealed) emptyList() else all.filter { it.isSnoozed(today) }
            val due = rankAndCap(
                all.filterNot { !revealed && it.isSnoozed(today) },
                today,
            )
            state = state.copy(
                queue = due,
                snoozed = snoozed,
                pantryEmpty = pantry.observeActive().first().isEmpty() && deadlines.observeActive().first().isEmpty(),
                cleared = 0,
                loaded = true,
                today = today,
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
     * Out of the way until tomorrow, written down rather than remembered: a card put aside has to
     * stay aside across a restart, and the nudge has to stay quiet about it too. Snoozing resolves
     * nothing — the item is still in the pantry and the product is still run out.
     */
    fun snooze(today: LocalDate = LocalDate.now()) {
        val insight = state.current ?: return
        val until = today.plusDays(1)
        viewModelScope.launch {
            when (insight) {
                is Insight.ExpiringSoon -> pantry.snooze(insight.item.id, until.toEpochDay())
                is Insight.RanOut -> products.snooze(insight.candidate.barcode, until.toEpochDay())
                is Insight.DeadlineDue -> deadlines.snooze(insight.deadline.id, until.toEpochDay())
            }
            // Not clearCurrent: a card put aside was not dealt with, and counting it as cleared
            // would have the board claim credit for work that is still waiting.
            state = state.copy(queue = state.queue.drop(1), snoozed = state.snoozed + insight)
        }
    }

    /**
     * Snoozed is out of the way, not gone. One tap puts them back and they stay back while Hub is
     * open, which is what makes putting a card aside a cheap decision rather than one to think about.
     */
    fun revealSnoozed() {
        revealed = true
        state = state.copy(queue = state.queue + state.snoozed, snoozed = emptyList())
    }

    fun completeDeadline(deadline: com.edi.hub.data.model.Deadline) {
        viewModelScope.launch {
            deadlines.complete(deadline.id, LocalDate.now(), Instant.now())
            clearCurrent()
        }
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
