package com.app.bartwidget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class BartApi(private val base: String) {
    suspend fun departures(abbr: String, now: Long): List<Train> = withContext(Dispatchers.IO) {
        val conn = URL("$base/api/etd.aspx?cmd=etd&orig=$abbr&key=$KEY&json=y").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode != 200) error("BART answered ${conn.responseCode}")
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
