package com.app.jekyllposter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.Shared
import com.app.jekyllposter.ui.blogs.BlogsScreen
import com.app.jekyllposter.ui.blogs.BlogsViewModel
import com.app.jekyllposter.ui.connect.ConnectScreen
import com.app.jekyllposter.ui.connect.ConnectViewModel
import com.app.jekyllposter.ui.editor.EditorScreen
import com.app.jekyllposter.ui.editor.EditorViewModel
import com.app.jekyllposter.ui.home.HomeScreen
import com.app.jekyllposter.ui.home.HomeViewModel
import com.app.jekyllposter.ui.settings.BlogPrivacyScreen
import com.app.jekyllposter.ui.settings.BlogPrivacyViewModel
import com.app.jekyllposter.ui.settings.SettingsScreen

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
        composable("add-sign-in") {
            ConnectScreen(viewModel { ConnectViewModel(container) }, onCancel = { nav.popBackStack() }) {
                nav.navigate("home") { popUpTo(0) { inclusive = true } }
            }
        }
        composable("add-blog/{signIn}", arguments = listOf(navArgument("signIn") { type = NavType.LongType })) { entry ->
            val signIn = entry.arguments!!.getLong("signIn")
            // Straight to the sign-in's repositories, with its token already in hand.
            ConnectScreen(viewModel { ConnectViewModel(container).also { it.addBlogTo(signIn) } }, onCancel = { nav.popBackStack() }) {
                nav.navigate("home") { popUpTo(0) { inclusive = true } }
            }
        }
        composable("blogs") {
            BlogsScreen(
                viewModel { BlogsViewModel(container) },
                onBack = { nav.popBackStack() },
                onOpened = { nav.navigate("home") { popUpTo(0) { inclusive = true } } },
                onAddBlog = { nav.navigate("add-blog/$it") },
                onAddSignIn = { nav.navigate("add-sign-in") },
                onSignedOut = { nav.navigate("connect") { popUpTo(0) { inclusive = true } } },
            )
        }
        composable("settings") {
            SettingsScreen(
                container,
                onBack = { nav.popBackStack() },
                onBlogs = { nav.navigate("blogs") },
                onAddSignIn = { nav.navigate("add-sign-in") },
                onBlogPrivacy = { nav.navigate("privacy") },
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
            val opened by vm.opened.collectAsStateWithLifecycle()
            LaunchedEffect(opened) {
                opened?.let { id ->
                    vm.openedHandled()
                    nav.navigate("editor/$id")
                }
            }
            HomeScreen(
                vm,
                onOpenDraft = { nav.navigate("editor/$it") },
                onSettings = { nav.navigate("settings") },
                blogs = viewModel { BlogsViewModel(container) },
                onAddBlog = { nav.navigate("add-blog/$it") },
                onAddSignIn = { nav.navigate("add-sign-in") },
            )
        }
        composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments!!.getLong("id")
            EditorScreen(viewModel(key = "editor-$id") { EditorViewModel(container, id) }) { nav.popBackStack() }
        }
    }
}
