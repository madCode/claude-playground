package spotter.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RackTest {
    @Test
    fun stepsFollowTheRackNotAFixedAmount() {
        assertEquals(10, Rack.up(8))
        assertEquals(15, Rack.up(12))
        assertEquals(20, Rack.up(15))
        assertEquals(12, Rack.down(15))
    }

    @Test
    fun theEndsOfTheRack() {
        assertNull(Rack.up(50))
        assertNull(Rack.down(3))
        assertNull(Rack.atMost(2.5))
        assertEquals(50, Rack.atMost(80.0))
    }
}
