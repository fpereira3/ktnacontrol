package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two real parameters this scale unblocked (CLAUDE.md §5.2): Reverb Time (`range
 * 00/63/0.1/10.0 sec`) and Mod's 2x2 Chorus Pre Delay (`range 00/50/0.0/40.0`, 0.5 ms/step).
 */
class FractionalLevelScaleTest {

    private val reverbTime = FractionalLevelScale(rawRange = 0..0x63, displayRange = 0.1..10.0, step = 0.1)
    private val chorusPreDelay = FractionalLevelScale(rawRange = 0..0x50, displayRange = 0.0..40.0, step = 0.5)

    @Test
    fun `reverb time - raw 0 is the bottom of the range, 0point1 s`() {
        assertEquals(0.1, reverbTime.toDisplay(0), 0.0)
    }

    @Test
    fun `reverb time - raw 99 (0x63) is the documented maximum, 10point0 s`() {
        assertEquals(10.0, reverbTime.toDisplay(0x63), 0.0)
    }

    @Test
    fun `reverb time - a value in the middle round-trips exactly`() {
        val raw = 50 // display = 0.1 + 50*0.1 = 5.1
        assertEquals(5.1, reverbTime.toDisplay(raw), 1e-9)
        assertEquals(raw, reverbTime.toRaw(5.1))
    }

    @Test
    fun `mod chorus pre delay - raw 0 is 0point0 ms`() {
        assertEquals(0.0, chorusPreDelay.toDisplay(0), 0.0)
    }

    @Test
    fun `mod chorus pre delay - raw 0x50 (80) is the documented maximum, 40point0 ms`() {
        assertEquals(40.0, chorusPreDelay.toDisplay(0x50), 0.0)
    }

    @Test
    fun `mod chorus pre delay - a value in the middle round-trips exactly`() {
        val raw = 40 // display = 0.0 + 40*0.5 = 20.0
        assertEquals(20.0, chorusPreDelay.toDisplay(raw), 1e-9)
        assertEquals(raw, chorusPreDelay.toRaw(20.0))
    }

    @Test
    fun `toRaw clamps a display value outside the range instead of over- or under-shooting`() {
        assertEquals(0, reverbTime.toRaw(-5.0))
        assertEquals(0x63, reverbTime.toRaw(50.0))
        assertEquals(0, chorusPreDelay.toRaw(-1.0))
        assertEquals(0x50, chorusPreDelay.toRaw(100.0))
    }

    @Test
    fun `toDisplay clamps a raw value outside the range`() {
        assertEquals(0.1, reverbTime.toDisplay(-10), 0.0)
        assertEquals(10.0, reverbTime.toDisplay(200), 0.0)
    }

    @Test
    fun `accepts only bytes inside the raw range`() {
        assertTrue(reverbTime.accepts(0))
        assertTrue(reverbTime.accepts(0x63))
        assertTrue(!reverbTime.accepts(0x64))
        assertTrue(!reverbTime.accepts(-1))
    }

    @Test
    fun `every raw value round-trips through display and back without drifting`() {
        // Cada conversión repite desde el entero crudo, así que no hay margen para que el
        // error de punto flotante de multiplicar por step se acumule entre pasadas.
        for (raw in reverbTime.rawRange) {
            val display = reverbTime.toDisplay(raw)
            assertEquals("raw=$raw", raw, reverbTime.toRaw(display))
        }
        for (raw in chorusPreDelay.rawRange) {
            val display = chorusPreDelay.toDisplay(raw)
            assertEquals("raw=$raw", raw, chorusPreDelay.toRaw(display))
        }
    }

    @Test
    fun `repeating the round trip many times never drifts off the original raw value`() {
        val raw = 37
        var display = reverbTime.toDisplay(raw)
        repeat(50) {
            val recoveredRaw = reverbTime.toRaw(display)
            assertEquals(raw, recoveredRaw)
            display = reverbTime.toDisplay(recoveredRaw)
        }
    }

    @Test
    fun `step must be positive`() {
        try {
            FractionalLevelScale(rawRange = 0..10, displayRange = 0.0..1.0, step = 0.0)
            org.junit.Assert.fail("step de 0 debería rechazarse")
        } catch (expected: IllegalArgumentException) {
            // esperado
        }
    }
}
