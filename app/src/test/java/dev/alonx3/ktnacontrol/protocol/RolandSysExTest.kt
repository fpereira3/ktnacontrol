package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vectors taken verbatim from the reference documentation, so a green run means our bytes
 * match what the amp is known to accept:
 *
 *  - `reference/TuxKatana/HOW.md` — the annotated request/reply traces.
 *  - `reference/katana-midi-bridge/doc/katana_sysex.txt` — the checksum worked example.
 */
class RolandSysExTest {

    private fun bytes(hex: String): ByteArray =
        hex.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.hex(): String =
        joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }

    @Test
    fun `builds the device name query from CLAUDE md`() {
        val message = RolandSysEx.get(KatanaAddresses.DEVICE_NAME, KatanaAddresses.DEVICE_NAME_SIZE)

        assertEquals(
            "F0 41 00 00 00 00 33 11 10 00 00 00 00 00 00 10 60 F7",
            message.hex(),
        )
    }

    @Test
    fun `builds the preset name queries`() {
        // HOW.md: presets 1 and 2, checksums 5F and 5E.
        assertEquals(
            "F0 41 00 00 00 00 33 11 10 01 00 00 00 00 00 10 5F F7",
            RolandSysEx.get(Address(0x10, 0x01, 0x00, 0x00), 16).hex(),
        )
        assertEquals(
            "F0 41 00 00 00 00 33 11 10 02 00 00 00 00 00 10 5E F7",
            RolandSysEx.get(Address(0x10, 0x02, 0x00, 0x00), 16).hex(),
        )
    }

    @Test
    fun `builds the memory dump query`() {
        // HOW.md: 1920 bytes from 60 00 00 00, size 00 00 0F 00, checksum 11.
        assertEquals(
            "F0 41 00 00 00 00 33 11 60 00 00 00 00 00 0F 00 11 F7",
            RolandSysEx.get(Address(0x60, 0x00, 0x00, 0x00), 1920).hex(),
        )
    }

    @Test
    fun `builds the edit mode write`() {
        // HOW.md: set edit mode on, checksum 7F.
        assertEquals(
            "F0 41 00 00 00 00 33 12 7F 00 00 01 01 7F F7",
            RolandSysEx.set(Address(0x7F, 0x00, 0x00, 0x01), bytes("01")).hex(),
        )
    }

    @Test
    fun `builds both edit mode writes`() {
        // ON: HOW.md traces this exact frame, checksum 7F.
        assertEquals(
            "F0 41 00 00 00 00 33 12 7F 00 00 01 01 7F F7",
            RolandSysEx.set(
                KatanaAddresses.EDIT_MODE,
                byteArrayOf(KatanaAddresses.EDIT_MODE_ON),
            ).hex(),
        )
        // OFF: the address alone sums to 128, so this is the case where the outer modulo
        // matters — the checksum has to be 00, never 80.
        assertEquals(
            "F0 41 00 00 00 00 33 12 7F 00 00 01 00 00 F7",
            RolandSysEx.set(
                KatanaAddresses.EDIT_MODE,
                byteArrayOf(KatanaAddresses.EDIT_MODE_OFF),
            ).hex(),
        )
    }

    @Test
    fun `builds a 2-byte SET, like the active channel recall`() {
        // reference/TuxKatana/params/config.yaml:8-17 gives data+checksum for CH_1 and CH_8;
        // Panel (00 00) is not listed there, so its checksum is derived from the same
        // algorithm rather than copied from a source.
        val address = Address(0x00, 0x01, 0x00, 0x00)
        assertEquals(
            "F0 41 00 00 00 00 33 12 00 01 00 00 00 00 7F F7", // Panel: data 00 00
            RolandSysEx.set(address, MidiBytes.encode(0, 2)).hex(),
        )
        assertEquals(
            "F0 41 00 00 00 00 33 12 00 01 00 00 00 01 7E F7", // CH_1: data 00 01, checksum 7E
            RolandSysEx.set(address, MidiBytes.encode(1, 2)).hex(),
        )
        assertEquals(
            "F0 41 00 00 00 00 33 12 00 01 00 00 00 08 77 F7", // CH_8: data 00 08, checksum 77
            RolandSysEx.set(address, MidiBytes.encode(8, 2)).hex(),
        )
    }

    @Test
    fun `builds the active channel query`() {
        // katana_sysex.txt:180-198, "Determine Current Preset": QUERY 00 01 00 00, length
        // 00 00 00 02. The exact reply is not traced byte-for-byte in the source; the
        // checksum here is the same algorithm already covered by the other GET tests.
        assertEquals(
            "F0 41 00 00 00 00 33 11 00 01 00 00 00 00 00 02 7D F7",
            RolandSysEx.get(Address(0x00, 0x01, 0x00, 0x00), 2).hex(),
        )
    }

    @Test
    fun `matches the checksum worked example of the spec`() {
        // katana_sysex.txt, "Checksum Algorithm": reverb type red -> 0x79.
        assertEquals(0x79, RolandSysEx.checksum(bytes("60 00 12 14 01")))
    }

    @Test
    fun `checksum of a multiple of 128 is zero, not 128`() {
        // 0x7F + 0x01 = 128, so the outer modulo has to bring it back to 0x00.
        assertEquals(0x00, RolandSysEx.checksum(bytes("7F 01")))
    }

    @Test
    fun `parses the device name reply`() {
        // HOW.md: the amp answers the device name query with this exact message.
        val reply = bytes(
            "F0 41 00 00 00 00 33 12 10 00 00 00 " +
                "4B 41 54 41 4E 41 20 4D 6B 32 20 20 20 20 20 20 76 F7"
        )

        val parsed = RolandSysEx.parse(reply)

        assertTrue("esperaba Data, llegó $parsed", parsed is RolandMessage.Data)
        parsed as RolandMessage.Data
        assertEquals(KatanaAddresses.DEVICE_NAME, parsed.address)
        assertEquals(16, parsed.data.size)
        assertEquals("KATANA Mk2", String(parsed.data, Charsets.US_ASCII).trim())
    }

    @Test
    fun `round trips a built message through the parser`() {
        val data = bytes("01 02 03")
        val address = Address(0x60, 0x00, 0x10, 0x20)

        val parsed = RolandSysEx.parse(RolandSysEx.set(address, data))

        assertTrue(parsed is RolandMessage.Data)
        parsed as RolandMessage.Data
        assertEquals(address, parsed.address)
        assertArrayEquals(data, parsed.data)
    }

    @Test
    fun `rejects a wrong checksum`() {
        val good = RolandSysEx.get(KatanaAddresses.DEVICE_NAME, 16)
        val corrupted = good.copyOf().also { it[it.size - 2] = 0x00 }

        val parsed = RolandSysEx.parse(corrupted)

        assertTrue(parsed is RolandMessage.Invalid)
        assertTrue(
            "el motivo debería nombrar el checksum, era '${(parsed as RolandMessage.Invalid).reason}'",
            parsed.reason.contains("checksum"),
        )
    }

    @Test
    fun `rejects a foreign header, a bad terminator and a short message`() {
        val foreign = bytes("F0 42 00 00 00 00 33 12 10 00 00 00 01 6F F7")
        assertTrue(RolandSysEx.parse(foreign) is RolandMessage.Invalid)

        val unterminated = RolandSysEx.get(KatanaAddresses.DEVICE_NAME, 16)
            .copyOf().also { it[it.size - 1] = 0x00 }
        assertTrue(RolandSysEx.parse(unterminated) is RolandMessage.Invalid)

        assertTrue(RolandSysEx.parse(bytes("F0 41 00 F7")) is RolandMessage.Invalid)
    }
}
