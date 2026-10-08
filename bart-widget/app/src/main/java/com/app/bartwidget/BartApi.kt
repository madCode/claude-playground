package com.app.bartwidget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class BartApi(private val base: String) {
    suspend fun departures(abbr: String, now: Long): List<Train> = withContext(Dispatchers.IO) {
        val conn = URL("$base/api/etd.aspx?cmd=etd&orig=$abbr&key=$KEY&json=y").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode != 200) throw BartError(conn.responseCode)
            parseEtd(conn.inputStream.bufferedReader().readText(), now)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        // BART's public key, published for anyone to use: https://api.bart.gov/docs/overview/
        const val KEY = "MW9S-E7SL-26DU-VV8V"
    }
}

class BartError(val code: Int) : IOException("BART answered $code")

/**
 * Why a fetch failed, in words for the screen: whether to look at the phone or wait for BART.
 * Android blocking an app's network in the background shows up as a failed lookup or connect,
 * so it reads "No connection" like being offline.
 */
fun whyFailed(e: Throwable): String = when (e) {
    is BartError -> "BART error ${e.code}"
    is SocketTimeoutException -> "BART didn't answer"
    is IOException -> "No connection"
    else -> "Couldn't read BART's times"
}
