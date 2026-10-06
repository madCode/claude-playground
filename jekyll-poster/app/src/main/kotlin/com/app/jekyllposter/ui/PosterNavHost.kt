package com.app.jekyllposter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.Shared
import com.app.jekyllposter.ui.connect.ConnectScreen
import com.app.jekyllposter.ui.connect.ConnectViewModel
import com.app.jekyllposter.ui.editor.EditorScreen
import com.app.jekyllposter.ui.editor.EditorViewModel
import com.app.jekyllposter.ui.home.HomeScreen
import com.app.jekyllposter.ui.home.HomeViewModel
import com.app.jekyllposter.ui.settings.BlogPrivacyScreen
import com.app.jekyllposter.ui.settings.BlogPrivacyViewModel
import com.app.jekyllposter.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private object Loading

@Composable
fun PosterNavHost(container: AppContainer, shared: Shared? = null) {
    // Wait for the stored account before choosing the first screen, so a signed-in writer never
    // sees the connect screen flash by.
    val signedIn by produceState<Any?>(Loading) { container.accounts.account.collect { value = it != null } }
    if (signedIn === Loading) return
    val nav = rememberNavController()
    // Home leaves the composition while the editor is open and its effects run again on the way
    // back, so a share is taken once, here, or every Back would start another post from it.
    var pending by rememberSaveable { mutableStateOf(shared != null) }
    NavHost(nav, startDestination = if (signedIn == true) "home" else "connect") {
        composable("connect") {
            ConnectScreen(viewModel { ConnectViewModel(container) }) {
                nav.navigate("home") { popUpTo(0) { inclusive = true } }
            }
        }
        composable("switch") {
            // Straight to the blog list, with the sign-in already in hand.
            ConnectScreen(viewModel { ConnectViewModel(container).also { it.switchBlog() } }, onCancel = { nav.popBackStack() }) {
                nav.navigate("home") { popUpTo(0) { inclusive = true } }
            }
        }
        composable("settings") {
            SettingsScreen(
                container,
                onBack = { nav.popBackStack() },
                onSwitchBlog = { nav.navigate("switch") },
                onBlogPrivacy = { nav.navigate("privacy") },
                onSignOut = {
                    // Navigation first, here on the main thread; the sign-out finishes behind it, in
                    // a scope that outlives this screen.
                    nav.navigate("connect") { popUpTo(0) { inclusive = true } }
                    container.appScope.launch { container.signOut() }
                },
            )
        }
        composable("privacy") {
            BlogPrivacyScreen(viewModel { BlogPrivacyViewModel(container) }) { nav.popBackStack() }
        }
        composable("home") {
            val vm = viewModel { HomeViewModel(container) }
            LaunchedEffect(Unit) {
                if (shared != null && pending) {
                    pending = false
                    vm.startShared(shared)
                }
            }
            val opened by vm.opened.collectAsState()
            LaunchedEffect(opened) {
                opened?.let { id ->
                    vm.opened.value = null
                    nav.navigate("editor/$id")
                }
            }
            HomeScreen(
                vm,
                onOpenDraft = { nav.navigate("editor/$it") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments!!.getLong("id")
            EditorScreen(viewModel(key = "editor-$id") { EditorViewModel(container, id) }) { nav.popBackStack() }
        }
    }
}
