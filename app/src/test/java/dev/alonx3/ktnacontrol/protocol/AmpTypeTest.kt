package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The enum tables are transcriptions of `reference/` documents, so what can go wrong is a
 * typo: a duplicated value, one out of 7-bit range, or a missing entry.
 *
 * These prove the **table** is well formed. They prove nothing about whether the amplifier
 * accepts these values — that needs the hardware (CLAUDE.md §5).
 */
class AmpTypeTest {

    @Test
    fun `amp types are the 30 that midi xml lists`() {
        assertEquals(30, AmpType.entries.size)
    }

    @Test
    fun `amp type values are unique`() {
        assertEquals(AmpType.entries.size, AmpType.VALUES.toSet().size)
    }

    @Test
    fun `every amp type value fits in a 7-bit SysEx byte`() {
        AmpType.entries.forEach { type ->
            assertTrue("${type.name} = ${type.value}", type.value in 0..0x7F)
        }
    }

    @Test
    fun `amp type values round trip`() {
        AmpType.entries.forEach { type ->
            assertEquals(type, AmpType.fromValue(type.value))
        }
    }

    @Test
    fun `an unlisted amp type value maps to null`() {
        // 0x19 es "Custom", que solo aparece en el bloque de conversión de midi.xml y no en
        // la tabla de la dirección 00 21; 0x21 está fuera del rango de la tabla.
        assertNull(AmpType.fromValue(0x19))
        assertNull(AmpType.fromValue(0x21))
    }

    @Test
    fun `BG Lead is present, which is what TuxKatana misses`() {
        // amplifier.yaml y Adresses.txt tienen 29 entradas y se saltan 0x10; midi.xml no.
        assertEquals(AmpType.BG_LEAD, AmpType.fromValue(0x10))
    }

    @Test
    fun `the five panel categories are 0 to 4 in panel order`() {
        assertEquals(listOf(0, 1, 2, 3, 4), AmpCategory.VALUES)
        assertEquals(AmpCategory.ACOUSTIC, AmpCategory.fromValue(0))
        assertEquals(AmpCategory.BROWN, AmpCategory.fromValue(4))
        assertNull(AmpCategory.fromValue(5))
    }

    @Test
    fun `the panel categories are a different space from the full amp types`() {
        // El error fácil: creer que 60 00 06 50 y 60 00 00 21 hablan el mismo idioma.
        // "Clean" es 0x01 como categoría de perilla y 0x08 como modelo.
        assertEquals(0x01, AmpCategory.CLEAN.value)
        assertEquals(0x08, AmpType.CLEAN.value)
    }

    // --- Variación: la categoría es la que sabe a qué modelo saltar --------------------
    //
    // `60 00 06 5C` solo reporta (confirmado 2026-09-03), así que la variación se cambia
    // escribiendo el modelo. Este mapeo es lo que hace posible ese rodeo.

    @Test
    fun `every category maps to a base and a variation type`() {
        AmpCategory.entries.forEach { category ->
            val base = AmpType.fromValue(category.baseValue)
            val variation = AmpType.fromValue(category.variationValue)
            assertEquals("base de ${category.name}", category, base?.category)
            assertEquals("var de ${category.name}", category, variation?.category)
            assertEquals(false, base?.isVariation)
            assertEquals(true, variation?.isVariation)
        }
    }

    @Test
    fun `typeValue picks the base or the variation`() {
        assertEquals(0x17, AmpCategory.BROWN.typeValue(variation = false))
        assertEquals(0x20, AmpCategory.BROWN.typeValue(variation = true))
        assertEquals(0x08, AmpCategory.CLEAN.typeValue(variation = false))
        assertEquals(0x1D, AmpCategory.CLEAN.typeValue(variation = true))
    }

    @Test
    fun `the ten paired types are exactly the five bases and five variations`() {
        val paired = AmpType.entries.filter { it.category != null }
        assertEquals(10, paired.size)
        assertEquals(5, paired.count { it.isVariation })
    }

    @Test
    fun `an individual amp has no category, so variation does not apply`() {
        // Los "sneaky amps" se eligen enteros; el botón VARIATION no tiene a qué referirse.
        assertNull(AmpType.CORE_METAL.category)
        assertNull(AmpType.MS_1959_I_II.category)
        assertEquals(false, AmpType.CORE_METAL.isVariation)
    }

    @Test
    fun `effect colours are green red yellow as 0 1 2`() {
        assertEquals(listOf(0, 1, 2), EffectColor.VALUES)
        assertEquals(EffectColor.GREEN, EffectColor.fromValue(0))
        assertEquals(EffectColor.RED, EffectColor.fromValue(1))
        assertEquals(EffectColor.YELLOW, EffectColor.fromValue(2))
        assertNull(EffectColor.fromValue(3))
    }
}
