package dev.alonx3.ktnacontrol.device

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.RolandSysEx
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The repository talks to [KatanaLink], never to `usb/`, so it can be driven here with a
 * fake — no device, no `UsbManager` (CLAUDE.md §6).
 */
class KatanaRepositoryTest {

    /**
     * A link that records what was sent and lets the test push messages back, standing in
     * for the amp.
     */
    private class FakeLink(
        /** Optional canned answer, keyed on the message just sent. */
        private val answer: (ByteArray) -> ByteArray? = { null },
    ) : KatanaLink {
        val sent = mutableListOf<ByteArray>()
        private val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        override val incoming: Flow<ByteArray> = messages

        override suspend fun send(message: ByteArray): Boolean {
            sent += message
            answer(message)?.let { reply -> messages.emit(reply) }
            return true
        }

        suspend fun receive(message: ByteArray) = messages.emit(message)
    }

    private fun levelReply(value: Int, address: Address = KatanaAddresses.REVERB_LEVEL) =
        RolandSysEx.set(address, byteArrayOf(value.toByte()))

    @Test
    fun `starts with no cached value`() = runBlocking {
        val repository = KatanaRepository(FakeLink(), this)

        assertNull(repository.reverbLevel.value)
        repository.close()
    }

    @Test
    fun `reading caches the value the amp answers`() = runBlocking {
        val link = FakeLink(answer = { levelReply(42) })
        val repository = KatanaRepository(link, this)

        assertEquals(42, repository.readReverbLevel())
        assertEquals(42, repository.reverbLevel.value)

        // And it asked with a proper GET for the reverb level address.
        assertEquals(
            "F0 41 00 00 00 00 33 11 60 00 06 5B 00 00 00 01 3E F7",
            link.sent.single().joinToString(" ") { "%02X".format(it.toInt() and 0xFF) },
        )
        repository.close()
    }

    @Test
    fun `reading returns null and leaves the cache alone when nothing answers`() = runBlocking {
        val repository = KatanaRepository(FakeLink(), this)

        assertNull(repository.readReverbLevel())
        assertNull(repository.reverbLevel.value)
        repository.close()
    }

    @Test
    fun `writing updates the cache before the message goes out`() = runBlocking {
        val link = FakeLink()
        val repository = KatanaRepository(link, this)

        repository.setReverbLevel(70)

        // Optimistic: the cache is already up to date on return, without waiting for the amp.
        assertEquals(70, repository.reverbLevel.value)

        yield() // let the send coroutine run
        assertEquals(
            "F0 41 00 00 00 00 33 12 60 00 06 5B 46 79 F7",
            link.sent.single().joinToString(" ") { "%02X".format(it.toInt() and 0xFF) },
        )
        repository.close()
    }

    @Test
    fun `writing clamps to the valid range`() = runBlocking {
        val repository = KatanaRepository(FakeLink(), this)

        repository.setReverbLevel(500)
        assertEquals(100, repository.reverbLevel.value)

        repository.setReverbLevel(-7)
        assertEquals(0, repository.reverbLevel.value)
        repository.close()
    }

    @Test
    fun `a spontaneous message from the amp updates the cache`() = runBlocking {
        // This is the front-panel knob case, which only happens with edit mode on.
        val link = FakeLink()
        val repository = KatanaRepository(link, this)
        yield() // let the listener subscribe

        link.receive(levelReply(33))
        yield()

        assertEquals(33, repository.reverbLevel.value)
        repository.close()
    }

    @Test
    fun `an incoming message is never echoed back to the amp`() = runBlocking {
        val link = FakeLink()
        val repository = KatanaRepository(link, this)
        yield()

        link.receive(levelReply(33))
        yield()

        assertEquals("no debe reenviar lo que llega", emptyList<ByteArray>(), link.sent)
        repository.close()
    }

    @Test
    fun `messages for other addresses are ignored`() = runBlocking {
        val link = FakeLink()
        val repository = KatanaRepository(link, this)
        yield()

        link.receive(levelReply(55, KatanaAddresses.REVERB_ACTIVE_COLOR))
        link.receive(levelReply(55, KatanaAddresses.DEVICE_NAME))
        yield()

        assertNull(repository.reverbLevel.value)
        repository.close()
    }

    @Test
    fun `stops listening after close`() = runBlocking {
        val link = FakeLink()
        val repository = KatanaRepository(link, this)
        yield()
        repository.close()
        yield()

        link.receive(levelReply(88))
        yield()

        assertNull(repository.reverbLevel.value)
    }
}
