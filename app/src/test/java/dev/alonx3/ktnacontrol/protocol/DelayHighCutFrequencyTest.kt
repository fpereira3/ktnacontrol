package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Delay 1's High Cut catalogue, from `reference/FxFloorboard/midi.xml:42468-42482` — the only
 * Mk2 source that documents it (CLAUDE.md §5.2).
 */
class DelayHighCutFrequencyTest {

    @Test
    fun `has the 15 frequencies midi xml lists`() {
        assertEquals(15, DelayHighCutFrequency.entries.size)
        assertEquals(15, DelayHighCutFrequency.VALUES.size)
    }

    @Test
    fun `runs 0x00 to 0x0E with no gaps`() {
        (0x00..0x0E).forEach { value ->
            assertEquals(
                "0x%02X debe existir, sin huecos en este catálogo".format(value),
                value,
                DelayHighCutFrequency.fromValue(value)?.value,
            )
        }
        assertNull(DelayHighCutFrequency.fromValue(0x0F))
    }

    @Test
    fun `values are unique and fit in a 7-bit byte`() {
        assertEquals(DelayHighCutFrequency.VALUES.size, DelayHighCutFrequency.VALUES.toSet().size)
        assertTrue(DelayHighCutFrequency.VALUES.all { it in 0..MidiBytes.MAX_BYTE_VALUE })
    }

    @Test
    fun `FLAT is the last entry, no cut at all`() {
        assertEquals(0x0E, DelayHighCutFrequency.FLAT.value)
    }

    @Test
    fun `0x0A is 6point30K, unlike Reverb's own High Cut`() {
        // La discrepancia entre las dos fuentes del mismo midi.xml: ver ReverbHighCutFrequency.
        assertEquals("6.30K", DelayHighCutFrequency.KHZ_6_30.displayName)
    }
}
