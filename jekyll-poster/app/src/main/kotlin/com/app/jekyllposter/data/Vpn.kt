package com.app.jekyllposter.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.os.Build
import com.app.jekyllposter.core.github.NoVpnException
import com.app.jekyllposter.core.net.Route
import com.app.jekyllposter.core.net.Vpn
import com.app.jekyllposter.core.net.VpnRouteRule
import com.app.jekyllposter.core.net.carries
import com.app.jekyllposter.core.net.carriesEverything
import java.net.InetAddress
import java.net.Socket
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

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
            // Into the VPN: through its interface. Android blocks a family the VPN doesn't carry
            // with an unreachable route that has none; from Android 13 the route also says so,
            // as it does for excluded ranges.
            val intoVpn = route.`interface` != null && (Build.VERSION.SDK_INT < 33 || route.type == RouteInfo.RTN_UNICAST)
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

            // A new default network's capabilities come before its link properties: the missing
            // one is asked for, or a VPN coming up would read as none for a moment.
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (network != this.network) links = connectivity.getLinkProperties(network)
                this.network = network
                this.capabilities = capabilities
                send()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                if (network != this.network) capabilities = connectivity.getNetworkCapabilities(network)
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
