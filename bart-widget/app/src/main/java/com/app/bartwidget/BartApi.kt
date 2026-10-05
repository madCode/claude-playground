package com.app.bartwidget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object BartApi {
    // BART's public key, published for anyone to use: https://api.bart.gov/docs/overview/
    private const val KEY = "MW9S-E7SL-26DU-VV8V"

    suspend fun departures(abbr: String, now: Long = System.currentTimeMillis()): List<Train> = withContext(Dispatchers.IO) {
        val url = URL("https://api.bart.gov/api/etd.aspx?cmd=etd&orig=$abbr&key=$KEY&json=y")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode != 200) error("BART answered ${conn.responseCode}")
            parseEtd(conn.inputStream.bufferedReader().readText(), now)
        } finally {
            conn.disconnect()
        }
    }
}
