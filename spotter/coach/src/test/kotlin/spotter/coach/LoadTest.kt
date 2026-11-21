package spotter.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadTest {
    // Trap 1: a goblet squat's one dumbbell is held in both hands, so two dumbbells from it
    // share its weight. 1×40 becomes 2×20 at most, never 2×35 or 2×40.
    @Test
    fun aSharedDumbbellSplitIntoTwoSharesItsWeight() {
        assertEquals(Load(20, 2), Load(40, 1).splitSharedDumbbell())
    }

    @Test
    fun splittingRoundsDownToTheRack() {
        // 35 / 2 = 17.5, and the rack has 15 and 20: never round up past what was lifted.
        assertEquals(Load(15, 2), Load(35, 1).splitSharedDumbbell())
    }

    @Test
    fun twoDumbbellsAreNeverMergedIntoOne() {
        // 2×25 rows say nothing about a 1×50 single-arm row, which is 50 in one hand.
        assertNull(Load(25, 2).splitSharedDumbbell())
    }

    @Test
    fun anUnknownCountNeitherStepsNorConverts() {
        // Imported "40" could have been 2×20 or 2×40: stepping up from it could double the weight.
        val imported = Load(40, null)
        assertNull(imported.splitSharedDumbbell())
        assertNull(imported.perHand)
        assertNull(imported.total)
    }

    @Test
    fun nothingOnTheRackFits() {
        assertNull(Load(5, 1).splitSharedDumbbell())
    }

    @Test
    fun labelsSayCountAndWeight() {
        assertEquals("2×25", Load(25, 2).label)
        assertEquals("1×50", Load(50, 1).label)
        assertEquals("40 lb (count?)", Load(40, null).label)
    }

    @Test(expected = IllegalArgumentException::class)
    fun threeDumbbellsIsNotALoad() {
        Load(20, 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aWeightMustBePositive() {
        Load(0, 1)
    }
}
