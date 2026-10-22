package com.app.carry

import android.content.Context

/** A release build never updates itself: whatever installed it does that. */
@Suppress("UNUSED_PARAMETER")
internal fun selfUpdater(context: Context): SelfUpdater = SelfUpdater.NONE
