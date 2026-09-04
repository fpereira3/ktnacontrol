package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Booster catalogue as CLAUDE.md §5.2 documents it, from the two Mk2 sources that agree
 * byte for byte: `booster.yaml:20-43` and `midi.xml:43568-43590`.
 *
 * These tests say nothing about whether the amp obeys any of it — that is what the audio test
 * is for. What they pin down is the table itself, which is the part a typo could break
 * silently.
 */
class BoostTypeTest {

    @Test
    fun `has the 23 types both Mk2 sources list`() {
        assertEquals(23, BoostType.entries.size)
        assertEquals(23, BoostType.VALUES.size)
    }

    @Test
    fun `0x07 is a real gap in the catalogue`() {
        // Falta en las dos fuentes, así que no es un error de transcripción: es como el 0x19
        // de AmpType. Un selector con huecos es justo lo que KatanaEnumParameter rechaza.
        assertNull("0x07 no debe existir", BoostType.fromValue(0x07))
        assertTrue("0x07 no debe estar entre los valores", 0x07 !in BoostType.VALUES)

        // Sus vecinos sí existen, que es lo que hace del hueco un hueco y no un recorte.
        assertEquals(BoostType.FAT_DS, BoostType.fromValue(0x06))
        assertEquals(BoostType.METAL_DS, BoostType.fromValue(0x08))
    }

    @Test
    fun `covers 0x00 to 0x17 with no other gaps`() {
        val missing = (0x00..0x17).filter { value -> BoostType.fromValue(value) == null }
        assertEquals("el único hueco debe ser 0x07", listOf(0x07), missing)
    }

    @Test
    fun `values are unique and fit in a 7-bit byte`() {
        assertEquals(BoostType.VALUES.size, BoostType.VALUES.toSet().size)
        assertTrue(BoostType.VALUES.all { it in 0..MidiBytes.MAX_BYTE_VALUE })
    }

    @Test
    fun `display names are unique and non-blank`() {
        val names = BoostType.entries.map { it.displayName }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.none { it.isBlank() })
    }

    @Test
    fun `spot-checks the values documented in CLAUDE md`() {
        assertEquals(0x00, BoostType.MID_BOOST.value)
        assertEquals(0x01, BoostType.CLEAN_BOOST.value)
        assertEquals(0x0C, BoostType.TUBE_SCREAMER.value)
        assertEquals(0x0E, BoostType.DISTORTION.value)
        assertEquals(0x0F, BoostType.RAT.value)
        assertEquals(0x17, BoostType.CENTA_OD.value)
    }

    @Test
    fun `the values Adresses txt observed on a real amp all exist`() {
        // Adresses.txt:73 anotó `60 00 00 11: [0A|0B|0E]` mirando su propio amplificador, o
        // sea que esos tres valores son reales, no solo tabla.
        listOf(0x0A, 0x0B, 0x0E).forEach { value ->
            assertNotNull("el amp reportó $value, debe estar en la tabla", BoostType.fromValue(value))
        }
        assertEquals(BoostType.BLUES_DRIVE, BoostType.fromValue(0x0A))
        assertEquals(BoostType.OVER_DRIVE, BoostType.fromValue(0x0B))
        assertEquals(BoostType.DISTORTION, BoostType.fromValue(0x0E))
    }
}
