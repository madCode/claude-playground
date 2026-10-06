package com.app.jekyllposter.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.app.jekyllposter.core.github.NoVpnException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/** A network requests can go out on: sockets bound to it, and names looked up through it. */
interface Route {
    fun socket(): Socket
    fun lookup(host: String): List<InetAddress>
}

/** The phone's VPN, as this app sees it. */
interface Vpn {
    /** The VPN this app's traffic goes through now, or null: none, or one that leaves this app out. */
    fun current(): Route?

    /** Whether there is one, as it changes. */
    val up: Flow<Boolean>
}

/**
 * The VPN Android routes this app through: its default network, when that is a VPN. A VPN that
 * leaves the app out (Mullvad's split tunneling, say) isn't the app's default network, so it
 * counts as none. Android doesn't say which VPN app it is.
 */
class AndroidVpn(context: Context) : Vpn {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override fun current(): Route? = connectivity.activeNetwork?.takeIf(::isVpn)?.let { network ->
        object : Route {
            override fun socket(): Socket = network.socketFactory.createSocket()
            override fun lookup(host: String): List<InetAddress> = network.getAllByName(host).toList()
        }
    }

    private fun isVpn(network: Network) = connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

    override val up: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
            }

            override fun onLost(network: Network) {
                trySend(current() != null)
            }
        }
        trySend(current() != null)
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

/**
 * Makes a client's connections through the VPN while [required] says so, and refuses them
 * (with [NoVpnException]) when there's none. Decided as each connection is made, not by checking
 * first: a socket bound to the VPN can't fall back to Wi-Fi if the VPN drops mid-request, and
 * names are looked up through it too, so the lookups don't leave around it either.
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

    val dns: Dns = Dns { host -> route()?.lookup(host) ?: Dns.SYSTEM.lookup(host) }

    /** [builder] with its connections made through this gate. */
    fun apply(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder.socketFactory(socketFactory).dns(dns)
}
