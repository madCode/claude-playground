package com.app.jekyllposter

import android.app.Application
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

open class PosterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        addNewPostShortcut()
    }

    protected open fun createContainer() = AppContainer(this)

    /**
     * Long-pressing the app's icon offers New post, straight into the editor. Added here rather
     * than in a shortcuts.xml, whose intent has to name the package literally, and the debug
     * build's differs.
     */
    private fun addNewPostShortcut() {
        val shortcut = ShortcutInfoCompat.Builder(this, NEW_POST_SHORTCUT)
            .setShortLabel(getString(R.string.shortcut_new_post))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_new_post))
            .setIntent(Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_POST))
            .build()
        // Never worth failing to start over: a launcher without shortcuts just doesn't show it.
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(this, shortcut) }
    }

    companion object {
        const val NEW_POST_SHORTCUT = "new-post"
    }
}
