package com.app.carry.device

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import androidx.test.uiautomator.UiDevice
import java.io.File
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * What was on screen when a device test failed, as a picture and the window's hierarchy, in the
 * run's report: the notification shade or another app's screen leaves no other trace.
 */
class ScreenOnFailure : TestWatcher() {
    override fun failed(e: Throwable, description: Description) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val name = description.methodName
        runCatching { TestStorage().openOutputFile("$name-window.xml").use { device.dumpWindowHierarchy(it) } }
        runCatching {
            val shot = File(instrumentation.targetContext.cacheDir, "$name.png")
            device.takeScreenshot(shot)
            TestStorage().openOutputFile("$name.png").use { out -> shot.inputStream().use { it.copyTo(out) } }
        }
    }
}
