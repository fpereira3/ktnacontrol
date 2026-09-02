package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Base-128 arithmetic: the classic source of bugs in this protocol (CLAUDE.md §5). */
class MidiBytesTest {

    private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()

    @Test
    fun `encodes the sizes used by the real queries`() {
        assertArrayEquals(bytes(0x00, 0x00, 0x00, 0x10), MidiBytes.encode(16, 4))
        // The memory dump: 1920 bytes is 00 00 0F 00, not 00 00 07 80.
        assertArrayEquals(bytes(0x00, 0x00, 0x0F, 0x00), MidiBytes.encode(1920, 4))
    }

    @Test
    fun `carries at 128, not at 256`() {
        assertArrayEquals(bytes(0x00, 0x7F), MidiBytes.encode(127, 2))
        assertArrayEquals(bytes(0x01, 0x00), MidiBytes.encode(128, 2))
    }

    @Test
    fun `decode is the inverse of encode`() {
        for (value in listOf(0, 1, 127, 128, 255, 1920, 16_383, 2_097_151)) {
            assertEquals(
                "falló con $value",
                value,
                MidiBytes.decode(MidiBytes.encode(value, 4)),
            )
        }
    }

    @Test
    fun `decode ignores the high bit instead of corrupting the value`() {
        // 0xF7 would be 247 read as a plain byte; as a 7-bit value it is 0x77.
        assertEquals(0x77, MidiBytes.decode(bytes(0xF7)))
    }

    @Test
    fun `rejects values that do not fit and invalid lengths`() {
        // 128 needs two 7-bit bytes.
        assertThrows(IllegalArgumentException::class.java) { MidiBytes.encode(128, 1) }
        assertThrows(IllegalArgumentException::class.java) { MidiBytes.encode(-1, 4) }
        assertThrows(IllegalArgumentException::class.java) { MidiBytes.encode(1, 0) }
    }

    @Test
    fun `detects bytes that are not seven bit`() {
        assertTrue(MidiBytes.isSevenBit(bytes(0x00, 0x7F)))
        assertFalse(MidiBytes.isSevenBit(bytes(0x00, 0x80)))
        assertFalse(MidiBytes.isSevenBit(bytes(0xF7)))
    }
}
