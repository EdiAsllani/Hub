package com.edi.hub

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.edi.hub.data.HubPrefs
import com.edi.hub.work.scheduleDailyNudge
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HubApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var prefs: HubPrefs

    /** The default WorkManagerInitializer is removed in the manifest so this one is used instead. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // On every launch, so a job cancelled during a restore re-establishes itself. Nothing to do
        // when the reminder is off: the switch cancels it, and touching WorkManager here would only
        // start it up at every cold launch for nothing.
        if (prefs.dailyReminder) scheduleDailyNudge(this)
    }
}
