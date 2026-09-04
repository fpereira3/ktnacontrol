package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Delay 1 catalogue as CLAUDE.md §5.2 documents it, from the two Mk2 sources that agree:
 * `delay.yaml:36-47` and `midi.xml:43840-43851` (the only difference is cosmetic: `0x08` is
 * "Tap Echo" in one and "Tape" in the other).
 */
class DelayTypeTest {

    @Test
    fun `has the 11 types both Mk2 sources list`() {
        assertEquals(11, DelayType.entries.size)
        assertEquals(11, DelayType.VALUES.size)
    }

    @Test
    fun `runs 0x00 to 0x0A with no gaps`() {
        (0x00..0x0A).forEach { value ->
            assertEquals(
                "0x%02X debe existir, sin huecos en este catálogo".format(value),
                value,
                DelayType.fromValue(value)?.value,
            )
        }
        assertNull(DelayType.fromValue(0x0B))
    }

    @Test
    fun `values are unique and fit in a 7-bit byte`() {
        assertEquals(DelayType.VALUES.size, DelayType.VALUES.toSet().size)
        assertTrue(DelayType.VALUES.all { it in 0..MidiBytes.MAX_BYTE_VALUE })
    }

    @Test
    fun `display names are unique and non-blank`() {
        val names = DelayType.entries.map { it.displayName }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.none { it.isBlank() })
    }

    @Test
    fun `spot-checks the values documented in CLAUDE md`() {
        assertEquals(0x00, DelayType.DIGITAL.value)
        assertEquals(0x07, DelayType.ANALOG.value)
        assertEquals(0x0A, DelayType.SDE_3000.value)
    }
}
