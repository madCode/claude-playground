package com.app.carry.device

import android.Manifest
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.app.carry.SessionFolder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The After tab's Add on a borrowed day, typed through the real keyboard (#210). Robolectric runs
 * effects and layout in an order of its own and has no keyboard to shrink the screen, so where the
 * new row lands and whether its box takes the cursor is checked here, on an emulator.
 */
@RunWith(AndroidJUnit4::class)
class AddMoveTest {
    @get:Rule val onFailure = ScreenOnFailure()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val carry = instrumentation.targetContext
    private val pkg = carry.packageName

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().decodeToString() }

    /** A made-up borrowed sheet: the rows are another class's, so the session is what gets added. */
    @Before fun aBorrowedSheetInTheFolder() {
        // Granted, so the one question Carry asks doesn't cover the screen.
        shell("pm grant $pkg ${Manifest.permission.POST_NOTIFICATIONS}")
        TestFolder.grantTo(carry, pkg)
        assertTrue("Carry couldn't take the folder's grant", SessionFolder(carry).remember(TestFolder.tree()))
        val today = LocalDate.now()
        val sets = (0 until 6).joinToString(",") {
            """{"ref":$it,"line":"10 x move $it","canonical":"move $it","weight":null,"reps":null}"""
        }
        TestFolder.put(
            carry, "session-$today-2G.json",
            """
            {"schema":"gym-session/1","sheet_id":"$today:2G","date":"$today","class":"2G",
             "borrowed_from":"3G","sets":[$sets],"added":[]}
            """.trimIndent(),
        )
        val launch = carry.packageManager.getLaunchIntentForPackage(pkg)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        carry.startActivity(launch)
        assertTrue("Carry didn't open", device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 20_000))
        device.wait(Until.findObject(By.text("After")), 20_000)!!.click()
    }

    // The text is on the field; its label is on a view inside it.
    private fun box(label: String) = By.clazz("android.widget.EditText").hasChild(By.desc(label))

    private fun boxes(label: String) = device.findObjects(box(label))

    private fun focused(label: String): BySelector = box(label).focused(true)

    private fun add() = device.wait(Until.findObject(By.text(ADD)), 10_000)!!.click()

    private fun alive() = assertTrue(
        "Carry is gone: ${shell("logcat -d -b crash").takeLast(4_000)}",
        device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 5_000),
    )

    /**
     * Types [text] into the box [label] that has the cursor, through the keyboard. A keystroke sent
     * while the keyboard is still changing over (letters to digits) can be dropped, so it waits for
     * the box to have the cursor and tries again, cleared, if the text didn't land.
     */
    private fun typeInto(label: String, text: String) {
        assertNotNull("$label never took the cursor", device.wait(Until.findObject(focused(label)), 10_000))
        repeat(3) {
            device.waitForIdle()
            shell("input text $text")
            if (device.wait(Until.hasObject(focused(label).text(text)), 5_000)) return
            repeat(12) { shell("input keyevent KEYCODE_DEL") }
        }
        error("couldn't type $text into $label")
    }

    @Test fun addMovesTheCursorToTheNewNameAndKeepsWhatWasTyped() {
        add()
        assertNotNull("the first Add didn't put the cursor in the new name", device.wait(Until.findObject(focused(NAME)), 10_000))
        typeInto(NAME, "popsquat")
        tapInto(LB)
        typeInto(LB, "12")

        // madCode's report: the cursor stayed in the weight box, and the form emptied.
        add()
        alive()
        assertTrue("no second row", device.wait(Until.hasObject(box(NAME).text("")), 10_000) || boxes(NAME).size == 2)
        assertNotNull("the second Add didn't move the cursor", device.wait(Until.findObject(focused(NAME)), 10_000))
        assertEquals("the second row's name has it", "", device.findObject(focused(NAME)).text ?: "")
        assertTrue("the first row lost its name", device.hasObject(box(NAME).text("popsquat")))
        assertTrue("the first row lost its weight", device.hasObject(box(LB).text("12")))

        typeInto(NAME, "plankjack")
        alive()

        device.wait(Until.findObject(By.text("Save")), 10_000)?.click()
            ?: run { device.findObject(By.scrollable(true)).scrollUntil(androidx.test.uiautomator.Direction.DOWN, Until.findObject(By.text("Save")))!!.click() }
        alive()
        assertTrue("the save didn't land", device.wait(Until.hasObject(By.text("Saved")), 20_000))
        alive()
    }

    /** Scrolls the After tab until the box [label] is on screen, either way. */
    private fun reach(label: String): androidx.test.uiautomator.UiObject2 {
        device.findObject(box(label))?.let { return it }
        val list = device.findObject(By.scrollable(true))
        return list.scrollUntil(androidx.test.uiautomator.Direction.DOWN, Until.findObject(box(label)))
            ?: list.scrollUntil(androidx.test.uiautomator.Direction.UP, Until.findObject(box(label)))
            ?: error("no $label")
    }

    /** Puts the cursor in [label]. A slow emulator can take a tap for a long press, which opens
     *  the Autofill menu instead, so the tap is tried again. */
    private fun tapInto(label: String) {
        repeat(3) {
            reach(label).click()
            if (device.wait(Until.hasObject(focused(label)), 3_000)) return
            device.findObject(By.text("Autofill"))?.let { device.pressBack() }
        }
    }

    /** The sheet's rows filled first, as on a day the borrowed block was partly what the class did. */
    @Test fun addKeepsTheSheetRowsTypedAbove() {
        for (k in 0 until 6) {
            tapInto("move $k, lb")
            typeInto("move $k, lb", "${k + 21}")
        }
        val list = device.findObject(By.scrollable(true))
        list.scrollUntil(androidx.test.uiautomator.Direction.UP, Until.findObject(By.text(ADD)))
        add()
        alive()
        assertNotNull("Add didn't move the cursor", device.wait(Until.findObject(focused(NAME)), 10_000))
        typeInto(NAME, "popsquat")
        for (k in 0 until 6) assertEquals("move $k", "${k + 21}", reach("move $k, lb").text)
        list.scrollUntil(androidx.test.uiautomator.Direction.UP, Until.findObject(By.text(ADD)))
        add()
        alive()
        for (k in 0 until 6) assertEquals("move $k after a second Add", "${k + 21}", reach("move $k, lb").text)
        list.scrollUntil(androidx.test.uiautomator.Direction.DOWN, Until.findObject(By.text("Save")))!!.click()
        alive()
        assertTrue("the save didn't land", device.wait(Until.hasObject(By.text("Saved")), 20_000))
        alive()
        for (k in 0 until 6) assertEquals("move $k after the save", "${k + 21}", reach("move $k, lb").text)
    }

    /** madCode's steps 5 and 6: Android ended Carry with typing on screen, and the save after. */
    @Test fun aRestoredFormSaves() {
        add()
        device.wait(Until.findObject(box(NAME)), 10_000); tapInto(NAME)
        typeInto(NAME, "popsquat")
        tapInto(LB)
        typeInto(LB, "12")
        add()
        assertTrue(device.wait(Until.hasObject(box(NAME).text("")), 10_000))

        // Stopped (so the draft is in saved state), then the process ended the way Android ends a
        // backgrounded app; the launcher brings the task back, restored.
        device.pressHome()
        device.waitForIdle()
        shell("am kill $pkg")
        carry.startActivity(carry.packageManager.getLaunchIntentForPackage(pkg)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue("Carry didn't come back", device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 20_000))
        device.wait(Until.findObject(By.text("After")), 20_000)?.click()
        assertTrue("the typing didn't come back", device.wait(Until.hasObject(box(NAME).text("popsquat")), 20_000))
        assertTrue(device.hasObject(box(LB).text("12")))

        device.wait(Until.findObject(By.text("Save")), 5_000)?.click()
            ?: device.findObject(By.scrollable(true)).scrollUntil(androidx.test.uiautomator.Direction.DOWN, Until.findObject(By.text("Save")))!!.click()
        alive()
        assertTrue("the save didn't land", device.wait(Until.hasObject(By.text("Saved")), 20_000))
        alive()
    }

    private companion object {
        const val ADD = "+ Add what you did"
        const val NAME = "added exercise name"
        const val LB = "added exercise, lb"
    }
}
