package com.app.jekyllposter.device

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Runs the device tests against [DeviceTestApp] in place of the app's own Application. */
class DeviceTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, DeviceTestApp::class.java.name, context)
}
