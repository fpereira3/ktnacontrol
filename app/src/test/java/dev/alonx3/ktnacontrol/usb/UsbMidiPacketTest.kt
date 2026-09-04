package dev.alonx3.ktnacontrol.usb

import org.junit.Assert.assertThrows
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the USB-MIDI 4-byte packet encoding (CLAUDE.md §4.1).
 *
 * These matter before any on-device test: if the packing is wrong, whatever the amp does or
 * does not answer tells us nothing.
 */
class UsbMidiPacketTest {

    private fun bytes(hex: String): ByteArray =
        hex.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `packs the handshake into five packets`() {
        // 15 bytes, an exact multiple of 3, so the last packet is a full one tagged 0x7.
        val handshake = KatanaHandshake.message(KatanaHandshake.MODEL_ID_KATANA)
        assertEquals(15, handshake.size)

        assertEquals(
            "04 F0 7E 00 04 06 02 41 04 33 03 00 04 00 00 00 07 00 00 F7",
            packUsbMidi(handshake).toHexString(),
        )
    }

    @Test
    fun `packs the identity request`() {
        val identityRequest = bytes("F0 7E 7F 06 01 F7")

        assertEquals("04 F0 7E 7F 07 06 01 F7", packUsbMidi(identityRequest).toHexString())
    }

    @Test
    fun `tags the last packet by how many bytes are left over`() {
        // 7 bytes -> 3 + 3 + 1, so the last CIN is 0x5 and two slots are padded.
        assertEquals(
            "04 F0 01 02 04 03 04 05 05 F7 00 00",
            packUsbMidi(bytes("F0 01 02 03 04 05 F7")).toHexString(),
        )
        // 8 bytes -> 3 + 3 + 2, last CIN 0x6 and one padded slot.
        assertEquals(
            "04 F0 01 02 04 03 04 05 06 06 F7 00",
            packUsbMidi(bytes("F0 01 02 03 04 05 06 F7")).toHexString(),
        )
    }

    @Test
    fun `packs an empty message into nothing`() {
        assertEquals(0, packUsbMidi(ByteArray(0)).size)
    }

    @Test
    fun `round trips messages of every length modulo three`() {
        for (size in 1..64) {
            val message = ByteArray(size) { index -> (index % 0x80).toByte() }

            assertArrayEquals(
                "el round trip falló con $size bytes",
                message,
                unpackUsbMidi(packUsbMidi(message)),
            )
        }
    }

    @Test
    fun `unpacks a reply split across several packets`() {
        // The Katana identity reply documented in reference/TuxKatana/HOW.md.
        val reply = bytes("F0 7E 00 06 02 41 33 03 00 00 06 00 00 00 F7")

        assertArrayEquals(reply, unpackUsbMidi(packUsbMidi(reply)))
    }

    @Test
    fun `unpacking ignores zero padding and short trailing chunks`() {
        val padded = bytes("04 F0 7E 7F 07 06 01 F7 00 00 00 00 04 F0")

        assertEquals("F0 7E 7F 06 01 F7", unpackUsbMidi(padded).toHexString())
    }

    @Test
    fun `unpacking honours the payload length of non sysex messages`() {
        // CIN 0xC = Program Change, 2 bytes; CIN 0xB = Control Change, 3 bytes.
        val messages = bytes("0C C0 05 00 0B B0 10 7F")

        assertEquals("C0 05 B0 10 7F", unpackUsbMidi(messages).toHexString())
    }

    @Test
    fun `unpacking a buffer shorter than one packet yields nothing`() {
        assertEquals(0, unpackUsbMidi(bytes("04 F0 7E")).size)
    }

    // --- Handshake ---------------------------------------------------------------------

    @Test
    fun `the generic handshake is the frame the reference library sends`() {
        assertEquals(
            "F0 7E 00 06 02 41 33 03 00 00 00 00 00 00 F7",
            KatanaHandshake.message().hex(),
        )
    }

    @Test
    fun `the real-version handshake differs from the generic one in a single byte`() {
        val generic = KatanaHandshake.message()
        val real = KatanaHandshake.message(version = KatanaHandshake.VERSION_REPORTED)

        assertEquals(
            "F0 7E 00 06 02 41 33 03 00 00 06 00 00 00 F7",
            real.hex(),
        )
        assertEquals("misma longitud", generic.size, real.size)
        assertEquals(
            "un solo byte de diferencia",
            1,
            generic.indices.count { generic[it] != real[it] },
        )
    }

    @Test
    fun `the real-version handshake is byte for byte the reply the amp sent`() {
        // Identity Reply real del amplificador, 2026-09-02. La hipótesis del handshake mudo
        // es justamente que la trama debe ser idéntica a esta.
        val reply = byteArrayOf(
            0xF0.toByte(), 0x7E, 0x00, 0x06, 0x02, 0x41, 0x33, 0x03,
            0x00, 0x00, 0x06, 0x00, 0x00, 0x00, 0xF7.toByte(),
        )
        assertArrayEquals(reply, KatanaHandshake.message(version = KatanaHandshake.VERSION_REPORTED))
    }

    @Test
    fun `a version field of the wrong length is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            KatanaHandshake.message(version = byteArrayOf(0x06, 0x00))
        }
    }

    private fun ByteArray.hex() = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
