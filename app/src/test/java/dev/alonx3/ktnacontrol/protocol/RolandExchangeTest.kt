package dev.alonx3.ktnacontrol.protocol

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /** A reply whose payload is [size] bytes, the way a dump chunk looks on the wire. */
    private fun chunkReply(address: Address, size: Int): ByteArray =
        RolandSysEx.set(address, ByteArray(size))

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

    // --- sendAndCollectUntilQuiet ------------------------------------------------------

    /** Una dirección alta cualquiera, fuera del rango del "dump" que usan estos tests. */
    private val chunkBase = Address(0x60, 0x00, 0x00, 0x00)

    @Test
    fun `collecting stops as soon as the amp goes quiet`() = runBlocking {
        val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val started = System.currentTimeMillis()

        val collected = sendAndCollectUntilQuiet(
            messages = messages,
            quietMillis = 150L,
            timeoutMillis = 5_000L,
        ) {
            // Tres mensajes seguidos, como los trozos del dump.
            repeat(3) { index ->
                messages.emit(chunkReply(chunkBase + index * 10, size = 10))
                delay(20)
            }
        }
        val elapsed = System.currentTimeMillis() - started

        assertEquals(3, collected.accepted.size)
        assertTrue("debe cortar por silencio, no agotar los 5 s (tardó $elapsed ms)", elapsed < 2_000)
    }

    @Test
    fun `collecting gives up on the timeout when nothing ever arrives`() = runBlocking {
        val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 4)

        val collected = sendAndCollectUntilQuiet(
            messages = messages,
            quietMillis = 100L,
            timeoutMillis = 200L,
        ) { /* no se envía nada */ }

        assertTrue("sin respuesta debe devolver vacío, no colgarse", collected.accepted.isEmpty())
        assertTrue(collected.rejected.isEmpty())
        assertEquals(0, collected.invalidCount)
    }

    @Test
    fun `a late message still counts if it arrives before the quiet window closes`() = runBlocking {
        val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)

        val collected = sendAndCollectUntilQuiet(
            messages = messages,
            quietMillis = 300L,
            timeoutMillis = 5_000L,
        ) {
            messages.emit(chunkReply(chunkBase, size = 10))
            delay(150) // menos que el silencio: la recogida sigue abierta
            messages.emit(chunkReply(chunkBase + 10, size = 10))
        }

        assertEquals(2, collected.accepted.size)
    }

    @Test
    fun `a message the predicate rejects is reported, not silently dropped`() = runBlocking {
        val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
        val outsider = Address(0x00, 0x01, 0x00, 0x00) // el canal activo, fuera del "dump"

        val collected = sendAndCollectUntilQuiet(
            messages = messages,
            accept = blockReplyIn(chunkBase, size = 100),
            quietMillis = 150L,
            timeoutMillis = 5_000L,
        ) {
            messages.emit(chunkReply(chunkBase, size = 10))
            messages.emit(RolandSysEx.set(outsider, byteArrayOf(0x03))) // reporte espontáneo
        }

        assertEquals(1, collected.accepted.size)
        assertEquals(listOf(outsider), collected.rejected)
    }

    @Test
    fun `a rejected message does not reset the quiet counter`() = runBlocking {
        val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        val outsider = Address(0x00, 0x01, 0x00, 0x00)
        val started = System.currentTimeMillis()

        // Antes de este filtro, cualquier mensaje reiniciaba el contador de silencio y la
        // ventana se estiraba hasta el tope absoluto. Aquí el tope es deliberadamente enorme
        // para que la aserción de tiempo demuestre que el rechazo no lo activó.
        val collected = sendAndCollectUntilQuiet(
            messages = messages,
            accept = blockReplyIn(chunkBase, size = 100),
            quietMillis = 150L,
            timeoutMillis = 10_000L,
        ) {
            messages.emit(chunkReply(chunkBase, size = 10))
            delay(50)
            // Un intruso llega bien entrado el silencio; no debe reabrir la ventana.
            repeat(4) {
                delay(60)
                messages.emit(RolandSysEx.set(outsider, byteArrayOf(0x03)))
            }
        }
        val elapsed = System.currentTimeMillis() - started

        assertEquals(1, collected.accepted.size)
        assertEquals(4, collected.rejected.size)
        assertTrue(
            "el rechazo no debe reiniciar el silencio (tardó $elapsed ms)",
            elapsed < 5_000,
        )
    }

    @Test
    fun `messages that cannot even be parsed are counted separately`() = runBlocking {
        val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)

        val collected = sendAndCollectUntilQuiet(
            messages = messages,
            quietMillis = 100L,
            timeoutMillis = 5_000L,
        ) {
            messages.emit(chunkReply(chunkBase, size = 10))
            messages.emit(byteArrayOf(0xF0.toByte(), 0x00, 0xF7.toByte())) // demasiado corto
        }

        assertEquals(1, collected.accepted.size)
        assertTrue(collected.rejected.isEmpty())
        assertEquals(1, collected.invalidCount)
    }

    // --- blockReplyIn -------------------------------------------------------------------

    @Test
    fun `blockReplyIn accepts only addresses inside the range with a block-sized payload`() {
        val accept = blockReplyIn(chunkBase, size = 100)

        val inRangeBlock = RolandMessage.Data(chunkBase + 50, ByteArray(10))
        val inRangeControlReport = RolandMessage.Data(chunkBase + 50, byteArrayOf(0x01))
        val outsideRange = RolandMessage.Data(chunkBase + 200, ByteArray(10))
        val atTheEdge = RolandMessage.Data(chunkBase + 99, ByteArray(1))
        val justPastTheEdge = RolandMessage.Data(chunkBase + 100, ByteArray(10))

        assertTrue("dentro del rango y de sobra para ser un trozo", accept(inRangeBlock))
        assertFalse(
            "dentro del rango pero es un reporte de 1 byte, no un trozo",
            accept(inRangeControlReport),
        )
        assertFalse("fuera del rango pedido", accept(outsideRange))
        assertFalse(
            "dentro del rango pero de 1 byte: sigue siendo un reporte, no un trozo",
            accept(atTheEdge),
        )
        assertFalse("justo fuera del rango pedido", accept(justPastTheEdge))
    }

    @Test
    fun `blockReplyIn rejects a 2-byte control report even though it is inside the range`() {
        // El canal activo tiene 2 bytes (CLAUDE.md §5.1); si algún día una dirección de
        // 2 bytes cayera dentro de un rango de dump, MAX_CONTROL_PAYLOAD debe seguir
        // distinguiéndola de un trozo real.
        val accept = blockReplyIn(chunkBase, size = 100)
        val twoByteReport = RolandMessage.Data(chunkBase + 10, byteArrayOf(0x01, 0x02))

        assertFalse(accept(twoByteReport))
    }
}
