package com.app.jekyllposter.testutil

import com.app.jekyllposter.data.Route
import com.app.jekyllposter.data.Vpn
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Dns
import java.net.InetAddress
import java.net.Socket

/**
 * A VPN tests turn on and off. Its connections are plain ones, counted, so a test can tell a
 * request went through it.
 */
class FakeVpn : Vpn {
    override val up = MutableStateFlow(true)
    @Volatile var connections = 0

    override fun current(): Route? = if (!up.value) null else object : Route {
        override fun socket(): Socket = Socket().also { connections++ }
        override fun lookup(host: String): List<InetAddress> = Dns.SYSTEM.lookup(host)
    }
}
