package dev.alonx3.ktnacontrol.device.model

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.AmpCategory
import dev.alonx3.ktnacontrol.protocol.AmpType
import dev.alonx3.ktnacontrol.protocol.EffectColor
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.MemoryDump
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Un dump sintético con los valores exactos que reportó el amplificador real el 2026-09-03,
 * para comprobar que salen por el otro lado tal cual — incluida la escala de los niveles de
 * efecto, que es donde un error se vería como "todo bien pero uno menos".
 */
class AmpStateTest {

    /** El bloque de perillas `06 50`..`06 5C`, con los valores del log real. */
    private val panel = MemoryDump.Chunk(
        base = KatanaAddresses.AMP_TYPE_PANEL,
        data = byteArrayOf(
            0x04,       // 06 50 amp category = Brown
            0x3A,       // 06 51 gain 58
            0x64,       // 06 52 volume 100
            0x26,       // 06 53 bass 38
            0x2B,       // 06 54 middle 43
            0x2D,       // 06 55 treble 45
            0x41,       // 06 56 presence 65
            0x00,       // 06 57 boost = Off
            0x00,       // 06 58 mod = Off
            0x00,       // 06 59 fx = Off
            0x4E,       // 06 5A delay crudo 78 -> 77
            0x65,       // 06 5B reverb crudo 101 -> 100
            0x00,       // 06 5C variación off
        ),
    )

    /** Los cinco selectores de color, `06 39`..`06 3D`. */
    private val colors = MemoryDump.Chunk(
        base = KatanaAddresses.BOOST_COLOR,
        data = byteArrayOf(0x00, 0x00, 0x00, 0x02, 0x00),
    )

    private val ampType = MemoryDump.Chunk(
        base = KatanaAddresses.AMP_TYPE_FULL,
        data = byteArrayOf(0x17), // Brown
    )

    private fun dump(vararg chunks: MemoryDump.Chunk) = MemoryDump(chunks.toList())

    @Test
    fun `the amp and EQ levels come through unchanged`() {
        val state = AmpState.from(dump(panel))

        assertEquals(58, state.gain)
        assertEquals(100, state.volume)
        assertEquals(38, state.bass)
        assertEquals(43, state.middle)
        assertEquals(45, state.treble)
        assertEquals(65, state.presence)
    }

    @Test
    fun `the effect levels come through with the offset applied`() {
        val state = AmpState.from(dump(panel))

        assertEquals("crudo 78", 77, state.delay.level)
        assertEquals("crudo 101", 100, state.reverb.level)
        assertEquals("crudo 0 es Off, se muestra como 0", 0, state.boost.level)
    }

    @Test
    fun `the amp type is read from its own address, not from the panel knob`() {
        val state = AmpState.from(dump(panel, ampType))

        assertEquals(AmpCategory.BROWN, state.ampCategory)
        assertEquals(AmpType.BROWN, state.ampType)
        assertEquals(false, state.variationOn)
        // Los dos valen "Brown" pero son bytes distintos: 0x04 la perilla, 0x17 el modelo.
        assertEquals(0x04, state.ampCategory?.value)
        assertEquals(0x17, state.ampType?.value)
    }

    @Test
    fun `colours are read per effect without crossing over`() {
        val state = AmpState.from(dump(colors))

        assertEquals(EffectColor.GREEN, state.boost.color)
        assertEquals(EffectColor.GREEN, state.mod.color)
        assertEquals(EffectColor.GREEN, state.fx.color)
        assertEquals("delay estaba en amarillo", EffectColor.YELLOW, state.delay.color)
        assertEquals(EffectColor.GREEN, state.reverb.color)
    }

    @Test
    fun `the on-off switches are read from their scattered low addresses`() {
        val state = AmpState.from(
            dump(
                MemoryDump.Chunk(KatanaAddresses.BOOST_ENABLED, byteArrayOf(0x00)),
                MemoryDump.Chunk(KatanaAddresses.MOD_ENABLED, byteArrayOf(0x00)),
                MemoryDump.Chunk(KatanaAddresses.FX_ENABLED, byteArrayOf(0x00)),
                MemoryDump.Chunk(KatanaAddresses.DELAY_ENABLED, byteArrayOf(0x01)),
                MemoryDump.Chunk(KatanaAddresses.REVERB_ENABLED, byteArrayOf(0x01)),
            )
        )

        assertEquals(false, state.boost.enabled)
        assertEquals(false, state.mod.enabled)
        assertEquals(false, state.fx.enabled)
        assertEquals(true, state.delay.enabled)
        assertEquals(true, state.reverb.enabled)
    }

    @Test
    fun `what the dump does not cover stays null, and that is not an error`() {
        val state = AmpState.from(dump(panel))

        assertNull("los on/off viven fuera de este trozo", state.boost.enabled)
        assertNull(state.boost.color)
        assertNull(state.ampType)
        // Pero lo que sí venía se leyó igual.
        assertEquals(58, state.gain)
    }

    @Test
    fun `an empty dump gives everything unknown without failing`() {
        val state = AmpState.from(MemoryDump(emptyList()))

        assertEquals(0, state.knownCount)
        assertNull(state.gain)
        assertNull(state.reverb.level)
    }

    @Test
    fun `knownCount counts exactly what came back`() {
        // panel: 6 niveles + categoría + variación + 5 niveles de efecto = 13.
        assertEquals(13, AmpState.from(dump(panel)).knownCount)
        // más los 5 colores y el modelo = 19.
        assertEquals(19, AmpState.from(dump(panel, colors, ampType)).knownCount)
    }

    @Test
    fun `an unlisted amp type byte is unknown rather than a wrong model`() {
        val state = AmpState.from(
            dump(MemoryDump.Chunk(KatanaAddresses.AMP_TYPE_FULL, byteArrayOf(0x19)))
        )
        assertNull("0x19 no está en la tabla de la dirección", state.ampType)
    }

    @Test
    fun `the summary names what it knows and marks the rest`() {
        val summary = AmpState.from(dump(panel, colors, ampType)).summary()

        assertEquals(true, summary.contains("Brown"))
        assertEquals("delay estaba encendido pero su on/off no venía", true, summary.contains("?/yellow/77"))
        assertEquals("y lo desconocido se ve", true, summary.contains("?"))
    }
}
