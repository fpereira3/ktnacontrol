package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The MOD/FX catalogue as CLAUDE.md §5.2 documents it, from the two Mk2 sources that agree:
 * `mod.yaml:11-42` = `fx.yaml:11-42`, and `midi.xml:43642-43674` (MOD green) which diffs
 * clean against `43741-43773` (FX green).
 */
class ModFxTypeTest {

    @Test
    fun `has the 31 types both Mk2 sources list`() {
        assertEquals(31, ModFxType.entries.size)
        assertEquals(31, ModFxType.VALUES.size)
    }

    @Test
    fun `the ten gaps are real, and their neighbours are not`() {
        // Faltan en las dos fuentes, así que no son erratas: son huecos del propio amp.
        val gaps = listOf(0x05, 0x08, 0x0B, 0x0D, 0x11, 0x18, 0x1E, 0x20, 0x21, 0x22)
        gaps.forEach { value ->
            assertNull("0x%02X no debe existir".format(value), ModFxType.fromValue(value))
        }

        val missing = (0x00..0x28).filter { value -> ModFxType.fromValue(value) == null }
        assertEquals("los huecos deben ser exactamente esos diez", gaps, missing)
    }

    @Test
    fun `values are unique and fit in a 7-bit byte`() {
        assertEquals(ModFxType.VALUES.size, ModFxType.VALUES.toSet().size)
        assertTrue(ModFxType.VALUES.all { it in 0..MidiBytes.MAX_BYTE_VALUE })
    }

    @Test
    fun `display names are unique and non-blank`() {
        val names = ModFxType.entries.map { it.displayName }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.none { it.isBlank() })
    }

    @Test
    fun `spot-checks the values documented in CLAUDE md`() {
        assertEquals(0x00, ModFxType.TOUCH_WAH.value)
        assertEquals(0x13, ModFxType.PHASER.value)
        assertEquals(0x14, ModFxType.FLANGER.value)
        assertEquals(0x1D, ModFxType.CHORUS.value)
        assertEquals(0x28, ModFxType.PEDAL_BEND.value)
    }

    @Test
    fun `the values Adresses txt observed on a real amp all exist`() {
        // Adresses.txt:86 anotó `60 00 01 01 -> [1D|14|13]` mirando su propio amplificador,
        // con los tres colores: Chorus, Flanger y Phaser.
        listOf(0x1D, 0x14, 0x13).forEach { value ->
            assertNotNull("el amp reportó $value, debe estar en la tabla", ModFxType.fromValue(value))
        }
        assertEquals(ModFxType.CHORUS, ModFxType.fromValue(0x1D))
        assertEquals(ModFxType.FLANGER, ModFxType.fromValue(0x14))
        assertEquals(ModFxType.PHASER, ModFxType.fromValue(0x13))
    }

    @Test
    fun `does not overlap the Booster catalogue by accident`() {
        // Son catálogos distintos aunque los valores bajos se solapen numéricamente: nada
        // debe traducir un valor de un efecto con la tabla del otro.
        assertEquals("Touch Wah", ModFxType.fromValue(0x00)?.displayName)
        assertEquals("Mid Boost", BoostType.fromValue(0x00)?.displayName)
    }
}
