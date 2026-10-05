package com.app.bartwidget.testutil

import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.app.bartwidget.AppContainer
import com.app.bartwidget.BartApp
import java.io.File
import java.time.ZonedDateTime
import java.util.TimeZone

/** 7:20 on a Monday morning in the Bay Area, when the fixtures were recorded. */
val MORNING: Long = ZonedDateTime.parse("2026-10-05T07:20:00-07:00[America/Los_Angeles]").toInstant().toEpochMilli()

/** The app against [FakeBart], with its own DataStore file, a clock tests move, and test WorkManager. */
class TestApp : BartApp() {
    val bart = FakeBart()
    var now = MORNING

    override fun onCreate() {
        // Clock times on screen are local; pin the zone so "7:20" means the same on any machine.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        super.onCreate()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            this,
            Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).setExecutor(SynchronousExecutor()).build(),
        )
    }

    override fun createContainer() = AppContainer(
        this,
        // A file of its own: a DataStore is a process-wide singleton per file and would carry one
        // test's stars into the next.
        dataStore = PreferenceDataStoreFactory.create {
            File.createTempFile("bart", ".preferences_pb").also { it.delete(); it.deleteOnExit() }
        },
        bartBase = bart.base,
        clock = { now },
    )
}
