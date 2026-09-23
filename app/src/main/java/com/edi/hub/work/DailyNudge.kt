package com.edi.hub.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.edi.hub.MainActivity
import com.edi.hub.R
import com.edi.hub.data.dao.DeadlineDao
import com.edi.hub.data.dao.PantryDao
import com.edi.hub.data.dao.RunOutCandidate
import com.edi.hub.data.model.PantryItem
import com.edi.hub.domain.Insight
import com.edi.hub.domain.EXPIRY_HORIZON_DAYS
import com.edi.hub.domain.Queries
import com.edi.hub.domain.expiringSoon
import com.edi.hub.domain.deadlineInsights
import com.edi.hub.domain.isSnoozed
import com.edi.hub.domain.ranOut
import com.edi.hub.domain.rankAndCap
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The dashboard is passive. Without a nudge the app gets forgotten, and that is the real risk.
 *
 * Two a day, and deliberately not the same sentence twice: the morning one is the week ahead plus
 * what you have run out of, which is what you act on before a shop, and the evening one is only
 * what goes off today or tomorrow, which is what you act on before dinner. Identical repeats are
 * how a notification gets muted, so the horizon is what separates them.
 */
const val MORNING_NUDGE_WORK = "daily-nudge"
const val EVENING_NUDGE_WORK = "evening-nudge"

private const val CHANNEL_ID = "daily-summary"

/** One id, so the evening summary replaces the morning one rather than stacking beneath it. */
private const val NOTIFICATION_ID = 1

private const val KEY_HORIZON_DAYS = "horizonDays"
private const val KEY_RESTOCK = "restock"
private const val KEY_TITLE = "title"

/** Early enough to change what you eat today, late enough not to be an alarm clock. */
private val MORNING_AT: LocalTime = LocalTime.of(8, 0)

/** Late enough to be home, early enough that cooking the thing is still an option. */
private val EVENING_AT: LocalTime = LocalTime.of(18, 0)

/** What the evening nudge counts as urgent. Anything further out keeps until the morning. */
private const val TONIGHT_HORIZON_DAYS = 1L

@HiltWorker
class DailyNudgeWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters,
    private val pantry: PantryDao,
    private val deadlines: DeadlineDao,
) : CoroutineWorker(context, parameters) {

    private val queries = object : Queries {
        override suspend fun expiringThrough(through: LocalDate): List<PantryItem> =
            pantry.expiringThrough(through.toEpochDay())

        override suspend fun ranOutSince(since: Instant): List<RunOutCandidate> =
            pantry.runOutCandidates(since.toEpochMilli())

        override suspend fun deadlinesThrough(through: LocalDate) =
            deadlines.dueThrough(through.toEpochDay())
    }

    override suspend fun doWork(): Result {
        val horizon = inputData.getLong(KEY_HORIZON_DAYS, EXPIRY_HORIZON_DAYS)
        val restock = inputData.getBoolean(KEY_RESTOCK, true)
        val insights = rankAndCap(buildList {
            addAll(expiringSoon(queries, horizonDays = horizon))
            // Running out of eggs is a shopping errand, so it keeps until the morning.
            if (restock) addAll(ranOut(queries))
            val deadlineCards = deadlineInsights(queries)
            addAll(if (restock) deadlineCards else deadlineCards.filter {
                it is Insight.DeadlineDue &&
                    it.deadline.kind in setOf(
                        com.edi.hub.data.model.DeadlineKind.BILL,
                        com.edi.hub.data.model.DeadlineKind.LENDING,
                    ) && it.deadline.dueOn <= LocalDate.now().plusDays(TONIGHT_HORIZON_DAYS)
            })
            // A card put aside on Today has to stay aside here too, or the button means nothing.
        }.filterNot { it.isSnoozed() })
        // Nothing to say is a good day, and saying so anyway is how a notification gets muted.
        if (insights.isEmpty()) return Result.success()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        notify(inputData.getString(KEY_TITLE) ?: "Today in Hub", summarise(insights))
        return Result.success()
    }

    private fun summarise(insights: List<Insight>): String {
        val expiring = insights.filterIsInstance<Insight.ExpiringSoon>()
        val ranOut = insights.filterIsInstance<Insight.RanOut>()
        val deadlines = insights.filterIsInstance<Insight.DeadlineDue>()
        val parts = buildList {
            when (expiring.size) {
                0 -> Unit
                1 -> add("${expiring.single().item.name} needs eating")
                else -> add("${expiring.size} things need eating")
            }
            when (ranOut.size) {
                0 -> Unit
                1 -> add("you are out of ${ranOut.single().candidate.name.lowercase()}")
                else -> add("you are out of ${ranOut.size} things")
            }
            when (deadlines.size) {
                0 -> Unit
                1 -> add("${deadlines.single().deadline.name} is due")
                else -> add("${deadlines.size} deadlines need attention")
            }
        }
        return parts.joinToString(", ").replaceFirstChar(Char::uppercase) + "."
    }

    private fun notify(title: String, text: String) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Daily summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "What needs eating or is coming due, morning and evening."
            },
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}

/**
 * KEEP rather than REPLACE, and on every launch: a job cancelled during a restore re-establishes
 * itself the next time Hub opens, and one that is already scheduled is left where it is rather than
 * having its next run pushed a day out every time the app starts.
 */
fun scheduleNudges(context: Context) {
    val manager = WorkManager.getInstance(context)
    manager.enqueueUniquePeriodicWork(
        MORNING_NUDGE_WORK,
        ExistingPeriodicWorkPolicy.KEEP,
        nudge(
            at = MORNING_AT,
            horizonDays = EXPIRY_HORIZON_DAYS,
            restock = true,
            title = "Today in Hub",
        ),
    )
    manager.enqueueUniquePeriodicWork(
        EVENING_NUDGE_WORK,
        ExistingPeriodicWorkPolicy.KEEP,
        nudge(
            at = EVENING_AT,
            horizonDays = TONIGHT_HORIZON_DAYS,
            restock = false,
            title = "Before tonight",
        ),
    )
}

fun cancelNudges(context: Context) {
    val manager = WorkManager.getInstance(context)
    manager.cancelUniqueWork(MORNING_NUDGE_WORK)
    manager.cancelUniqueWork(EVENING_NUDGE_WORK)
}

private fun nudge(
    at: LocalTime,
    horizonDays: Long,
    restock: Boolean,
    title: String,
) = PeriodicWorkRequestBuilder<DailyNudgeWorker>(Duration.ofDays(1))
    .setInitialDelay(until(at))
    .setInputData(
        workDataOf(
            KEY_HORIZON_DAYS to horizonDays,
            KEY_RESTOCK to restock,
            KEY_TITLE to title,
        ),
    )
    .build()

private fun until(time: LocalTime, now: ZonedDateTime = ZonedDateTime.now()): Duration {
    val today = now.with(time)
    val next = if (today.isAfter(now)) today else today.plusDays(1)
    return Duration.between(now, next)
}
