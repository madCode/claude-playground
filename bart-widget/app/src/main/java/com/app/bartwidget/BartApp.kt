package com.app.bartwidget

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.updateAll

private val Context.dataStore by preferencesDataStore("bart")

/** Everything the app talks to, swapped out in tests (see TestApp). */
class AppContainer(
    val context: Context,
    dataStore: DataStore<Preferences>,
    bartBase: String = "https://api.bart.gov",
    val clock: () -> Long = System::currentTimeMillis,
) {
    val api = BartApi(bartBase)
    val store = Store(dataStore)
    val updateWidgets: suspend () -> Unit = { BartWidget().updateAll(context) }
    val refresher = Refresher(store, api, clock, updateWidgets)
}

open class BartApp : Application() {
    val container: AppContainer by lazy { createContainer() }

    protected open fun createContainer() = AppContainer(this, dataStore)
}

val Context.container: AppContainer get() = (applicationContext as BartApp).container
