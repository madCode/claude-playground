package com.app.jekyllposter.data

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.publish.Publisher
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.ui.forWriter
import com.app.jekyllposter.ui.home.status
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** "Only connect through a VPN": every request goes through the VPN, or isn't sent at all. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class VpnTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container
    private val vpn = app.vpn

    @Before fun signIn() = runBlocking {
        c.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main"))
    }

    @After fun close() = app.github.close()

    private fun refresh() = runBlocking { runCatching { c.blogs.refresh() }.exceptionOrNull() }

    @Test fun offTheAppConnectsAsUsual() {
        assertNull(refresh())
        assertEquals(0, vpn.connections)
    }

    @Test fun onRequestsGoThroughTheVpn() {
        runBlocking { c.setOnlyThroughVpn(true) }
        assertNull(refresh())
        assertTrue(vpn.connections > 0)
    }

    @Test fun onWithoutAVpnNothingIsSent() {
        runBlocking { c.setOnlyThroughVpn(true) }
        vpn.up.value = false
        app.github.log.clear()
        val error = refresh()
        assertEquals("No VPN connection, so nothing was sent to GitHub. Turn your VPN on, then try again.", error!!.forWriter())
        assertEquals(emptyList<String>(), app.github.log)
    }

    @Test fun turningItOnDropsConnectionsMadeWithoutIt() {
        // A connection made with the switch off, kept open for the next request...
        assertNull(refresh())
        vpn.up.value = false
        runBlocking { c.setOnlyThroughVpn(true) }
        app.github.log.clear()
        // ...isn't used once it's on.
        assertNotNull(refresh())
        assertEquals(emptyList<String>(), app.github.log)
    }

    @Test fun aQueuedPostWaitsForTheVpnAndSaysSo() = runBlocking {
        c.setOnlyThroughVpn(true)
        vpn.up.value = false
        val id = c.drafts.insert(Draft(title = "Quiet", body = "Hello.", state = PostState.Queued))
        assertEquals(Publisher.Outcome.Retry, c.publisher.publish(id))
        assertTrue(app.github.commits.values.none { it.message.contains("Quiet") })
        assertFalse(c.awaitVpn(50.milliseconds))
        assertEquals("Waiting for your VPN…", c.drafts.get(id)!!.status(waitingForVpn = true).first)

        vpn.up.value = true
        assertTrue(c.awaitVpn(5.seconds))
        assertEquals(Publisher.Outcome.Done, c.publisher.publish(id))
    }

    @Test fun thePreviewsPhotosGoThroughTheVpnToo() {
        runBlocking { c.setOnlyThroughVpn(true) }
        vpn.up.value = false
        app.github.log.clear()
        c.previewFetcher.fetch(app.github.apiBase.resolve("assets/images/2025/lighthouse.jpg").toString())
        assertEquals(emptyList<String>(), app.github.log)
    }

    @Test fun queuedPostsStartAgainWhenTheVpnIsBack() {
        val id = runBlocking {
            c.setOnlyThroughVpn(true)
            c.drafts.insert(Draft(title = "Quiet", body = "Hello.", state = PostState.Queued))
        }
        vpn.up.value = false
        // Long enough for the app to see it's waiting.
        Thread.sleep(500)
        assertTrue(app.retried.isEmpty())
        vpn.up.value = true
        val deadline = System.currentTimeMillis() + 5_000
        while (id !in app.retried && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(listOf(id), app.retried.toList())
    }

    @Test fun androidCountsOnlyAVpnTheAppIsRoutedThrough() {
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        val android = AndroidVpn(app)
        // Robolectric's phone is on an ordinary network.
        assertNull(android.current())
        val vpnCapabilities = ShadowNetworkCapabilities.newInstance().also { shadowOf(it).addTransportType(NetworkCapabilities.TRANSPORT_VPN) }
        val network = connectivity.activeNetwork
        shadowOf(connectivity).setNetworkCapabilities(network, vpnCapabilities)
        // A VPN for its own range only, as Tailscale without an exit node: GitHub goes around it.
        shadowOf(connectivity).setLinkProperties(network, links("100.64.0.0/10" to "tun0"))
        assertNull(android.current())
        // Android's unreachable route, blocking the IPv6 the VPN doesn't carry, isn't a way in.
        shadowOf(connectivity).setLinkProperties(network, links("100.64.0.0/10" to "tun0", "::/0" to null))
        assertNull(android.current())
        shadowOf(connectivity).setLinkProperties(network, links("0.0.0.0/0" to "tun0"))
        assertNotNull(android.current())
    }

    /**
     * Link properties with a route for each prefix, through the interface named, or unreachable
     * without one, as Android adds them. Their constructors are hidden from apps.
     */
    private fun links(vararg routes: Pair<String, String?>) = android.net.LinkProperties().also { links ->
        ReflectionHelpers.callInstanceMethod<Any>(links, "setInterfaceName", ClassParameter.from(String::class.java, "tun0"))
        for ((p, iface) in routes) {
            val prefix = ReflectionHelpers.callConstructor(android.net.IpPrefix::class.java, ClassParameter.from(String::class.java, p))
            val route = ReflectionHelpers.callConstructor(
                android.net.RouteInfo::class.java,
                ClassParameter.from(android.net.IpPrefix::class.java, prefix),
                ClassParameter.from(java.net.InetAddress::class.java, null),
                ClassParameter.from(String::class.java, iface),
                ClassParameter.from(Int::class.javaPrimitiveType, if (iface != null) android.net.RouteInfo.RTN_UNICAST else android.net.RouteInfo.RTN_UNREACHABLE),
            )
            ReflectionHelpers.callInstanceMethod<Any>(links, "addRoute", ClassParameter.from(android.net.RouteInfo::class.java, route))
        }
    }
}
