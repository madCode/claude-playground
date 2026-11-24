package spotter.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every value here is invented: the owner's real rulings are private. */
class DumbbellCountsTest {
    private val file = """
        schema: dumbbell-counts/1
        movements:
          shoulder press: 2                                     # a plain count
          halo: 1
          tricep extension: {count: 1, when_line_has: overhead} # only when the line has the word
          squat: {count: 2, unless_line_has: {goblet: 1, sumo: 1}}
          "farmer's carry": 2
    """.trimIndent()

    // The three forms a ruling takes, against the board line where a form looks at it.
    @Test
    fun aRulingIsACountOrOneThatLooksAtTheLine() {
        val counts = DumbbellCounts.parse(file)
        assertEquals(5, counts.size)
        assertEquals(2, counts.count(listOf("shoulder press"), "8 x shoulder press"))
        assertEquals(1, counts.count(listOf("Halo"), null))
        assertEquals(1, counts.count(listOf("tricep extension"), "8 x Overhead tricep extension"))
        assertNull(counts.count(listOf("tricep extension"), "8 x tricep extension (lying on bench)"))
        assertNull(counts.count(listOf("tricep extension"), null))
        assertEquals(listOf(2, 1, 1), listOf("8 x squat", "8 x sumo squat", "8 x goblet squat").map { counts.count(listOf("squat"), it) })
        assertEquals(2, counts.count(listOf("farmer's carry"), null))
        // A movement the file doesn't rule on stays unknown, and so does a combo unless its parts agree.
        assertNull(counts.count(listOf("deadlift"), "8 x deadlift"))
        assertEquals(2, counts.count(listOf("squat", "shoulder press"), "8 x squat to press"))
        assertNull(counts.count(listOf("squat", "halo"), "8 x squat to halo"))
        assertNull(counts.count(listOf("squat", "deadlift"), "8 x squat to deadlift"))
        assertNull(DumbbellCounts.NONE.count(listOf("squat"), "8 x squat"))
    }

    // A file that isn't one is refused whole, with a sentence that quotes no more than a line's start.
    @Test
    fun aFileThatIsNotOneIsRefused() {
        fun refused(text: String) = assertThrows(IllegalArgumentException::class.java) { DumbbellCounts.parse(text) }.message!!
        assertTrue("first line" in refused("movements:\n  squat: 2"))
        assertTrue("movements" in refused("schema: dumbbell-counts/1\nsquat: 2"))
        val head = "schema: dumbbell-counts/1\nmovements:\n"
        assertTrue("1 or 2" in refused(head + "  squat: 3"))
        assertTrue("1 or 2" in refused(head + "  squat: {when_line_has: goblet}"))
        assertTrue("format doesn't have" in refused(head + "  squat: {count: 2, sometimes: 1}"))
        assertTrue("doesn't close" in refused(head + "  squat: {count: 2"))
        assertTrue("indented" in refused(head + "squat: 2"))
        assertTrue("too big" in refused(head + "  squat: 2\n".repeat(20_000)))
        assertTrue("nested" in refused(head + "  squat: " + "{a: ".repeat(40)))
        // A history file picked by mistake.
        assertTrue("first line" in refused("""{"date": "2026-03-03", "exercise": "squat"}"""))
        assertEquals(0, DumbbellCounts.parse(head).size)
    }
}
