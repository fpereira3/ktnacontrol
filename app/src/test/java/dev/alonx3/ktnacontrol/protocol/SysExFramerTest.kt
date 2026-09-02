package dev.alonx3.ktnacontrol.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A `bulkTransfer` can hand back a message cut in half, several at once, or leftovers from
 * before we started listening. These cover each of those shapes.
 */
class SysExFramerTest {

    private fun bytes(hex: String): ByteArray =
        hex.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

    private fun List<ByteArray>.hex(): List<String> =
        map { message -> message.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) } }

    private val identityReply = "F0 7E 00 06 02 41 33 03 00 00 06 00 00 00 F7"

    @Test
    fun `a whole message in a single chunk`() {
        val framer = SysExFramer()

        assertEquals(listOf(identityReply), framer.feed(bytes(identityReply)).hex())
        assertEquals(0, framer.pendingBytes)
    }

    @Test
    fun `a message split across two chunks`() {
        val framer = SysExFramer()

        // Nothing is emitted until the F7 arrives.
        assertTrue(framer.feed(bytes("F0 7E 00 06 02 41 33")).isEmpty())
        assertTrue(framer.pendingBytes > 0)

        assertEquals(
            listOf(identityReply),
            framer.feed(bytes("03 00 00 06 00 00 00 F7")).hex(),
        )
        assertEquals(0, framer.pendingBytes)
    }

    @Test
    fun `two messages in one chunk`() {
        val framer = SysExFramer()
        val first = "F0 41 00 00 00 00 33 12 10 00 00 00 01 6F F7"
        val second = "F0 7E 7F 06 01 F7"

        assertEquals(listOf(first, second), framer.feed(bytes("$first $second")).hex())
    }

    @Test
    fun `garbage before an F0 is discarded without breaking the next message`() {
        val framer = SysExFramer()

        // Leftovers from a message that started before we were listening.
        val stream = "11 22 33 F7 44 $identityReply"

        assertEquals(listOf(identityReply), framer.feed(bytes(stream)).hex())
    }

    @Test
    fun `garbage between two messages is discarded`() {
        val framer = SysExFramer()
        val short = "F0 7E 7F 06 01 F7"

        assertEquals(
            listOf(short, short),
            framer.feed(bytes("$short 00 11 22 $short")).hex(),
        )
    }

    @Test
    fun `an F0 arriving mid message restarts it instead of merging`() {
        val framer = SysExFramer()
        val truncated = "F0 41 00 00"

        val messages = framer.feed(bytes("$truncated $identityReply"))

        assertEquals(listOf(identityReply), messages.hex())
    }

    @Test
    fun `reassembles a message fed one byte at a time`() {
        val framer = SysExFramer()
        val message = bytes(identityReply)
        val collected = mutableListOf<ByteArray>()

        message.forEach { byte -> collected += framer.feed(byteArrayOf(byte)) }

        assertEquals(listOf(identityReply), collected.hex())
    }

    @Test
    fun `drops a message that never terminates instead of growing forever`() {
        val framer = SysExFramer(maxMessageSize = 8)

        assertTrue(framer.feed(bytes("F0 01 02 03 04 05 06 07 08 09 0A")).isEmpty())
        assertEquals(0, framer.pendingBytes)

        // And it can still frame the next message that does fit in the limit.
        val short = "F0 7E 7F 06 01 F7"
        assertEquals(listOf(short), framer.feed(bytes(short)).hex())
    }

    @Test
    fun `an empty chunk yields nothing and keeps the pending state`() {
        val framer = SysExFramer()
        framer.feed(bytes("F0 7E 00"))

        assertTrue(framer.feed(ByteArray(0)).isEmpty())
        assertEquals(3, framer.pendingBytes)

        framer.reset()
        assertEquals(0, framer.pendingBytes)
    }

    @Test
    fun `reassembles a memory dump sized message split into 512 byte reads`() {
        // Worst case of the real protocol: 1920 data bytes + 14 of overhead, arriving in
        // chunks the size of one bulkTransfer. This is what a dump exercises end to end.
        val payload = ByteArray(KatanaAddresses.MEMORY_DUMP_SIZE) { index -> (index % 0x80).toByte() }
        val message = RolandSysEx.set(KatanaAddresses.MEMORY_DUMP, payload)
        assertEquals(1934, message.size)

        val framer = SysExFramer()
        val collected = mutableListOf<ByteArray>()
        var offset = 0
        var reads = 0
        while (offset < message.size) {
            val end = minOf(offset + BULK_TRANSFER_SIZE, message.size)
            collected += framer.feed(message.copyOfRange(offset, end))
            offset = end
            reads++
        }

        assertEquals("debería haber hecho falta más de una lectura", 4, reads)
        assertEquals(1, collected.size)
        assertArrayEquals(message, collected.single())

        // And it still parses: the framing did not corrupt the checksum.
        val parsed = RolandSysEx.parse(collected.single())
        assertTrue(parsed is RolandMessage.Data)
        assertArrayEquals(payload, (parsed as RolandMessage.Data).data)
    }

    private companion object {
        /** `wMaxPacketSize` of the bulk endpoints, i.e. the most one read can return. */
        const val BULK_TRANSFER_SIZE = 512
    }
}
