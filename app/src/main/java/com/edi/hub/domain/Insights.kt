package com.edi.hub.domain

import com.edi.hub.data.dao.RunOutCandidate
import com.edi.hub.data.model.PantryItem
import java.time.Instant
import java.time.LocalDate

/**
 * Everything the rules are allowed to ask the database. Narrow on purpose: a rule that can run any
 * query is a rule nobody can test.
 */
interface Queries {
    suspend fun expiringThrough(through: LocalDate): List<PantryItem>

    suspend fun ranOutSince(since: Instant): List<RunOutCandidate>
}

/** One card on Today. Two variants, because two rules are live. */
sealed interface Insight {
    val id: String

    /** The day this card comes back, or null while it has never been snoozed. */
    val snoozedUntil: LocalDate?

    data class ExpiringSoon(val item: PantryItem) : Insight {
        override val id = "expiring-${item.id}"
        override val snoozedUntil = item.snoozedUntil
    }

    data class RanOut(val candidate: RunOutCandidate) : Insight {
        override val id = "ranout-${candidate.barcode}"
        override val snoozedUntil = candidate.snoozedUntil
    }
}

/**
 * Snoozed means out of the way until the date arrives, not gone: Today can still show it on
 * demand, and the nudge stays quiet about it. Kept here rather than in SQL so the screen and the
 * worker cannot drift apart on what "snoozed" means.
 */
fun Insight.isSnoozed(today: LocalDate = LocalDate.now()): Boolean =
    snoozedUntil?.isAfter(today) == true

/** Anything already gone off, or going off inside the week. */
const val EXPIRY_HORIZON_DAYS = 7L

/** How long a finished product stays on Today as a restock hint before it stops being news. */
const val RUN_OUT_HOLD_DAYS = 7L

suspend fun expiringSoon(queries: Queries, today: LocalDate = LocalDate.now()): List<Insight> =
    queries.expiringThrough(today.plusDays(EXPIRY_HORIZON_DAYS))
        .map(Insight::ExpiringSoon)

suspend fun ranOut(queries: Queries, now: Instant = Instant.now()): List<Insight> =
    queries.ranOutSince(now.minusSeconds(RUN_OUT_HOLD_DAYS * 24 * 60 * 60))
        .map(Insight::RanOut)

/**
 * Not a rules engine. A list of functions, which is what a rules engine is before someone decides
 * it needs configuration.
 */
val insightRules: List<suspend (Queries) -> List<Insight>> = listOf(
    { queries -> expiringSoon(queries) },
    { queries -> ranOut(queries) },
)
