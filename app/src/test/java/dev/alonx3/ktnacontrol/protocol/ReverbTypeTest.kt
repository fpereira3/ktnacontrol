package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Reverb catalogue as CLAUDE.md §5.2 documents it, from the two Mk2 sources that agree:
 * `reverb.yaml:24-31` and `midi.xml:43879-43886`.
 */
class ReverbTypeTest {

    @Test
    fun `has the 7 types both Mk2 sources list`() {
        assertEquals(7, ReverbType.entries.size)
        assertEquals(7, ReverbType.VALUES.size)
    }

    @Test
    fun `runs 0x00 to 0x06 with no gaps`() {
        (0x00..0x06).forEach { value ->
            assertEquals(
                "0x%02X debe existir, sin huecos en este catálogo".format(value),
                value,
                ReverbType.fromValue(value)?.value,
            )
        }
        assertNull(ReverbType.fromValue(0x07))
    }

    @Test
    fun `values are unique and fit in a 7-bit byte`() {
        assertEquals(ReverbType.VALUES.size, ReverbType.VALUES.toSet().size)
        assertTrue(ReverbType.VALUES.all { it in 0..MidiBytes.MAX_BYTE_VALUE })
    }

    @Test
    fun `display names are unique and non-blank`() {
        val names = ReverbType.entries.map { it.displayName }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.none { it.isBlank() })
    }

    @Test
    fun `spot-checks the values documented in CLAUDE md`() {
        assertEquals(0x00, ReverbType.AMBIENCE.value)
        assertEquals(0x04, ReverbType.PLATE.value)
        assertEquals(0x06, ReverbType.MODULATE.value)
    }
}
