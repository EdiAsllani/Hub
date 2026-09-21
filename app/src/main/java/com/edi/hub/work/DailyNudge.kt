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
import com.edi.hub.MainActivity
import com.edi.hub.R
import com.edi.hub.data.dao.PantryDao
import com.edi.hub.data.dao.RunOutCandidate
import com.edi.hub.data.model.PantryItem
import com.edi.hub.domain.Insight
import com.edi.hub.domain.Queries
import com.edi.hub.domain.insightRules
import com.edi.hub.domain.isSnoozed
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** The dashboard is passive. Without one nudge a day the app gets forgotten, and that is the real risk. */
const val DAILY_NUDGE_WORK = "daily-nudge"

private const val CHANNEL_ID = "daily-summary"
private const val NOTIFICATION_ID = 1

/** Early enough to change what you eat today, late enough not to be an alarm clock. */
private val NUDGE_AT: LocalTime = LocalTime.of(8, 0)

@HiltWorker
class DailyNudgeWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters,
    private val pantry: PantryDao,
) : CoroutineWorker(context, parameters) {

    private val queries = object : Queries {
        override suspend fun expiringThrough(through: LocalDate): List<PantryItem> =
            pantry.expiringThrough(through.toEpochDay())

        override suspend fun ranOutSince(since: Instant): List<RunOutCandidate> =
            pantry.runOutCandidates(since.toEpochMilli())
    }

    override suspend fun doWork(): Result {
        // A card put aside on Today has to stay aside here too, or the button means nothing.
        val insights = insightRules.flatMap { rule -> rule(queries) }.filterNot { it.isSnoozed() }
        // Nothing to say is a good day, and saying so anyway is how a notification gets muted.
        if (insights.isEmpty()) return Result.success()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        notify(summarise(insights))
        return Result.success()
    }

    private fun summarise(insights: List<Insight>): String {
        val expiring = insights.filterIsInstance<Insight.ExpiringSoon>()
        val ranOut = insights.filterIsInstance<Insight.RanOut>()
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
        }
        return parts.joinToString(", ").replaceFirstChar(Char::uppercase) + "."
    }

    private fun notify(text: String) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Daily summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "One summary a day of what needs eating. Nothing else."
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
            .setContentTitle("Today in Hub")
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
fun scheduleDailyNudge(context: Context) {
    val request = PeriodicWorkRequestBuilder<DailyNudgeWorker>(Duration.ofDays(1))
        .setInitialDelay(untilNextNudge())
        .build()
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(DAILY_NUDGE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
}

fun cancelDailyNudge(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(DAILY_NUDGE_WORK)
}

private fun untilNextNudge(now: ZonedDateTime = ZonedDateTime.now()): Duration {
    val todaysNudge = now.with(NUDGE_AT)
    val next = if (todaysNudge.isAfter(now)) todaysNudge else todaysNudge.plusDays(1)
    return Duration.between(now, next)
}
