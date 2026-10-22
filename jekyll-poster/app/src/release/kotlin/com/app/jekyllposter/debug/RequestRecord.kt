package com.app.jekyllposter.debug

import android.content.Context
import okhttp3.OkHttpClient

/** Release builds record nothing: the debug build's request record isn't in them. */
@Suppress("UNUSED_PARAMETER")
fun OkHttpClient.recordedAs(context: Context, via: String): OkHttpClient = this
