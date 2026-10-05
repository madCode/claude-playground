package com.app.bartwidget

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The official BART app (com.app.bart) doesn't publish a link to its parking screen, so this opens
 * the app itself, one tap from Parking. Without the app, BART's parking page on the web.
 */
fun parkingIntent(context: Context): Intent =
    (context.packageManager.getLaunchIntentForPackage("com.app.bart")
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bart.gov/guide/parking")))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
