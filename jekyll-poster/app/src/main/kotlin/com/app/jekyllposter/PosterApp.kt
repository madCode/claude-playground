package com.app.jekyllposter

import android.app.Application

open class PosterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }

    protected open fun createContainer() = AppContainer(this)
}
