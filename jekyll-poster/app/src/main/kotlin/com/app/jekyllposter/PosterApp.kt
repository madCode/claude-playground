package com.app.jekyllposter

import android.app.Application
import com.app.jekyllposter.ui.editor.cameraDir
import kotlinx.coroutines.launch

open class PosterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        container.startVpnRetry()
        // Camera originals whose answer never came (the app died with the camera open and wasn't
        // restored): they carry the photo's location, so they don't stay. An hour spares one the
        // camera may still be writing.
        container.appScope.launch {
            val hourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
            cameraDir(this@PosterApp).listFiles()?.filter { it.lastModified() < hourAgo }?.forEach { it.delete() }
        }
    }

    protected open fun createContainer() = AppContainer(this)
}
