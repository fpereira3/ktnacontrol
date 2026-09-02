package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class AddressTest {

    @Test
    fun `converts to bytes and back`() {
        val address = Address(0x10, 0x00, 0x00, 0x00)

        assertArrayEquals(byteArrayOf(0x10, 0x00, 0x00, 0x00), address.toByteArray())
        assertEquals(address, Address.fromBytes(address.toByteArray()))
    }

    @Test
    fun `prints the way the protocol docs write it`() {
        assertEquals("60 00 0F 00", Address(0x60, 0x00, 0x0F, 0x00).toString())
    }

    @Test
    fun `adding an offset carries in base 128`() {
        // HOW.md walks the memory dump in 241-byte steps: 60 00 00 00 -> 60 00 01 71.
        assertEquals(
            Address(0x60, 0x00, 0x01, 0x71),
            Address(0x60, 0x00, 0x00, 0x00) + 241,
        )
        assertEquals(
            Address(0x60, 0x00, 0x03, 0x62),
            Address(0x60, 0x00, 0x01, 0x71) + 241,
        )
    }

    @Test
    fun `carries across a byte boundary at 128`() {
        assertEquals(Address(0x00, 0x00, 0x01, 0x00), Address(0x00, 0x00, 0x00, 0x7F) + 1)
    }

    @Test
    fun `subtraction gives the distance in bytes`() {
        assertEquals(241, Address(0x60, 0x00, 0x01, 0x71) - Address(0x60, 0x00, 0x00, 0x00))
    }

    @Test
    fun `rejects bytes outside seven bits`() {
        assertThrows(IllegalArgumentException::class.java) { Address(0x80, 0, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { Address(0, 0, 0, -1) }
        assertThrows(IllegalArgumentException::class.java) {
            Address.fromBytes(byteArrayOf(0x10, 0x00, 0x00))
        }
    }

    @Test
    fun `equality is structural, so a parsed address matches a constant`() {
        // Question (d): rules out a silent comparison bug in KatanaRepository. An address
        // rebuilt from the bytes of an incoming message must equal the declared constant.
        val fromWire = Address.fromBytes(KatanaAddresses.REVERB_LEVEL.toByteArray())

        assertEquals(KatanaAddresses.REVERB_LEVEL, fromWire)
        assertEquals(KatanaAddresses.REVERB_LEVEL.hashCode(), fromWire.hashCode())
        assertTrue(KatanaAddresses.REVERB_LEVEL == fromWire)

        // And a neighbouring address must NOT match.
        @Suppress("DEPRECATION")
        assertNotEquals(KatanaAddresses.REVERB_LEVEL, KatanaAddresses.REVERB_LEVEL_DERIVED)
    }
}
