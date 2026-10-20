package com.app.carry

import android.content.Context

/**
 * A debug build from CI keeps itself current. One built locally has no build number to compare,
 * so it never does, as it never asks GitHub.
 */
internal fun selfUpdater(context: Context): SelfUpdater =
    DebugUpdates.buildOf(BuildConfig.VERSION_NAME)?.let { LiveUpdates(context.applicationContext, it) }
        ?: SelfUpdater.NONE
