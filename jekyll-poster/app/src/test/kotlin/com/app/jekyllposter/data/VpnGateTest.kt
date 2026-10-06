package com.app.jekyllposter.data

import com.app.jekyllposter.core.github.NoVpnException
import com.app.jekyllposter.testutil.FakeVpn
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gate itself, below the app: plain JVM. */
class VpnGateTest {
    private val vpn = FakeVpn()
    @Volatile private var required = true
    private val gate = VpnGate(vpn) { required }
    private val server = MockWebServer().also { it.start() }

    @After fun close() = server.close()

    @Test fun namesAreLookedUpThroughTheVpnOrNotAtAll() {
        gate.dns().lookup("localhost")
        assertEquals(1, vpn.lookups)
        vpn.up.value = false
        assertThrows(NoVpnException::class.java) { gate.dns().lookup("localhost") }
        required = false
        gate.dns().lookup("localhost")
        assertEquals(1, vpn.lookups)
    }

    @Test fun noSocksProxyWhileTheVpnIsRequired() {
        val socks = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("proxy.example", 1080))
        val before = ProxySelector.getDefault()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI) = listOf(socks)
            override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit
        })
        try {
            assertEquals(listOf(Proxy.NO_PROXY), gate.proxySelector.select(URI("https://api.github.com/")))
            required = false
            assertEquals(listOf(socks), gate.proxySelector.select(URI("https://api.github.com/")))
        } finally {
            ProxySelector.setDefault(before)
        }
    }

    @Test fun aConnectionBusyWhenTheSwitchWentOnIsNeverUsedAgain() {
        required = false
        vpn.up.value = false
        val calls = GatedCalls(OkHttpClient(), gate)
        // A slow answer keeps the connection busy while the switch goes on...
        server.enqueue(MockResponse.Builder().body("slow").bodyDelay(500, TimeUnit.MILLISECONDS).build())
        server.enqueue(MockResponse.Builder().body("next").build())
        val busy = thread { calls.newCall(Request(server.url("/slow"))).execute().use { it.body.string() } }
        // Connected and asking, before the switch goes on.
        server.takeRequest(5, TimeUnit.SECONDS)!!
        required = true
        calls.renew()
        busy.join()
        // ...and, idle again, it doesn't carry the next request without the VPN.
        assertThrows(NoVpnException::class.java) { calls.newCall(Request(server.url("/next"))).execute().close() }
        assertEquals(1, server.requestCount)
    }

    @Test fun aVpnCountsOnlyForTheAddressesItCarries() {
        fun v4(vararg b: Int) = b.map { it.toByte() }.toByteArray()
        val everything = listOf(VpnRouteRule(v4(0, 0, 0, 0), 0, intoVpn = true))
        val github = v4(140, 82, 112, 6)
        assertTrue(carries(everything, github))
        assertTrue(carriesEverything(everything))
        // Two halves, as some VPNs route all of it.
        val halves = listOf(VpnRouteRule(v4(0, 0, 0, 0), 1, true), VpnRouteRule(v4(128, 0, 0, 0), 1, true))
        assertTrue(carriesEverything(halves) && carries(halves, github))
        // Only its own range (Tailscale without an exit node): GitHub would go around it.
        val ownRange = listOf(VpnRouteRule(v4(100, 64, 0, 0), 10, true))
        assertFalse(carries(ownRange, github))
        assertFalse(carriesEverything(ownRange))
        // An excluded range wins over the default route.
        val excluded = everything + VpnRouteRule(v4(140, 82, 0, 0), 16, intoVpn = false)
        assertFalse(carries(excluded, github))
        // IPv4 only: an IPv6 address isn't carried.
        assertFalse(carries(everything, ByteArray(16).also { it[0] = 0x20; it[1] = 0x01 }))
    }
}
