package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El desplazamiento de un valor entre lo que se muestra y lo que viaja por el cable es
 * pequeño y por eso es fácil de equivocar en un sentido y no en el otro. Estos tests fijan
 * los dos.
 */
class LevelScaleTest {

    private val panel = KatanaAddresses.PANEL_LEVEL_SCALE
    private val effect = KatanaAddresses.EFFECT_LEVEL_SCALE

    @Test
    fun `an amp knob shows exactly what it sends`() {
        assertEquals(0, panel.toRaw(0))
        assertEquals(100, panel.toRaw(100))
        assertEquals(0, panel.toDisplay(0))
        assertEquals(100, panel.toDisplay(100))
        assertEquals(0..100, panel.rawRange)
    }

    @Test
    fun `an effect knob sends one more than it shows`() {
        assertEquals(1, effect.toRaw(0))
        assertEquals(43, effect.toRaw(42))
        assertEquals(101, effect.toRaw(100))
        assertEquals(1..101, effect.rawRange)
    }

    @Test
    fun `an effect knob shows one less than it receives`() {
        assertEquals(0, effect.toDisplay(1))
        assertEquals(42, effect.toDisplay(43))
        assertEquals(100, effect.toDisplay(101))
    }

    @Test
    fun `a display value round trips through the raw byte`() {
        (0..100).forEach { shown ->
            assertEquals(shown, effect.toDisplay(effect.toRaw(shown)))
            assertEquals(shown, panel.toDisplay(panel.toRaw(shown)))
        }
    }

    @Test
    fun `raw 100 is 99 on an effect knob, which is the bug this fixes`() {
        // Lo que la app mandaba como máximo antes de corregirlo.
        assertEquals(99, effect.toDisplay(100))
    }

    @Test
    fun `off shows as the bottom of the range`() {
        assertEquals(0, effect.toDisplay(0))
    }

    @Test
    fun `off is accepted even though it sits outside the raw range`() {
        assertTrue(effect.accepts(0))
        assertTrue(effect.accepts(101))
        assertFalse(effect.accepts(102))
        // Una escala directa no tiene "off" y su 0 es un valor normal.
        assertTrue(panel.accepts(0))
        assertFalse(panel.accepts(101))
    }

    @Test
    fun `the slider cannot reach off, and that is deliberate`() {
        // Mover el slider al mínimo manda crudo 1, no 0: apagar es cosa del switch on/off.
        assertEquals(1, effect.toRaw(0))
    }

    @Test
    fun `out of range display values are clamped before conversion`() {
        assertEquals(101, effect.toRaw(500))
        assertEquals(1, effect.toRaw(-7))
    }

    // --- centered(): Booster's Bottom/Tone, `00/64/-50/+50` (CLAUDE.md §5.2) ---------------

    private val trim = LevelScale.centered(50)

    @Test
    fun `a centered scale shows zero at the middle of its raw range`() {
        assertEquals((-50)..50, trim.displayRange)
        assertEquals(0..100, trim.rawRange)
        assertEquals(50, trim.toRaw(0))
        assertEquals(0, trim.toDisplay(50))
    }

    @Test
    fun `a centered scale's extremes map both ways`() {
        assertEquals(0, trim.toRaw(-50))
        assertEquals(100, trim.toRaw(50))
        assertEquals(-50, trim.toDisplay(0))
        assertEquals(50, trim.toDisplay(100))
    }

    @Test
    fun `a centered scale has no off value`() {
        assertEquals(null, trim.offRawValue)
        assertTrue(trim.accepts(0))
        assertTrue(trim.accepts(100))
        assertFalse(trim.accepts(101))
        assertFalse(trim.accepts(-1))
    }

    @Test
    fun `a centered display value round trips through the raw byte`() {
        (-50..50).forEach { shown ->
            assertEquals(shown, trim.toDisplay(trim.toRaw(shown)))
        }
    }

    @Test
    fun `a centered scale clamps out of range display values before conversion`() {
        assertEquals(100, trim.toRaw(500))
        assertEquals(0, trim.toRaw(-500))
    }
}
