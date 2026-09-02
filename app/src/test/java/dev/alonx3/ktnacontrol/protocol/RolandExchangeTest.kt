package dev.alonx3.ktnacontrol.protocol

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The correlation logic is pure Kotlin over a [kotlinx.coroutines.flow.Flow], so it can be
 * driven here with a fake stream — no `UsbManager`, no device.
 *
 * Timeouts are deliberately short so the suite stays fast on real time; that also avoids
 * pulling in `kotlinx-coroutines-test` just for this (CLAUDE.md §6 keeps the dependency list
 * short).
 */
class RolandExchangeTest {

    private val deviceName = Address(0x10, 0x00, 0x00, 0x00)
    private val presetOne = Address(0x10, 0x01, 0x00, 0x00)

    /** A reply carrying [text] padded to 16 bytes, the way the amp answers a name query. */
    private fun nameReply(address: Address, text: String): ByteArray =
        RolandSysEx.set(address, text.padEnd(16).toByteArray(Charsets.US_ASCII))

    private fun RolandMessage.Data.text(): String =
        String(data, Charsets.US_ASCII).trim()

    @Test
    fun `returns the reply whose address matches the query`() = runBlocking {
        val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)

        val reply = awaitRolandReply(messages, deviceName, timeoutMillis = 500) {
            messages.emit(nameReply(deviceName, "KATANA Mk2"))
        }

        assertNotNull(reply)
        assertEquals(deviceName, reply?.address)
        assertEquals("KATANA Mk2", reply?.text())
    }

    @Test
    fun `subscribes before sending, so an immediate reply is not missed`() = runBlocking {
        // The send lambda answers synchronously — the tightest possible race. If the
        // collector only subscribed after send(), this would time out.
        val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)

        val reply = awaitRolandReply(messages, presetOne, timeoutMillis = 500) {
            messages.emit(nameReply(presetOne, "Clean Reverb"))
        }

        assertEquals("Clean Reverb", reply?.text())
    }

    @Test
    fun `skips replies for other addresses`() = runBlocking {
        val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)

        val reply = awaitRolandReply(messages, presetOne, timeoutMillis = 500) {
            // An out-of-order answer to a previous query arrives first.
            messages.emit(nameReply(deviceName, "KATANA Mk2"))
            messages.emit(nameReply(presetOne, "Clean Reverb"))
        }

        assertEquals(presetOne, reply?.address)
        assertEquals("Clean Reverb", reply?.text())
    }

    @Test
    fun `ignores messages that are not valid Roland replies`() = runBlocking {
        val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)

        val reply = awaitRolandReply(messages, deviceName, timeoutMillis = 500) {
            // An Identity Reply: universal SysEx, no Roland header.
            messages.emit(
                byteArrayOf(0xF0.toByte(), 0x7E, 0x00, 0x06, 0x02, 0x41, 0x33, 0xF7.toByte())
            )
            // A Roland message with a broken checksum.
            val corrupted = nameReply(deviceName, "nope").copyOf()
            corrupted[corrupted.size - 2] = 0x00
            messages.emit(corrupted)

            messages.emit(nameReply(deviceName, "KATANA Mk2"))
        }

        assertEquals("KATANA Mk2", reply?.text())
    }

    @Test
    fun `returns null when nothing answers, without throwing`() = runBlocking {
        val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)

        val reply = awaitRolandReply(messages, deviceName, timeoutMillis = 100) {
            // Silence: the amp did not answer this address.
        }

        assertNull(reply)
    }

    @Test
    fun `a timeout on one address does not stop the next query`() = runBlocking {
        val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)

        val first = awaitRolandReply(messages, deviceName, timeoutMillis = 100) { }
        val second = awaitRolandReply(messages, presetOne, timeoutMillis = 500) {
            messages.emit(nameReply(presetOne, "Clean Reverb"))
        }

        assertNull(first)
        assertEquals("Clean Reverb", second?.text())
    }

    // --- sendAndCollect: writes are not acknowledged, so this only observes ---------------

    @Test
    fun `collects everything that arrives during the window`() = runBlocking {
        val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)

        val seen = sendAndCollect(messages, windowMillis = 150) {
            messages.emit(nameReply(deviceName, "uno"))
            messages.emit(nameReply(presetOne, "dos"))
        }

        assertEquals(2, seen.size)
    }

    @Test
    fun `returns empty when the write is not acknowledged`() = runBlocking {
        val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)

        // What a Roland write is expected to do: nothing comes back.
        val seen = sendAndCollect(messages, windowMillis = 100) { }

        assertEquals(emptyList<ByteArray>(), seen)
    }

    @Test
    fun `watches from before the write, so an immediate message is not missed`() = runBlocking {
        val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)

        val seen = sendAndCollect(messages, windowMillis = 150) {
            messages.emit(nameReply(deviceName, "inmediato"))
        }

        assertEquals(1, seen.size)
    }
}
