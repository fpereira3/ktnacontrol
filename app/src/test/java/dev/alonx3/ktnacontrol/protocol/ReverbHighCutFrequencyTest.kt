package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reverb's High Cut catalogue, from `reference/FxFloorboard/midi.xml:42912-42922` — the only
 * Mk2 source that documents it (CLAUDE.md §5.2).
 */
class ReverbHighCutFrequencyTest {

    @Test
    fun `has the 15 frequencies midi xml lists`() {
        assertEquals(15, ReverbHighCutFrequency.entries.size)
        assertEquals(15, ReverbHighCutFrequency.VALUES.size)
    }

    @Test
    fun `runs 0x00 to 0x0E with no gaps`() {
        (0x00..0x0E).forEach { value ->
            assertEquals(
                "0x%02X debe existir, sin huecos en este catálogo".format(value),
                value,
                ReverbHighCutFrequency.fromValue(value)?.value,
            )
        }
        assertNull(ReverbHighCutFrequency.fromValue(0x0F))
    }

    @Test
    fun `values are unique and fit in a 7-bit byte`() {
        assertEquals(ReverbHighCutFrequency.VALUES.size, ReverbHighCutFrequency.VALUES.toSet().size)
        assertTrue(ReverbHighCutFrequency.VALUES.all { it in 0..MidiBytes.MAX_BYTE_VALUE })
    }

    @Test
    fun `FLAT is the last entry, no cut at all`() {
        assertEquals(0x0E, ReverbHighCutFrequency.FLAT.value)
    }

    @Test
    fun `0x0A is 6point00k, unlike Delay's own High Cut`() {
        // La misma posición en el bloque de Delay 1 es "6.30K": las dos fuentes de la misma
        // sección de midi.xml se contradicen en esta única fila. Ver KDoc de la clase.
        assertEquals("6.00k", ReverbHighCutFrequency.KHZ_6_00.displayName)
        assertEquals("6.30K", DelayHighCutFrequency.KHZ_6_30.displayName)
    }
}
