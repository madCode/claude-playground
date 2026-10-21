package com.app.jekyllposter.update

import android.content.Context
import okhttp3.Call

/** A debug build from CI keeps itself current. */
internal fun selfUpdater(context: Context, installedBuild: Int, calls: Call.Factory, publishing: () -> Boolean): SelfUpdater =
    LiveUpdates(context.applicationContext, installedBuild, calls, publishing)
