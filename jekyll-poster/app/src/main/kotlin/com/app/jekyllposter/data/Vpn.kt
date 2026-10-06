package com.app.jekyllposter.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.os.Build
import com.app.jekyllposter.core.github.NoVpnException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import okhttp3.Call
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.Socket
import java.net.SocketAddress
import java.net.URI
import javax.net.SocketFactory

/** A network requests can go out on: sockets bound to it, and names looked up through it. */
interface Route {
    fun socket(): Socket

    /** [host]'s addresses that this network carries; it throws when it carries none of them. */
    fun lookup(host: String): List<InetAddress>
}

/** The phone's VPN, as this app sees it. */
interface Vpn {
    /** The VPN this app's traffic goes through now, or null: none, or one that leaves this app out. */
    fun current(): Route?

    /** Whether there is one, as it changes. */
    val up: Flow<Boolean>
}

/** One of a VPN's routes: [prefix] bits of [address], and whether it sends traffic into the VPN. */
data class VpnRouteRule(val address: ByteArray, val prefix: Int, val intoVpn: Boolean)

/**
 * Whether a VPN with [routes] carries traffic to [address]: its most specific route for it sends
 * it in. A VPN can route only some addresses (Tailscale without an exit node, a work VPN, one
 * without IPv6): a socket bound to it still reaches the rest, over the ordinary network.
 */
fun carries(routes: List<VpnRouteRule>, address: ByteArray): Boolean =
    routes.filter { it.address.size == address.size && matches(it, address) }.maxByOrNull { it.prefix }?.intoVpn == true

private fun matches(rule: VpnRouteRule, address: ByteArray): Boolean {
    var bits = rule.prefix
    for (i in address.indices) {
        if (bits <= 0) return true
        val mask = if (bits >= 8) 0xFF else (0xFF shl (8 - bits)) and 0xFF
        if ((address[i].toInt() and mask) != (rule.address[i].toInt() and mask)) return false
        bits -= 8
    }
    return true
}

/** Whether [routes] send all of IPv4 or all of IPv6 into the VPN: it's one meant for everything. */
fun carriesEverything(routes: List<VpnRouteRule>): Boolean =
    carries(routes, ByteArray(4)) && carries(routes, byteArrayOf(-128, 0, 0, 0)) ||
        carries(routes, ByteArray(16)) && carries(routes, ByteArray(16).also { it[0] = -128 })

/**
 * The VPN Android routes this app through: its default network, when that is a VPN meant for all
 * traffic. A VPN that leaves the app out (Mullvad's split tunneling, say) isn't the app's default
 * network, so it counts as none. Android doesn't say which VPN app it is.
 */
class AndroidVpn(context: Context) : Vpn {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override fun current(): Route? {
        val network = connectivity.activeNetwork ?: return null
        val routes = rules(network, connectivity.getNetworkCapabilities(network), connectivity.getLinkProperties(network)) ?: return null
        return object : Route {
            override fun socket(): Socket = network.socketFactory.createSocket()
            override fun lookup(host: String): List<InetAddress> =
                network.getAllByName(host).filter { carries(routes, it.address) }.ifEmpty { throw NoVpnException() }
        }
    }

    /** [network]'s routes, if it's a VPN meant for all traffic; null otherwise. */
    private fun rules(network: Network?, capabilities: NetworkCapabilities?, links: LinkProperties?): List<VpnRouteRule>? {
        if (network == null || capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) != true || links == null) return null
        val rules = links.routes.map { route ->
            // Routes that take traffic out of the VPN (excluded ranges) are told apart from Android 13.
            val intoVpn = Build.VERSION.SDK_INT < 33 || route.type == RouteInfo.RTN_UNICAST
            VpnRouteRule(route.destination.address.address, route.destination.prefixLength, intoVpn)
        }
        return rules.takeIf(::carriesEverything)
    }

    // Read from what the callback is told about the default network, not asked again: after a
    // loss, asking can still answer with the network just lost.
    override val up: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            var network: Network? = null
            var capabilities: NetworkCapabilities? = null
            var links: LinkProperties? = null

            fun send() = trySend(rules(network, capabilities, links) != null)

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (network != this.network) links = null
                this.network = network
                this.capabilities = capabilities
                send()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                if (network != this.network) capabilities = null
                this.network = network
                links = linkProperties
                send()
            }

            override fun onLost(network: Network) {
                if (network != this.network) return
                this.network = null
                send()
            }
        }
        trySend(current() != null)
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

/**
 * Makes connections through the VPN while [required] says so, and refuses them (with
 * [NoVpnException]) when there's none. Decided as each connection is made, not by checking
 * first: a socket bound to the VPN can't fall back to Wi-Fi if the VPN drops mid-request, and
 * names are looked up through it, so the lookups don't go around it either.
 *
 * [required] is read on OkHttp's threads, never the main one.
 */
class VpnGate(private val vpn: Vpn, private val required: () -> Boolean) {
    private fun route(): Route? = if (required()) vpn.current() ?: throw NoVpnException() else null

    val socketFactory: SocketFactory = object : SocketFactory() {
        override fun createSocket(): Socket = route()?.socket() ?: Socket()

        // OkHttp makes its sockets unconnected, through the call above; these would connect
        // before any check, so they aren't offered.
        override fun createSocket(host: String, port: Int): Socket = throw UnsupportedOperationException()
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket = throw UnsupportedOperationException()
        override fun createSocket(host: InetAddress, port: Int): Socket = throw UnsupportedOperationException()
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket = throw UnsupportedOperationException()
    }

    /**
     * A new lookup each time. OkHttp only reuses a connection for a client with the same one,
     * so connections made under one setting never serve requests made under another.
     */
    fun dns(): Dns = Dns { host -> route()?.lookup(host) ?: Dns.SYSTEM.lookup(host) }

    /**
     * The phone's proxies, but no SOCKS one while the VPN is required: OkHttp opens a SOCKS
     * socket itself, without the socket factory, so it would go around the VPN.
     */
    val proxySelector: ProxySelector = object : ProxySelector() {
        private val system get() = ProxySelector.getDefault()

        override fun select(uri: URI): List<Proxy> {
            val proxies = system?.select(uri).orEmpty()
            if (!required()) return proxies.ifEmpty { listOf(Proxy.NO_PROXY) }
            return proxies.filter { it.type() != Proxy.Type.SOCKS }.ifEmpty { listOf(Proxy.NO_PROXY) }
        }

        override fun connectFailed(uri: URI, address: SocketAddress, e: IOException) {
            system?.connectFailed(uri, address, e)
        }
    }
}

/**
 * Calls through [base] with its connections behind [gate], on a client made afresh by [renew]
 * whenever the switch changes. A connection belongs to the client it was made for, so one made
 * before the switch went on (perhaps not through the VPN, perhaps still busy) is never used again.
 */
class GatedCalls(private val base: OkHttpClient, private val gate: VpnGate) : Call.Factory {
    @Volatile private var client = make()

    private fun make() = base.newBuilder().socketFactory(gate.socketFactory).dns(gate.dns()).proxySelector(gate.proxySelector).build()

    override fun newCall(request: Request): Call = client.newCall(request)

    fun renew() {
        val old = client
        client = make()
        // The idle ones close now. Busy ones finish what they started, then idle out unused.
        old.connectionPool.evictAll()
    }
}
