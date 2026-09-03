package dev.alonx3.ktnacontrol.device

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.RolandSysEx
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repository talks to [KatanaLink], never to `usb/`, so it can be driven here with a
 * fake — no device, no `UsbManager` (CLAUDE.md §6).
 *
 * The debounce tests run on real time with a short interval, which keeps them fast enough
 * without pulling in `kotlinx-coroutines-test` for a handful of cases.
 */
class KatanaRepositoryTest {

    /** Debounce used in tests: long enough to be observable, short enough to stay fast. */
    private val debounce = 50L

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

        fun hex(index: Int = 0) =
            sent[index].joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    }

    private fun repository(link: KatanaLink, scope: kotlinx.coroutines.CoroutineScope) =
        KatanaRepository(link, scope, debounceMillis = debounce)

    private fun levelReply(value: Int, address: Address = KatanaAddresses.REVERB_LEVEL) =
        RolandSysEx.set(address, byteArrayOf(value.toByte()))

    @Test
    fun `starts with no cached value`() = runBlocking {
        val repo = repository(FakeLink(), this)

        assertNull(repo.reverbLevel.state.value)
        repo.close()
    }

    @Test
    fun `reading caches the value the amp answers`() = runBlocking {
        val link = FakeLink(answer = { levelReply(42) })
        val repo = repository(link, this)

        assertEquals(42, repo.reverbLevel.read())
        assertEquals(42, repo.reverbLevel.state.value)
        assertEquals("F0 41 00 00 00 00 33 11 60 00 06 5B 00 00 00 01 3E F7", link.hex())
        repo.close()
    }

    @Test
    fun `reading returns null and leaves the cache alone when nothing answers`() = runBlocking {
        val repo = repository(FakeLink(), this)

        assertNull(repo.reverbLevel.read())
        assertNull(repo.reverbLevel.state.value)
        repo.close()
    }

    @Test
    fun `writing updates the cache immediately and sends after the debounce`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.reverbLevel.set(70)

        // Optimistic: the cache is up to date on return, without waiting for anything.
        assertEquals(70, repo.reverbLevel.state.value)
        yield()
        assertTrue("no debe enviar antes del debounce", link.sent.isEmpty())

        delay(debounce * 3)
        assertEquals("F0 41 00 00 00 00 33 12 60 00 06 5B 46 79 F7", link.hex())
        repo.close()
    }

    @Test
    fun `dragging fast sends only the last value, once`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        // What a slider drag looks like: many writes in quick succession.
        for (value in 1..20) {
            repo.reverbLevel.set(value)
            delay(debounce / 5)
        }
        delay(debounce * 3)

        assertEquals("un solo SET, no 20", 1, link.sent.size)
        assertEquals(20, repo.reverbLevel.state.value)
        // 20 = 0x14, and the cache never lagged behind the finger.
        assertEquals("F0 41 00 00 00 00 33 12 60 00 06 5B 14 2B F7", link.hex())
        repo.close()
    }

    @Test
    fun `separate gestures each send their own value`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.reverbLevel.set(10)
        delay(debounce * 3)
        repo.reverbLevel.set(90)
        delay(debounce * 3)

        assertEquals(2, link.sent.size)
        repo.close()
    }

    @Test
    fun `writing clamps to the valid range`() = runBlocking {
        val repo = repository(FakeLink(), this)

        repo.reverbLevel.set(500)
        assertEquals(100, repo.reverbLevel.state.value)

        repo.reverbLevel.set(-7)
        assertEquals(0, repo.reverbLevel.state.value)
        repo.close()
    }

    @Test
    fun `a spontaneous message from the amp updates the cache`() = runBlocking {
        // This is the front-panel knob case, which only happens with edit mode on.
        val link = FakeLink()
        val repo = repository(link, this)
        yield() // let the listener subscribe

        link.receive(levelReply(33))
        yield()

        assertEquals(33, repo.reverbLevel.state.value)
        repo.close()
    }

    @Test
    fun `an incoming message is never echoed back to the amp`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        link.receive(levelReply(33))
        delay(debounce * 3)

        assertEquals("no debe reenviar lo que llega", emptyList<ByteArray>(), link.sent)
        repo.close()
    }

    @Test
    fun `messages for other addresses are ignored`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        link.receive(levelReply(55, KatanaAddresses.EFFECT_COLOR_MK1[4]))
        link.receive(levelReply(55, KatanaAddresses.DEVICE_NAME))
        yield()

        assertNull(repo.reverbLevel.state.value)
        repo.close()
    }

    // --- Cableado de cada parámetro -----------------------------------------------------
    //
    // Estos tests comprueban que cada parámetro use **su propia** dirección y no se pise con
    // los demás. No dicen nada sobre si esas direcciones son las correctas del amplificador:
    // eso solo lo dice el oído (CLAUDE.md §5), y los once niveles del panel ya pasaron esa
    // prueba.

    @Test
    fun `presence writes to its own address`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.presenceLevel.set(42)
        delay(debounce * 3)

        assertEquals("F0 41 00 00 00 00 33 12 60 00 06 56 2A 1A F7", link.hex())
        repo.close()
    }

    @Test
    fun `reading presence queries its own address`() = runBlocking {
        val link = FakeLink(answer = { levelReply(77, KatanaAddresses.PRESENCE_LEVEL) })
        val repo = repository(link, this)

        assertEquals(77, repo.presenceLevel.read())
        assertEquals("F0 41 00 00 00 00 33 11 60 00 06 56 00 00 00 01 43 F7", link.hex())
        repo.close()
    }

    @Test
    fun `boost writes to its own address`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.boostLevel.set(42)
        delay(debounce * 3)

        assertEquals("F0 41 00 00 00 00 33 12 60 00 06 57 2A 19 F7", link.hex())
        repo.close()
    }

    @Test
    fun `reading boost queries its own address`() = runBlocking {
        val link = FakeLink(answer = { levelReply(55, KatanaAddresses.BOOST_LEVEL) })
        val repo = repository(link, this)

        assertEquals(55, repo.boostLevel.read())
        assertEquals("F0 41 00 00 00 00 33 11 60 00 06 57 00 00 00 01 42 F7", link.hex())
        repo.close()
    }

    @Test
    fun `mod writes to its own address`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.modLevel.set(42)
        delay(debounce * 3)

        assertEquals("F0 41 00 00 00 00 33 12 60 00 06 58 2A 18 F7", link.hex())
        repo.close()
    }

    @Test
    fun `reading mod queries its own address`() = runBlocking {
        val link = FakeLink(answer = { levelReply(64, KatanaAddresses.MOD_LEVEL) })
        val repo = repository(link, this)

        assertEquals(64, repo.modLevel.read())
        assertEquals("F0 41 00 00 00 00 33 11 60 00 06 58 00 00 00 01 41 F7", link.hex())
        repo.close()
    }

    @Test
    fun `every level writes to its own address`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        // Un SET por parámetro, esperando el debounce entre medias para que no se cancelen
        // entre sí. Las seis direcciones son contiguas salvo reverb, así que un error de un
        // byte al añadir la séptima caería aquí.
        // En el orden del panel, que es el mismo del bloque `60 00 06 51`..`06 5B`.
        val parameters = listOf(
            repo.gainLevel, repo.volumeLevel, repo.bassLevel, repo.middleLevel,
            repo.trebleLevel, repo.presenceLevel, repo.boostLevel, repo.modLevel,
            repo.fxLevel, repo.delayLevel, repo.reverbLevel,
        )
        parameters.forEach { parameter ->
            parameter.set(42)
            delay(debounce * 3)
        }

        assertEquals(
            listOf(
                "F0 41 00 00 00 00 33 12 60 00 06 51 2A 1F F7",
                "F0 41 00 00 00 00 33 12 60 00 06 52 2A 1E F7",
                "F0 41 00 00 00 00 33 12 60 00 06 53 2A 1D F7",
                "F0 41 00 00 00 00 33 12 60 00 06 54 2A 1C F7",
                "F0 41 00 00 00 00 33 12 60 00 06 55 2A 1B F7",
                "F0 41 00 00 00 00 33 12 60 00 06 56 2A 1A F7",
                "F0 41 00 00 00 00 33 12 60 00 06 57 2A 19 F7",
                "F0 41 00 00 00 00 33 12 60 00 06 58 2A 18 F7",
                "F0 41 00 00 00 00 33 12 60 00 06 59 2A 17 F7",
                "F0 41 00 00 00 00 33 12 60 00 06 5A 2A 16 F7",
                "F0 41 00 00 00 00 33 12 60 00 06 5B 2A 15 F7",
            ),
            List(link.sent.size) { index -> link.hex(index) },
        )
        repo.close()
    }

    @Test
    fun `reading gain queries its own address`() = runBlocking {
        val link = FakeLink(answer = { levelReply(63, KatanaAddresses.GAIN_LEVEL) })
        val repo = repository(link, this)

        assertEquals(63, repo.gainLevel.read())
        assertEquals("F0 41 00 00 00 00 33 11 60 00 06 51 00 00 00 01 48 F7", link.hex())
        repo.close()
    }

    @Test
    fun `the amp and EQ knobs do not take each other's messages`() = runBlocking {
        // Las cinco son direcciones consecutivas (`06 51`..`06 55`) y desembocan en Presence
        // (`06 56`), así que un error de un byte al añadirlas caería justo aquí.
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        val expected = listOf(
            KatanaAddresses.GAIN_LEVEL to repo.gainLevel,
            KatanaAddresses.VOLUME_LEVEL to repo.volumeLevel,
            KatanaAddresses.BASS_LEVEL to repo.bassLevel,
            KatanaAddresses.MIDDLE_LEVEL to repo.middleLevel,
            KatanaAddresses.TREBLE_LEVEL to repo.trebleLevel,
        )
        expected.forEachIndexed { index, (address, _) ->
            link.receive(levelReply(index * 10, address))
        }
        yield()

        expected.forEachIndexed { index, (_, parameter) ->
            assertEquals(index * 10, parameter.state.value)
        }
        assertNull("presence es contigua a treble y no debe moverse", repo.presenceLevel.state.value)
        repo.close()
    }

    @Test
    fun `reading delay queries its own address`() = runBlocking {
        val link = FakeLink(answer = { levelReply(21, KatanaAddresses.DELAY_LEVEL) })
        val repo = repository(link, this)

        assertEquals(21, repo.delayLevel.read())
        assertEquals("F0 41 00 00 00 00 33 11 60 00 06 5A 00 00 00 01 3F F7", link.hex())
        repo.close()
    }

    @Test
    fun `each parameter only takes the messages for its own address`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        link.receive(levelReply(33, KatanaAddresses.REVERB_LEVEL))
        yield()
        assertEquals(33, repo.reverbLevel.state.value)
        assertNull("presence no debe moverse con un mensaje de reverb", repo.presenceLevel.state.value)
        assertNull("boost no debe moverse con un mensaje de reverb", repo.boostLevel.state.value)

        link.receive(levelReply(70, KatanaAddresses.PRESENCE_LEVEL))
        yield()
        assertEquals(70, repo.presenceLevel.state.value)
        assertEquals("reverb no debe moverse con un mensaje de presence", 33, repo.reverbLevel.state.value)
        assertNull("boost no debe moverse con un mensaje de presence", repo.boostLevel.state.value)

        // El botón de color de boost vive en otra dirección: no debe tocar el nivel.
        link.receive(levelReply(2, KatanaAddresses.BOOST_COLOR))
        yield()
        assertNull("el botón de color no es el nivel de boost", repo.boostLevel.state.value)

        link.receive(levelReply(88, KatanaAddresses.BOOST_LEVEL))
        yield()
        assertEquals(88, repo.boostLevel.state.value)
        assertEquals("presence no debe moverse con un mensaje de boost", 70, repo.presenceLevel.state.value)
        assertNull("mod no debe moverse con un mensaje de boost", repo.modLevel.state.value)

        // Boost y Mod son direcciones contiguas (06 57 / 06 58): justo el caso en que un error
        // de un byte pasaría desapercibido.
        link.receive(levelReply(12, KatanaAddresses.MOD_LEVEL))
        yield()
        assertEquals(12, repo.modLevel.state.value)
        assertEquals("boost no debe moverse con un mensaje de mod", 88, repo.boostLevel.state.value)

        link.receive(levelReply(1, KatanaAddresses.MOD_COLOR))
        yield()
        assertEquals("el botón de color no es el nivel de mod", 12, repo.modLevel.state.value)

        link.receive(levelReply(5, KatanaAddresses.FX_LEVEL))
        link.receive(levelReply(99, KatanaAddresses.DELAY_LEVEL))
        yield()
        assertEquals(5, repo.fxLevel.state.value)
        assertEquals(99, repo.delayLevel.state.value)
        assertEquals("mod no debe moverse con mensajes de fx o delay", 12, repo.modLevel.state.value)

        link.receive(levelReply(2, KatanaAddresses.FX_COLOR))
        link.receive(levelReply(2, KatanaAddresses.DELAY_COLOR))
        yield()
        assertEquals("los botones de color no son niveles", 5, repo.fxLevel.state.value)
        assertEquals("los botones de color no son niveles", 99, repo.delayLevel.state.value)
        repo.close()
    }

    @Test
    fun `closing stops listening and drops a pending write`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        repo.reverbLevel.set(88)
        repo.close()
        delay(debounce * 3)

        assertTrue("el envío pendiente debe cancelarse", link.sent.isEmpty())

        link.receive(levelReply(12))
        yield()
        assertEquals("ya no debe escuchar", 88, repo.reverbLevel.state.value)
    }
}
