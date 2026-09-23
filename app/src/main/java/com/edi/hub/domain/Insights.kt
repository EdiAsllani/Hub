package com.edi.hub.domain

import com.edi.hub.data.dao.RunOutCandidate
import com.edi.hub.data.model.Deadline
import com.edi.hub.data.model.DeadlineKind
import com.edi.hub.data.model.PantryItem
import com.edi.hub.ui.theme.Urgency
import com.edi.hub.ui.theme.urgencyOf
import java.time.Instant
import java.time.LocalDate

interface Queries {
    suspend fun expiringThrough(through: LocalDate): List<PantryItem>
    suspend fun ranOutSince(since: Instant): List<RunOutCandidate>
    suspend fun deadlinesThrough(through: LocalDate): List<Deadline> = emptyList()
}

sealed interface Insight {
    val id: String
    val snoozedUntil: LocalDate?

    data class ExpiringSoon(val item: PantryItem) : Insight {
        override val id = "expiring-${item.id}"
        override val snoozedUntil = item.snoozedUntil
    }

    data class RanOut(val candidate: RunOutCandidate) : Insight {
        override val id = "ranout-${candidate.barcode}"
        override val snoozedUntil = candidate.snoozedUntil
    }

    data class DeadlineDue(val deadline: Deadline) : Insight {
        override val id = "deadline-${deadline.id}"
        override val snoozedUntil = deadline.snoozedUntil
    }
}

fun Insight.isSnoozed(today: LocalDate = LocalDate.now()): Boolean = snoozedUntil?.isAfter(today) == true

const val EXPIRY_HORIZON_DAYS = 7L
const val RUN_OUT_HOLD_DAYS = 7L
const val INSIGHT_CAP = 8
const val DEADLINE_HORIZON_DAYS = 90L

suspend fun expiringSoon(
    queries: Queries,
    today: LocalDate = LocalDate.now(),
    horizonDays: Long = EXPIRY_HORIZON_DAYS,
): List<Insight> = queries.expiringThrough(today.plusDays(horizonDays)).map(Insight::ExpiringSoon)

suspend fun ranOut(queries: Queries, now: Instant = Instant.now()): List<Insight> =
    queries.ranOutSince(now.minusSeconds(RUN_OUT_HOLD_DAYS * 24 * 60 * 60)).map(Insight::RanOut)

/** Warranty, document, bill and overdue lending cards. Upkeep and vehicle stay in their list. */
suspend fun deadlineInsights(queries: Queries, today: LocalDate = LocalDate.now()): List<Insight> =
    queries.deadlinesThrough(today.plusDays(DEADLINE_HORIZON_DAYS))
        .filter { deadline ->
            when (deadline.kind) {
                DeadlineKind.WARRANTY -> deadline.dueOn <= today.plusDays(30)
                DeadlineKind.DOCUMENT -> deadline.dueOn <= today.plusDays(90)
                DeadlineKind.BILL -> deadline.dueOn <= today.plusDays(7)
                DeadlineKind.LENDING -> deadline.counterparty != null && deadline.dueOn.isBefore(today)
                DeadlineKind.UPKEEP, DeadlineKind.VEHICLE -> false
            }
        }
        .map(Insight::DeadlineDue)

val insightRules: List<suspend (Queries) -> List<Insight>> = listOf(
    { queries -> expiringSoon(queries) },
    { queries -> ranOut(queries) },
    { queries -> deadlineInsights(queries) },
)

/** One stable ordering shared by Today and notifications, with warning cards first and eight max. */
fun rankAndCap(insights: List<Insight>, today: LocalDate = LocalDate.now()): List<Insight> =
    insights.sortedWith(compareBy<Insight>({ insight ->
        when (insight) {
            is Insight.ExpiringSoon -> urgencyOf(insight.item.expiresOn, today).rank
            is Insight.DeadlineDue -> deadlineUrgency(insight.deadline.kind, insight.deadline.dueOn, today).rank
            is Insight.RanOut -> Urgency.OK.rank
        }
    }, { insight ->
        when (insight) {
            is Insight.ExpiringSoon -> insight.item.expiresOn ?: LocalDate.MAX
            is Insight.DeadlineDue -> insight.deadline.dueOn
            is Insight.RanOut -> LocalDate.MAX
        }
    }, Insight::id)).take(INSIGHT_CAP)

private val Urgency.rank: Int get() = when (this) {
    Urgency.EXPIRED -> 0
    Urgency.CRITICAL -> 1
    Urgency.SOON -> 2
    Urgency.OK -> 3
    Urgency.NONE -> 4
}
