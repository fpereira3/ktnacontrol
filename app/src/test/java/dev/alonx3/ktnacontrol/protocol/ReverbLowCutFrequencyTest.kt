package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reverb's Low Cut catalogue, from `reference/FxFloorboard/midi.xml:42892-42906` — the only
 * Mk2 source that documents it (CLAUDE.md §5.2).
 */
class ReverbLowCutFrequencyTest {

    @Test
    fun `has the 18 frequencies midi xml lists`() {
        assertEquals(18, ReverbLowCutFrequency.entries.size)
        assertEquals(18, ReverbLowCutFrequency.VALUES.size)
    }

    @Test
    fun `runs 0x00 to 0x11 with no gaps`() {
        (0x00..0x11).forEach { value ->
            assertEquals(
                "0x%02X debe existir, sin huecos en este catálogo".format(value),
                value,
                ReverbLowCutFrequency.fromValue(value)?.value,
            )
        }
        assertNull(ReverbLowCutFrequency.fromValue(0x12))
    }

    @Test
    fun `values are unique and fit in a 7-bit byte`() {
        assertEquals(ReverbLowCutFrequency.VALUES.size, ReverbLowCutFrequency.VALUES.toSet().size)
        assertTrue(ReverbLowCutFrequency.VALUES.all { it in 0..MidiBytes.MAX_BYTE_VALUE })
    }

    @Test
    fun `FLAT is the first entry, unlike the two High Cut catalogues`() {
        assertEquals(0x00, ReverbLowCutFrequency.FLAT.value)
    }
}
