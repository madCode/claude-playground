package com.app.jekyllposter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.ui.connect.ConnectScreen
import com.app.jekyllposter.ui.connect.ConnectViewModel
import com.app.jekyllposter.ui.editor.EditorScreen
import com.app.jekyllposter.ui.editor.EditorViewModel
import com.app.jekyllposter.ui.home.HomeScreen
import com.app.jekyllposter.ui.home.HomeViewModel

private object Loading

@Composable
fun PosterNavHost(container: AppContainer, sharedText: String? = null) {
    // Wait for the stored account before choosing the first screen, so a signed-in writer never
    // sees the connect screen flash by.
    val signedIn by produceState<Any?>(Loading) { container.accounts.account.collect { value = it != null } }
    if (signedIn === Loading) return
    val nav = rememberNavController()
    NavHost(nav, startDestination = if (signedIn == true) "home" else "connect") {
        composable("connect") {
            ConnectScreen(viewModel { ConnectViewModel(container) }) {
                nav.navigate("home") { popUpTo("connect") { inclusive = true } }
            }
        }
        composable("home") {
            val vm = viewModel { HomeViewModel(container) }
            LaunchedEffect(sharedText) {
                if (sharedText != null) nav.navigate("editor/${container.drafts.insert(Draft(body = sharedText))}")
            }
            HomeScreen(
                vm,
                onOpenDraft = { nav.navigate("editor/$it") },
                onSignedOut = { nav.navigate("connect") { popUpTo("home") { inclusive = true } } },
            )
        }
        composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments!!.getLong("id")
            EditorScreen(viewModel(key = "editor-$id") { EditorViewModel(container, id) }) { nav.popBackStack() }
        }
    }
}
