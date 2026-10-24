package com.app.carry.device

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
 * effects and layout in an order of its own, so a request for focus made before the new row was
 * laid out passed there and did nothing on a phone.
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

    @Test fun addMovesTheCursorToTheNewNameAndKeepsWhatWasTyped() {
        add()
        assertNotNull("the first Add didn't put the cursor in the new name", device.wait(Until.findObject(focused(NAME)), 10_000))
        shell("input text popsquat")
        assertTrue(device.wait(Until.hasObject(box(NAME).text("popsquat")), 10_000))
        device.findObject(box(LB)).click()
        shell("input text 12")
        assertTrue(device.wait(Until.hasObject(box(LB).text("12")), 10_000))

        // madCode's report: the cursor stayed in the weight box, and the form emptied.
        add()
        alive()
        assertTrue("no second row", device.wait(Until.hasObject(box(NAME).text("")), 10_000) || boxes(NAME).size == 2)
        assertNotNull("the second Add didn't move the cursor", device.wait(Until.findObject(focused(NAME)), 10_000))
        assertEquals("the second row's name has it", "", device.findObject(focused(NAME)).text ?: "")
        assertTrue("the first row lost its name", device.hasObject(box(NAME).text("popsquat")))
        assertTrue("the first row lost its weight", device.hasObject(box(LB).text("12")))

        shell("input text plankjack")
        assertTrue(device.wait(Until.hasObject(box(NAME).text("plankjack")), 10_000))
        alive()

        device.wait(Until.findObject(By.text("Save")), 10_000)?.click()
            ?: run { device.findObject(By.scrollable(true)).scrollUntil(androidx.test.uiautomator.Direction.DOWN, Until.findObject(By.text("Save")))!!.click() }
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
