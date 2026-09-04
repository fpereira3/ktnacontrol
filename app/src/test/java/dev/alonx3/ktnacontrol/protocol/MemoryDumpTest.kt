package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El dump se parsea buscando cada dirección conocida dentro de los trozos que llegaron, así
 * que lo que puede fallar es la aritmética de desplazamiento: un `off by one` haría que un
 * parámetro se leyera con el valor de su vecino, y eso **no lo detecta ningún checksum**.
 *
 * Por eso los tests son estrictos con las direcciones contiguas, igual que se hizo al
 * distinguir `REVERB_LEVEL` de sus vecinas.
 */
class MemoryDumpTest {

    /** Un trozo que cubre `60 00 06 50`..`60 00 06 5B`, con el valor = último byte de la dirección. */
    private fun panelChunk() = MemoryDump.Chunk(
        base = Address(0x60, 0x00, 0x06, 0x50),
        data = ByteArray(12) { index -> (0x50 + index).toByte() },
    )

    @Test
    fun `a byte is read from the chunk that covers its address`() {
        val dump = MemoryDump(listOf(panelChunk()))

        assertEquals(0x50, dump.byteAt(Address(0x60, 0x00, 0x06, 0x50)))
        assertEquals(0x51, dump.byteAt(Address(0x60, 0x00, 0x06, 0x51)))
        assertEquals(0x5B, dump.byteAt(Address(0x60, 0x00, 0x06, 0x5B)))
    }

    @Test
    fun `each address gets its own byte, not its neighbour's`() {
        // El fallo que este test existe para atrapar: un desplazamiento de uno haría que
        // gain leyera el byte de volume y nadie se enteraría.
        val dump = MemoryDump(listOf(panelChunk()))

        assertEquals(0x51, dump.byteAt(KatanaAddresses.GAIN_LEVEL))
        assertEquals(0x52, dump.byteAt(KatanaAddresses.VOLUME_LEVEL))
        assertEquals(0x53, dump.byteAt(KatanaAddresses.BASS_LEVEL))
        assertEquals(0x54, dump.byteAt(KatanaAddresses.MIDDLE_LEVEL))
        assertEquals(0x55, dump.byteAt(KatanaAddresses.TREBLE_LEVEL))
        assertEquals(0x56, dump.byteAt(KatanaAddresses.PRESENCE_LEVEL))
        assertEquals(0x57, dump.byteAt(KatanaAddresses.BOOST_LEVEL))
        assertEquals(0x5B, dump.byteAt(KatanaAddresses.REVERB_LEVEL))
    }

    @Test
    fun `an address just outside a chunk is not covered`() {
        val dump = MemoryDump(listOf(panelChunk()))

        assertNull("uno antes del inicio", dump.byteAt(Address(0x60, 0x00, 0x06, 0x4F)))
        assertNull("uno después del final", dump.byteAt(Address(0x60, 0x00, 0x06, 0x5C)))
    }

    @Test
    fun `an address in no chunk at all is unknown, not an error`() {
        val dump = MemoryDump(listOf(panelChunk()))

        // Los on/off viven en direcciones bajas que este trozo no cubre.
        assertNull(dump.byteAt(KatanaAddresses.BOOST_ENABLED))
        assertNull(dump.byteAt(KatanaAddresses.AMP_TYPE_FULL))
    }

    @Test
    fun `several chunks are searched, and the address space carries across bytes`() {
        // `60 00 00 7F` + 1 = `60 00 01 00`: acarreo en base 128, no en base 256.
        val dump = MemoryDump(
            listOf(
                MemoryDump.Chunk(Address(0x60, 0x00, 0x00, 0x7E), byteArrayOf(0x11, 0x22, 0x33)),
                panelChunk(),
            )
        )

        assertEquals(0x11, dump.byteAt(Address(0x60, 0x00, 0x00, 0x7E)))
        assertEquals(0x22, dump.byteAt(Address(0x60, 0x00, 0x00, 0x7F)))
        assertEquals("el tercer byte cae ya en 60 00 01 00", 0x33, dump.byteAt(Address(0x60, 0x00, 0x01, 0x00)))
        assertEquals("y el otro trozo se sigue viendo", 0x51, dump.byteAt(KatanaAddresses.GAIN_LEVEL))
    }

    @Test
    fun `valuesAt returns only what the dump covers`() {
        val dump = MemoryDump(listOf(panelChunk()))

        val values = dump.valuesAt(
            listOf(
                KatanaAddresses.GAIN_LEVEL,
                KatanaAddresses.BOOST_ENABLED, // fuera del trozo
                KatanaAddresses.REVERB_LEVEL,
            )
        )

        assertEquals(2, values.size)
        assertEquals(0x51, values[KatanaAddresses.GAIN_LEVEL])
        assertEquals(0x5B, values[KatanaAddresses.REVERB_LEVEL])
        assertFalse(values.containsKey(KatanaAddresses.BOOST_ENABLED))
    }

    @Test
    fun `an empty dump answers unknown to everything without failing`() {
        val dump = MemoryDump(emptyList())

        assertNull(dump.byteAt(KatanaAddresses.GAIN_LEVEL))
        assertEquals(0, dump.dataByteCount)
        assertTrue(dump.valuesAt(listOf(KatanaAddresses.GAIN_LEVEL)).isEmpty())
    }

    @Test
    fun `a dump is built from the parsed messages the amp answered`() {
        // Mensajes reales tal y como los devuelve RolandSysEx.parse.
        val first = RolandSysEx.set(Address(0x60, 0x00, 0x06, 0x50), byteArrayOf(0x04, 0x3A))
        val second = RolandSysEx.set(Address(0x60, 0x00, 0x00, 0x10), byteArrayOf(0x01))
        val messages = listOf(first, second).map { RolandSysEx.parse(it) }
            .filterIsInstance<RolandMessage.Data>()

        val dump = MemoryDump.from(messages)

        assertEquals(2, dump.chunks.size)
        assertEquals(3, dump.dataByteCount)
        assertEquals(0x04, dump.byteAt(KatanaAddresses.AMP_TYPE_PANEL))
        assertEquals(0x3A, dump.byteAt(KatanaAddresses.GAIN_LEVEL))
        assertEquals(0x01, dump.byteAt(KatanaAddresses.BOOST_ENABLED))
    }

    @Test
    fun `data bytes are unsigned, so values above 0x7F would not come back negative`() {
        // Un byte SysEx nunca pasa de 0x7F, pero si el amp mandara basura no debe leerse
        // como negativo y colarse en un rango por accidente.
        val dump = MemoryDump(
            listOf(MemoryDump.Chunk(Address(0x60, 0x00, 0x06, 0x51), byteArrayOf(0xFF.toByte())))
        )
        assertEquals(255, dump.byteAt(KatanaAddresses.GAIN_LEVEL))
    }
}
