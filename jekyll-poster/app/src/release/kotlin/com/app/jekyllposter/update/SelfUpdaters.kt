package com.app.jekyllposter.update

import android.content.Context
import okhttp3.Call

/** A release build never updates itself: whatever installed it does that. */
@Suppress("UNUSED_PARAMETER")
internal fun selfUpdater(context: Context, installedBuild: Int, calls: Call.Factory, waitFor: () -> Hold?): SelfUpdater = SelfUpdater.NONE
