package com.app.bartwidget.testutil

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.io.Closeable

/**
 * BART's etd.aspx on a local server, answering with departures recorded from the real API
 * (etd-<station>.json in test resources). Stations in [failing] answer 500; [requests] lists
 * the stations asked for.
 */
class FakeBart : Closeable {
    val failing = mutableSetOf<String>()
    val requests: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    private val server = MockWebServer()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                val orig = url.queryParameter("orig") ?: return MockResponse(code = 400)
                requests += orig
                if (url.encodedPath != "/api/etd.aspx" || url.queryParameter("json") != "y" || url.queryParameter("key").isNullOrEmpty()) {
                    return MockResponse(code = 400)
                }
                if (orig in failing) return MockResponse(code = 500)
                return MockResponse(body = fixture("etd-${orig.lowercase()}.json") ?: NO_TRAINS.replace("ABBR", orig))
            }
        }
        server.start()
    }

    val base: String get() = server.url("/").toString().removeSuffix("/")

    override fun close() = server.close()

    companion object {
        private const val NO_TRAINS = """{"root":{"station":[{"abbr":"ABBR","message":""}]}}"""

        fun fixture(name: String): String? = FakeBart::class.java.classLoader!!.getResource(name)?.readText()
    }
}
