package dev.alonx3.ktnacontrol.device

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.AmpCategory
import dev.alonx3.ktnacontrol.protocol.AmpType
import dev.alonx3.ktnacontrol.protocol.BoostType
import dev.alonx3.ktnacontrol.protocol.ChainPreset
import dev.alonx3.ktnacontrol.protocol.DelayType
import dev.alonx3.ktnacontrol.protocol.EffectColor
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.MidiBytes
import dev.alonx3.ktnacontrol.protocol.ModFxType
import dev.alonx3.ktnacontrol.protocol.ReverbType
import dev.alonx3.ktnacontrol.protocol.RolandSysEx
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Same idea as [levelReply], but 2 bytes — what [KatanaRepository.channel] expects. */
    private fun channelReply(value: Int, address: Address = KatanaAddresses.ACTIVE_CHANNEL) =
        RolandSysEx.set(address, MidiBytes.encode(value, 2))

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

        // What a slider drag looks like: many writes in quick succession. Usa `set`, o sea
        // valores crudos, porque lo que se comprueba aquí es el debounce y no la escala.
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

        // Reverb es un nivel de efecto: su rango crudo es 1..101, no 0..100.
        repo.reverbLevel.set(500)
        assertEquals(101, repo.reverbLevel.state.value)
        repo.reverbLevel.set(-7)
        assertEquals("no baja de 1: el 0 es Off, no el mínimo", 1, repo.reverbLevel.state.value)

        // Una perilla de amp sí llega a 0 crudo, porque ahí 0 es un valor normal.
        repo.gainLevel.set(-7)
        assertEquals(0, repo.gainLevel.state.value)
        repo.gainLevel.set(500)
        assertEquals(100, repo.gainLevel.state.value)
        repo.close()
    }

    @Test
    fun `setLevel clamps in display terms before converting`() = runBlocking {
        val repo = repository(FakeLink(), this)

        repo.reverbLevel.setLevel(500)
        assertEquals("crudo", 101, repo.reverbLevel.state.value)
        assertEquals("mostrado", 100, repo.reverbLevel.displayValue)

        repo.reverbLevel.setLevel(-7)
        assertEquals("crudo", 1, repo.reverbLevel.state.value)
        assertEquals("mostrado", 0, repo.reverbLevel.displayValue)
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
    fun `setLevel offsets the effect knobs and leaves the amp knobs alone`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.gainLevel.setLevel(0)
        delay(debounce * 3)
        repo.boostLevel.setLevel(0)
        delay(debounce * 3)
        repo.boostLevel.setLevel(100)
        delay(debounce * 3)
        repo.gainLevel.setLevel(100)
        delay(debounce * 3)

        assertEquals(
            listOf(
                "F0 41 00 00 00 00 33 12 60 00 06 51 00 49 F7", // gain 0 -> crudo 0
                "F0 41 00 00 00 00 33 12 60 00 06 57 01 42 F7", // boost 0 -> crudo 1
                "F0 41 00 00 00 00 33 12 60 00 06 57 65 5E F7", // boost 100 -> crudo 101
                "F0 41 00 00 00 00 33 12 60 00 06 51 64 65 F7", // gain 100 -> crudo 100
            ),
            List(link.sent.size) { index -> link.hex(index) },
        )
        repo.close()
    }

    @Test
    fun `an effect level shows one less than the byte the amp reports`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        link.receive(levelReply(43, KatanaAddresses.BOOST_LEVEL))
        link.receive(levelReply(43, KatanaAddresses.GAIN_LEVEL))
        yield()

        assertEquals("crudo", 43, repo.boostLevel.state.value)
        assertEquals("mostrado", 42, repo.boostLevel.displayValue)
        assertEquals("una perilla de amp no se desplaza", 43, repo.gainLevel.displayValue)
        repo.close()
    }

    @Test
    fun `an effect reporting off is kept, not clamped up to the minimum`() = runBlocking {
        // Crudo 0 queda fuera del rango 1..101 a propósito. Clampearlo a 1 convertiría un
        // "apagado" en "al mínimo", que no es lo mismo.
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        link.receive(levelReply(0, KatanaAddresses.BOOST_LEVEL))
        yield()

        assertEquals("crudo se conserva", 0, repo.boostLevel.state.value)
        assertEquals("se muestra como el mínimo", 0, repo.boostLevel.displayValue)
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

    // --- Selectores (enum) -----------------------------------------------------------------
    //
    // Comprueban el **contrato** de KatanaEnumParameter y los bytes que produce. Ninguna de
    // estas direcciones está confirmada contra el amplificador (CLAUDE.md §5): que el SET esté
    // bien formado no dice nada sobre si el amp lo obedece.

    @Test
    fun `a selector writes the raw value of the chosen option`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.ampCategory.set(AmpCategory.CRUNCH.value)
        yield()

        assertEquals("F0 41 00 00 00 00 33 12 60 00 06 50 02 48 F7", link.hex())
        assertEquals(AmpCategory.CRUNCH.value, repo.ampCategory.state.value)
        repo.close()
    }

    @Test
    fun `every selector family builds the SysEx its address expects`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.ampType.set(AmpType.MS_1959_I_II.value)
        repo.boostColor.set(EffectColor.RED.value)
        repo.reverbColor.set(EffectColor.YELLOW.value)
        repo.reverbEnabled.set(KatanaAddresses.SWITCH_ON)
        repo.modEnabled.set(KatanaAddresses.SWITCH_OFF)
        repo.ampVariation.set(KatanaAddresses.SWITCH_ON)
        yield()

        assertEquals(
            listOf(
                "F0 41 00 00 00 00 33 12 60 00 00 21 13 6C F7",
                "F0 41 00 00 00 00 33 12 60 00 06 39 01 60 F7",
                "F0 41 00 00 00 00 33 12 60 00 06 3D 02 5B F7",
                "F0 41 00 00 00 00 33 12 60 00 05 40 01 5A F7",
                "F0 41 00 00 00 00 33 12 60 00 01 00 00 1F F7",
                "F0 41 00 00 00 00 33 12 60 00 06 5C 01 3D F7",
            ),
            List(link.sent.size) { index -> link.hex(index) },
        )
        repo.close()
    }

    @Test
    fun `a selector sends without waiting for a debounce`() = runBlocking {
        // Un toque no es un arrastre: no hay nada que coalescer y esperar solo daría lag.
        val link = FakeLink()
        val repo = repository(link, this)

        repo.boostColor.set(EffectColor.YELLOW.value)
        yield()

        assertEquals("debe salir ya, sin esperar el debounce", 1, link.sent.size)
        repo.close()
    }

    @Test
    fun `a selector rejects a value that is not one of its options`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.boostColor.set(EffectColor.GREEN.value)
        yield()
        repo.boostColor.set(7) // no existe un cuarto color
        delay(debounce * 3)

        assertEquals("no debe enviar un valor ilegal", 1, link.sent.size)
        assertEquals("la caché se queda como estaba", EffectColor.GREEN.value, repo.boostColor.state.value)
        repo.close()
    }

    @Test
    fun `a selector does not clamp, because its values have gaps`() = runBlocking {
        // 0x09 (Clean Twin) y 0x0B (Crunch) existen; 0x0A también. Pero 0x19 no está en la
        // tabla, y "el más cercano" no significaría nada: se rechaza en vez de adivinar.
        val link = FakeLink()
        val repo = repository(link, this)

        repo.ampType.set(0x19)
        delay(debounce * 3)

        assertTrue("no debe enviar nada", link.sent.isEmpty())
        assertNull(repo.ampType.state.value)
        repo.close()
    }

    @Test
    fun `reading a selector queries its own address`() = runBlocking {
        val link = FakeLink(answer = { levelReply(0x02, KatanaAddresses.AMP_TYPE_PANEL) })
        val repo = repository(link, this)

        assertEquals(0x02, repo.ampCategory.read())
        assertEquals(
            "F0 41 00 00 00 00 33 11 60 00 06 50 00 00 00 01 49 F7",
            link.hex(),
        )
        repo.close()
    }

    @Test
    fun `an incoming value outside the table leaves the selector alone`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        link.receive(levelReply(EffectColor.RED.value, KatanaAddresses.MOD_COLOR))
        yield()
        assertEquals(EffectColor.RED.value, repo.modColor.state.value)

        link.receive(levelReply(9, KatanaAddresses.MOD_COLOR))
        yield()
        assertEquals(
            "un valor desconocido no debe pisar la caché",
            EffectColor.RED.value,
            repo.modColor.state.value,
        )
        repo.close()
    }

    @Test
    fun `selectors and levels do not take each other's messages`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        // 60 00 06 39 (color de boost) y 60 00 06 57 (nivel de boost) son del mismo efecto
        // pero direcciones distintas; y 06 50 (amp type) es contigua a 06 51 (gain).
        link.receive(levelReply(EffectColor.YELLOW.value, KatanaAddresses.BOOST_COLOR))
        link.receive(levelReply(77, KatanaAddresses.BOOST_LEVEL))
        link.receive(levelReply(AmpCategory.LEAD.value, KatanaAddresses.AMP_TYPE_PANEL))
        link.receive(levelReply(33, KatanaAddresses.GAIN_LEVEL))
        yield()

        assertEquals(EffectColor.YELLOW.value, repo.boostColor.state.value)
        assertEquals(77, repo.boostLevel.state.value)
        assertEquals(AmpCategory.LEAD.value, repo.ampCategory.state.value)
        assertEquals(33, repo.gainLevel.state.value)
        repo.close()
    }

    @Test
    fun `changing the variation writes the model, not the variation flag`() = runBlocking {
        // `60 00 06 5C` solo reporta, así que la variación se pide por el modelo. El
        // ViewModel decide qué valor; aquí se comprueba que ese valor produzca los bytes que
        // toca en la dirección del modelo.
        val link = FakeLink()
        val repo = repository(link, this)

        repo.ampType.set(AmpCategory.BROWN.typeValue(variation = true))
        yield()
        repo.ampType.set(AmpCategory.BROWN.typeValue(variation = false))
        yield()

        assertEquals(
            listOf(
                "F0 41 00 00 00 00 33 12 60 00 00 21 20 5F F7",
                "F0 41 00 00 00 00 33 12 60 00 00 21 17 68 F7",
            ),
            List(link.sent.size) { index -> link.hex(index) },
        )
        repo.close()
    }

    @Test
    fun `the variation address still reports, it just is not written`() = runBlocking {
        // Sigue registrado como control para que el botón físico actualice la UI.
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        link.receive(levelReply(KatanaAddresses.SWITCH_ON, KatanaAddresses.AMP_VARIATION))
        yield()

        assertEquals(KatanaAddresses.SWITCH_ON, repo.ampVariation.state.value)
        assertTrue("recibir no debe enviar nada", link.sent.isEmpty())
        repo.close()
    }

    @Test
    fun `an effect switch only accepts off and on`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.fxEnabled.set(2)
        delay(debounce * 3)

        assertTrue("2 no es ni off ni on", link.sent.isEmpty())
        assertNull(repo.fxEnabled.state.value)
        repo.close()
    }

    // --- Tipo de efecto (CLAUDE.md §5.2) --------------------------------------------------
    //
    // ✅ La dirección de "tipo activo" está confirmada en Booster contra el amplificador. Mod y
    // FX usan la gemela y **no** están confirmados, así que estos tests fijan los bytes, no que
    // el amp los obedezca.

    @Test
    fun `the active boost type writes to its own address`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.boostTypeActive.set(BoostType.TUBE_SCREAMER.value)
        yield()

        // 60 00 00 11 + dato 0C: suma 0x60+0x11+0x0C = 125, checksum 3.
        assertEquals("F0 41 00 00 00 00 33 12 60 00 00 11 0C 03 F7", link.hex())
        repo.close()
    }

    @Test
    fun `each colour slot writes to its own address`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.boostTypeByColor[EffectColor.GREEN.value].set(BoostType.CLEAN_BOOST.value)
        yield()
        repo.boostTypeByColor[EffectColor.RED.value].set(BoostType.DISTORTION.value)
        yield()
        repo.boostTypeByColor[EffectColor.YELLOW.value].set(BoostType.RAT.value)
        yield()

        assertEquals(
            listOf(
                "F0 41 00 00 00 00 33 12 60 00 06 24 01 75 F7",
                "F0 41 00 00 00 00 33 12 60 00 06 25 0E 67 F7",
                "F0 41 00 00 00 00 33 12 60 00 06 26 0F 65 F7",
            ),
            List(link.sent.size) { index -> link.hex(index) },
        )
        repo.close()
    }

    @Test
    fun `the boost type rejects 0x07, which does not exist`() = runBlocking {
        // Mismo caso que el 0x19 de AmpType: el hueco está en las dos fuentes de Mk2, así que
        // "el valor legal más cercano" no significaría nada. Se rechaza en vez de adivinar.
        val link = FakeLink()
        val repo = repository(link, this)

        repo.boostTypeActive.set(0x07)
        repo.boostTypeByColor[EffectColor.GREEN.value].set(0x07)
        delay(debounce * 3)

        assertTrue("0x07 no existe: no debe enviarse nada", link.sent.isEmpty())
        assertNull(repo.boostTypeActive.state.value)
        assertNull(repo.boostTypeByColor[EffectColor.GREEN.value].state.value)
        repo.close()
    }

    @Test
    fun `the four boost type addresses do not take each other's messages`() = runBlocking {
        // Es lo que hace legible el experimento: si al escribir en una se moviera otra por
        // error de cableado, el diagnóstico apuntaría a la dirección equivocada.
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        link.receive(levelReply(BoostType.MID_BOOST.value, KatanaAddresses.BOOST_TYPE_ACTIVE))
        KatanaAddresses.BOOST_TYPE_BY_COLOR.forEachIndexed { colorValue, address ->
            link.receive(levelReply(BoostType.entries[colorValue + 1].value, address))
        }
        yield()

        assertEquals(BoostType.MID_BOOST.value, repo.boostTypeActive.state.value)
        repo.boostTypeByColor.forEachIndexed { colorValue, control ->
            assertEquals(BoostType.entries[colorValue + 1].value, control.state.value)
        }
        repo.close()
    }

    @Test
    fun `a boost type message does not disturb the boost colour or level`() = runBlocking {
        // 60 00 06 24-26 (tipo por color) son vecinas de nada crítico, pero 60 00 00 11 va
        // justo detrás de 60 00 00 10 (on/off de boost): un error de un byte caería aquí.
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        link.receive(levelReply(BoostType.RAT.value, KatanaAddresses.BOOST_TYPE_ACTIVE))
        yield()

        assertEquals(BoostType.RAT.value, repo.boostTypeActive.state.value)
        assertNull("el on/off no debe moverse", repo.boostEnabled.state.value)
        assertNull("el color no debe moverse", repo.boostColor.state.value)
        assertNull("el nivel no debe moverse", repo.boostLevel.state.value)
        repo.close()
    }

    @Test
    fun `mod and fx write their own type addresses`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.modTypeActive.set(ModFxType.CHORUS.value)
        yield()
        repo.fxTypeActive.set(ModFxType.PHASER.value)
        yield()

        assertEquals(
            listOf(
                "F0 41 00 00 00 00 33 12 60 00 01 01 1D 01 F7",
                "F0 41 00 00 00 00 33 12 60 00 03 01 13 09 F7",
            ),
            List(link.sent.size) { index -> link.hex(index) },
        )
        repo.close()
    }

    @Test
    fun `mod and fx share one catalogue but not one control`() = runBlocking {
        // Comparten tabla porque son la misma lista en las dos fuentes, pero son dos efectos
        // distintos: un mensaje para uno no debe mover el otro.
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        link.receive(levelReply(ModFxType.TREMOLO.value, KatanaAddresses.MOD_TYPE_ACTIVE))
        yield()

        assertEquals(ModFxType.TREMOLO.value, repo.modTypeActive.state.value)
        assertNull("fx no debe moverse con un mensaje de mod", repo.fxTypeActive.state.value)
        assertNull("el tipo de boost tampoco", repo.boostTypeActive.state.value)
        repo.close()
    }

    @Test
    fun `mod and fx reject a value outside their shared catalogue`() = runBlocking {
        // 0x05 es uno de los diez huecos de ModFxType, y está en las dos fuentes.
        val link = FakeLink()
        val repo = repository(link, this)

        repo.modTypeActive.set(0x05)
        repo.fxTypeActive.set(0x05)
        delay(debounce * 3)

        assertTrue("0x05 no existe: no debe enviarse nada", link.sent.isEmpty())
        assertNull(repo.modTypeActive.state.value)
        assertNull(repo.fxTypeActive.state.value)
        repo.close()
    }

    @Test
    fun `every colour slot of the three effects has its own address`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        // 06 24..06 2C seguidas: boost verde/rojo/amarillo, mod, fx. Un error de un byte al
        // añadir mod o fx caería justo aquí.
        val slots = repo.boostTypeByColor + repo.modTypeByColor + repo.fxTypeByColor
        val addresses = KatanaAddresses.BOOST_TYPE_BY_COLOR +
            KatanaAddresses.MOD_TYPE_BY_COLOR + KatanaAddresses.FX_TYPE_BY_COLOR
        assertEquals(9, slots.size)
        assertEquals(addresses, slots.map { it.address })

        // Un valor legal **del catálogo de cada slot**, no el índice: 0x05 y 0x08 son huecos
        // de ModFxType y el control los rechazaría, que es justo lo que debe hacer.
        val values = slots.mapIndexed { index, control -> control.options[index] }
        assertEquals("deben ser distintos para detectar cruces", 9, values.toSet().size)
        addresses.forEachIndexed { index, address ->
            link.receive(levelReply(values[index], address))
        }
        yield()
        slots.forEachIndexed { index, control ->
            assertEquals(values[index], control.state.value)
        }
        repo.close()
    }

    @Test
    fun `delay and reverb write their own type addresses`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.delayTypeActive.set(DelayType.ANALOG.value)
        yield()
        repo.reverbTypeActive.set(ReverbType.PLATE.value)
        yield()

        assertEquals(
            listOf(
                "F0 41 00 00 00 00 33 12 60 00 05 01 07 13 F7",
                "F0 41 00 00 00 00 33 12 60 00 05 41 04 56 F7",
            ),
            List(link.sent.size) { index -> link.hex(index) },
        )
        repo.close()
    }

    @Test
    fun `delay and reverb types do not take each other's messages`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        link.receive(levelReply(DelayType.SDE_3000.value, KatanaAddresses.DELAY_TYPE_ACTIVE))
        yield()

        assertEquals(DelayType.SDE_3000.value, repo.delayTypeActive.state.value)
        assertNull("reverb no debe moverse con un mensaje de delay", repo.reverbTypeActive.state.value)
        assertNull("boost tampoco", repo.boostTypeActive.state.value)
        repo.close()
    }

    @Test
    fun `delay and reverb reject values outside their own catalogues`() = runBlocking {
        // 0x0B no existe para delay (11 tipos, 00-0A); 0x07 no existe para reverb (7 tipos,
        // 00-06). Ninguno de los dos catálogos tiene huecos internos, solo un tope.
        val link = FakeLink()
        val repo = repository(link, this)

        repo.delayTypeActive.set(0x0B)
        repo.reverbTypeActive.set(0x07)
        delay(debounce * 3)

        assertTrue("ningún valor fuera de rango debe enviarse", link.sent.isEmpty())
        assertNull(repo.delayTypeActive.state.value)
        assertNull(repo.reverbTypeActive.state.value)
        repo.close()
    }

    @Test
    fun `every colour slot of the five effects has its own address`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        // 06 24..06 32 seguidas: boost, mod, fx, delay, reverb — los cinco efectos con tipo.
        val slots = repo.boostTypeByColor + repo.modTypeByColor + repo.fxTypeByColor +
            repo.delayTypeByColor + repo.reverbTypeByColor
        val addresses = KatanaAddresses.BOOST_TYPE_BY_COLOR + KatanaAddresses.MOD_TYPE_BY_COLOR +
            KatanaAddresses.FX_TYPE_BY_COLOR + KatanaAddresses.DELAY_TYPE_BY_COLOR +
            KatanaAddresses.REVERB_TYPE_BY_COLOR
        assertEquals(15, slots.size)
        assertEquals(addresses, slots.map { it.address })

        // Un valor legal del catálogo de cada slot, no el índice: los catálogos de boost/mod/fx
        // tienen huecos y rechazarían un índice cualquiera.
        val values = slots.mapIndexed { index, control -> control.options[index % control.options.size] }
        addresses.forEachIndexed { index, address ->
            link.receive(levelReply(values[index], address))
        }
        yield()
        slots.forEachIndexed { index, control ->
            assertEquals(values[index], control.state.value)
        }
        repo.close()
    }

    // --- Parámetros internos de Booster (CLAUDE.md §5.2) -----------------------------------
    //
    // ⚠️ Implementados, pendientes de confirmar con audio (2026-09-04). Estos tests fijan los
    // bytes de cada dirección y la escala centrada de Bottom/Tone; no dicen nada sobre si el
    // amp los obedece.

    @Test
    fun `booster drive writes a direct 0 to 120 byte`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.boostDrive.setLevel(60)
        delay(debounce * 3)

        assertEquals(60, repo.boostDrive.displayValue)
        assertEquals("F0 41 00 00 00 00 33 12 60 00 00 12 3C 52 F7", link.hex())
        repo.close()
    }

    @Test
    fun `booster drive clamps to 0 to 120, unlike the 0 to 100 amp knobs`() = runBlocking {
        val repo = repository(FakeLink(), this)

        repo.boostDrive.setLevel(500)
        assertEquals(120, repo.boostDrive.state.value)
        repo.boostDrive.setLevel(-7)
        assertEquals(0, repo.boostDrive.state.value)
        repo.close()
    }

    @Test
    fun `booster bottom and tone use the centered scale`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.boostBottom.setLevel(10)
        delay(debounce * 3)
        repo.boostTone.setLevel(-50)
        delay(debounce * 3)

        assertEquals(10, repo.boostBottom.displayValue)
        assertEquals(-50, repo.boostTone.displayValue)
        assertEquals(
            listOf(
                "F0 41 00 00 00 00 33 12 60 00 00 13 3C 51 F7", // display 10 -> raw 60
                "F0 41 00 00 00 00 33 12 60 00 00 14 00 0C F7", // display -50 -> raw 0
            ),
            List(link.sent.size) { index -> link.hex(index) },
        )
        repo.close()
    }

    @Test
    fun `booster bottom and tone clamp to -50 to +50`() = runBlocking {
        val repo = repository(FakeLink(), this)

        repo.boostBottom.setLevel(500)
        assertEquals(50, repo.boostBottom.displayValue)
        repo.boostTone.setLevel(-500)
        assertEquals(-50, repo.boostTone.displayValue)
        repo.close()
    }

    @Test
    fun `booster solo switch and its three levels write their own addresses`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.boostSoloEnabled.set(KatanaAddresses.SWITCH_ON)
        yield()
        repo.boostSoloLevel.setLevel(100)
        delay(debounce * 3)
        repo.boostEffectLevel.setLevel(0)
        delay(debounce * 3)
        repo.boostDirectMix.setLevel(25)
        delay(debounce * 3)

        assertEquals(
            listOf(
                "F0 41 00 00 00 00 33 12 60 00 00 15 01 0A F7",
                "F0 41 00 00 00 00 33 12 60 00 00 16 64 26 F7",
                "F0 41 00 00 00 00 33 12 60 00 00 17 00 09 F7",
                "F0 41 00 00 00 00 33 12 60 00 00 18 19 6F F7",
            ),
            List(link.sent.size) { index -> link.hex(index) },
        )
        repo.close()
    }

    @Test
    fun `the remaining PREAMP controls write their own addresses`() = runBlocking {
        // Bright/Gain SW/Solo Sw/Solo Level: 60 00 00 29-2C, el resto del bloque PREAMP que
        // faltaba cablear (BACKLOG.md, "Cambiar el tipo de amplificador no recarga nada").
        val link = FakeLink()
        val repo = repository(link, this)

        repo.ampBright.set(KatanaAddresses.SWITCH_ON)
        yield()
        repo.ampGainSw.set(0x02)
        yield()
        repo.ampSoloEnabled.set(KatanaAddresses.SWITCH_ON)
        yield()
        repo.ampSoloLevel.setLevel(100)
        delay(debounce * 3)

        assertEquals(
            listOf(
                "F0 41 00 00 00 00 33 12 60 00 00 29 01 76 F7",
                "F0 41 00 00 00 00 33 12 60 00 00 2A 02 74 F7",
                "F0 41 00 00 00 00 33 12 60 00 00 2B 01 74 F7",
                "F0 41 00 00 00 00 33 12 60 00 00 2C 64 10 F7",
            ),
            List(link.sent.size) { index -> link.hex(index) },
        )
        repo.close()
    }

    @Test
    fun `AMP_GAIN_SW rejects a value outside its three positions`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.ampGainSw.set(0x03)
        yield()

        assertTrue("un valor fuera de Low/Middle/High no debe salir al cable", link.sent.isEmpty())
        assertNull(repo.ampGainSw.state.value)
        repo.close()
    }

    @Test
    fun `booster internal parameters do not take each other's messages, or the type or level`() = runBlocking {
        // 00 11 (tipo), 00 12-18 (internos) y 06 57 (perilla) son direcciones distintas y
        // adyacentes: un error de un byte al cablear cualquiera caería aquí.
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        link.receive(levelReply(60, KatanaAddresses.BOOST_DRIVE))
        yield()

        assertEquals(60, repo.boostDrive.state.value)
        assertNull("bottom no debe moverse", repo.boostBottom.state.value)
        assertNull("tone no debe moverse", repo.boostTone.state.value)
        assertNull("tipo no debe moverse", repo.boostTypeActive.state.value)
        assertNull("la perilla del panel no debe moverse", repo.boostLevel.state.value)
        repo.close()
    }

    // --- Canal activo: el único control de 2 bytes -----------------------------------------
    //
    // ⚠️ Sin confirmar contra el amplificador (CLAUDE.md §5.1). Estos tests comprueban el
    // contrato de un `KatanaEnumParameter` con `byteWidth = 2`, no que el amp lo obedezca.

    @Test
    fun `the active channel writes 2 bytes, unlike every other selector`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.channel.set(1)
        yield()

        assertEquals("F0 41 00 00 00 00 33 12 00 01 00 00 00 01 7E F7", link.hex())
        repo.close()
    }

    @Test
    fun `reading the active channel queries 2 bytes`() = runBlocking {
        val link = FakeLink(answer = { channelReply(3) })
        val repo = repository(link, this)

        assertEquals(3, repo.channel.read())
        assertEquals("F0 41 00 00 00 00 33 11 00 01 00 00 00 00 00 02 7D F7", link.hex())
        repo.close()
    }

    @Test
    fun `a spontaneous channel change updates the cache without echoing`() = runBlocking {
        // El caso real: alguien pisa un footswitch de banco/canal en el propio amplificador.
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        link.receive(channelReply(5))
        yield()

        assertEquals(5, repo.channel.state.value)
        assertTrue("no debe reenviar lo que llega", link.sent.isEmpty())
        repo.close()
    }

    @Test
    fun `the active channel rejects a value outside 0 to 8`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.channel.set(9)
        yield()

        assertTrue("no debe enviar un valor fuera de 0..8", link.sent.isEmpty())
        assertNull(repo.channel.state.value)
        repo.close()
    }

    @Test
    fun `the active channel does not take a level's 1-byte message, or vice versa`() = runBlocking {
        // 06 51 (gain) es 1 byte y 00 01 00 00 (canal) es 2: un fallo en la comprobación de
        // ancho dejaría que uno se colara en el otro.
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        link.receive(levelReply(42, KatanaAddresses.GAIN_LEVEL))
        link.receive(channelReply(2))
        yield()

        assertEquals(42, repo.gainLevel.state.value)
        assertEquals(2, repo.channel.state.value)
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

    @Test
    fun `writing a control never depends on anything but the value itself`() = runBlocking {
        // Contrato de Edit Mode (CLAUDE.md §4.2): quién puede editar lo decide la **UI**, no
        // `device/`. Aquí no existe el concepto de edit mode y no debe existir: el cambio de
        // canal tiene que salir al cable siempre, y meter un gate en esta capa lo rompería
        // sin que la pantalla se enterara. Este test falla si alguien lo intenta.
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        repo.channel.set(3)
        delay(debounce * 3)

        assertEquals("el SET del canal debe salir sin condiciones", 1, link.sent.size)
        assertEquals(
            "F0 41 00 00 00 00 33 12 00 01 00 00 00 03 7C F7",
            link.hex(),
        )
        repo.close()
    }

    // --- El bug de recarga al cambiar de canal (BACKLOG.md, "El estado se desincroniza al
    // cambiar de canal rápido") -----------------------------------------------------------

    /** Responde cualquier GET con ceros del tamaño pedido — sirve tanto para el dump como
     * para cualquier GET de respaldo, así que nada se queda esperando una respuesta que
     * nunca llega. */
    private fun genericGetEcho(message: ByteArray): ByteArray? {
        val bytes = message.map { it.toInt() and 0xFF }
        if (bytes.size < 18 || bytes[7] != RolandSysEx.COMMAND_GET) return null
        val address = Address(bytes[8], bytes[9], bytes[10], bytes[11])
        val size = MidiBytes.decode(
            byteArrayOf(bytes[12].toByte(), bytes[13].toByte(), bytes[14].toByte(), bytes[15].toByte())
        )
        return RolandSysEx.set(address, ByteArray(size))
    }

    @Test
    fun `loadFromDump rejects a spontaneous channel report instead of letting it corrupt the dump`() =
        runBlocking {
            val link = FakeLink(answer = ::genericGetEcho)
            val repo = repository(link, this)

            launch {
                delay(50) // llega mientras la ventana de silencio del dump sigue abierta
                link.receive(RolandSysEx.set(KatanaAddresses.ACTIVE_CHANNEL, MidiBytes.encode(1, 2)))
            }

            val load = repo.loadFromDump()

            assertEquals(
                "el reporte espontáneo debe quedar registrado como rechazado, no colado en el dump",
                1,
                load.rejectedDuringWindow[KatanaAddresses.ACTIVE_CHANNEL],
            )
            // El GET de respaldo —contestado por el mismo eco genérico, con 0— es el que
            // manda al final, no el "1" espurio que llegó durante la ventana del dump.
            assertEquals(0, repo.channel.state.value)
            repo.close()
        }

    /**
     * Como [FakeLink], pero contesta cada GET **con retraso**, desde una corrutina propia:
     * lo que hace falta para que un cambio de canal pueda solaparse de verdad con un dump
     * que sigue en vuelo, en vez de que todo se resuelva en el mismo tick.
     */
    private class DelayedFakeLink(
        private val scope: CoroutineScope,
        private val replyDelayMs: Long,
    ) : KatanaLink {
        private val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
        override val incoming: Flow<ByteArray> = messages
        val dumpRequests = AtomicInteger(0)

        /**
         * El canal "real" del amplificador falso. Sin esto, el GET de respaldo del canal
         * —que dispara todo `loadFromDump`, porque `00 01 00 00` no vive en el dump— se
         * contestaría con ceros y pisaría el reporte espontáneo que se acaba de simular,
         * en vez de confirmarlo como haría el amplificador real.
         */
        private var currentChannel = 0

        override suspend fun send(message: ByteArray): Boolean {
            val bytes = message.map { it.toInt() and 0xFF }
            if (bytes.size >= 18 && bytes[7] == RolandSysEx.COMMAND_GET) {
                val address = Address(bytes[8], bytes[9], bytes[10], bytes[11])
                val size = MidiBytes.decode(
                    byteArrayOf(
                        bytes[12].toByte(), bytes[13].toByte(), bytes[14].toByte(), bytes[15].toByte()
                    )
                )
                if (address == KatanaAddresses.MEMORY_DUMP) dumpRequests.incrementAndGet()
                val reply = if (address == KatanaAddresses.ACTIVE_CHANNEL) {
                    RolandSysEx.set(address, MidiBytes.encode(currentChannel, size))
                } else {
                    RolandSysEx.set(address, ByteArray(size))
                }
                scope.launch {
                    delay(replyDelayMs)
                    messages.emit(reply)
                }
            }
            return true
        }

        suspend fun reportChannel(channel: Int) {
            currentChannel = channel
            messages.emit(RolandSysEx.set(KatanaAddresses.ACTIVE_CHANNEL, MidiBytes.encode(channel, 2)))
        }

        /**
         * El amplificador dice que la cadena predefinida cambió — lo que pasa al elegir una
         * en la UI, ya que el SET es optimista y `06 20` sí vive dentro del dump.
         */
        suspend fun reportChainType(value: Int) {
            messages.emit(RolandSysEx.set(KatanaAddresses.CHAIN_TYPE, byteArrayOf(value.toByte())))
        }
    }

    /**
     * Reconstruye la lógica de `DebugConnectionViewModel.startReloadCoordinator` —único
     * disparador de recarga, `collectLatest`, margen, mutex, guard revalidado después del
     * margen— contra el [KatanaRepository] real.
     *
     * No se prueba la ViewModel directamente porque necesita `android.app.Application` y este
     * proyecto no tiene Robolectric (CLAUDE.md §6 mantiene la lista de dependencias corta).
     * Esto es el mismo reproductor que diagnosticó el bug, ahora contra el diseño corregido.
     */
    private fun CoroutineScope.startTestReloadCoordinator(
        repository: KatanaRepository,
        settleMillis: Long,
        inFlight: AtomicInteger,
        maxInFlight: AtomicInteger,
        onReload: (channel: Int?) -> Unit = {},
    ): Job {
        val mutex = Mutex()
        var loadedChannel: Int? = null
        val requests = MutableStateFlow<Int?>(null) // null = recarga de conexión

        suspend fun reload() {
            val now = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { current -> maxOf(current, now) }
            try {
                repository.loadFromDump()
                loadedChannel = repository.channel.state.value
                onReload(loadedChannel)
            } finally {
                inFlight.decrementAndGet()
            }
        }

        return launch {
            launch {
                repository.channel.state.filterNotNull().collect { channel -> requests.value = channel }
            }
            requests.collectLatest { channel ->
                if (channel != null) {
                    if (channel == loadedChannel) return@collectLatest
                    delay(settleMillis)
                    if (channel == loadedChannel) return@collectLatest
                }
                mutex.withLock { reload() }
            }
        }
    }

    /**
     * Igual que [startTestReloadCoordinator] pero con **los dos disparadores** que tiene hoy el
     * coordinador real: el canal y la cadena predefinida (QA 2026-09-09, bloque A.2).
     *
     * Reproduce la estructura que importa: un solo `MutableStateFlow` conflado, un solo
     * `collectLatest`, guard revalidado después del margen para cada clase de petición.
     */
    private fun CoroutineScope.startTestReloadCoordinatorWithChain(
        repository: KatanaRepository,
        settleMillis: Long,
        onReload: () -> Unit = {},
    ): Job {
        val mutex = Mutex()
        var loadedChannel: Int? = null
        var loadedChainType: Int? = null
        var reloadInFlight = false
        // null = recarga de conexión; Pair(esCanal, valor) = uno de los dos disparadores.
        val requests = MutableStateFlow<Pair<Boolean, Int>?>(null)

        return launch {
            launch {
                repository.channel.state.filterNotNull().collect { requests.value = true to it }
            }
            launch {
                // ⚠️ El guard que importa: `06 20` vive **dentro** del dump, así que se
                // repuebla a mitad de la recarga. Sin esto, esa repoblación cancelaría la
                // recarga en vuelo y lanzaría otra. Espeja a `DebugConnectionViewModel`.
                repository.chainType.state.filterNotNull().collect {
                    if (reloadInFlight) return@collect
                    requests.value = false to it
                }
            }
            requests.collectLatest { request ->
                if (request != null) {
                    val (isChannel, value) = request
                    val loaded = if (isChannel) loadedChannel else loadedChainType
                    if (value == loaded) return@collectLatest
                    delay(settleMillis)
                    val stillLoaded = if (isChannel) loadedChannel else loadedChainType
                    if (value == stillLoaded) return@collectLatest
                }
                mutex.withLock {
                    reloadInFlight = true
                    try {
                        repository.loadFromDump()
                    } finally {
                        reloadInFlight = false
                    }
                    loadedChannel = repository.channel.state.value
                    loadedChainType = repository.chainType.state.value
                    onReload()
                }
            }
        }
    }

    @Test
    fun `cambiar la cadena predefinida dispara una relectura de las veinte ranuras`() = runBlocking {
        // ⚠️ El bug de QA A.2: "el diagrama no cambia al cambiar de cadena". Elegir una cadena
        // reescribe `60 00 06 00`–`06 13` dentro del amplificador, y nada volvía a leerlas.
        val link = DelayedFakeLink(this, replyDelayMs = 5L)
        val repo = repository(link, this)

        val coordinator = startTestReloadCoordinatorWithChain(repo, settleMillis = 60L)

        delay(400) // que termine la recarga de conexión
        val trasConexion = link.dumpRequests.get()

        link.reportChainType(ChainPreset.CHAIN_4_2.value)
        delay(600)

        assertEquals(
            "cambiar de cadena debe releer el dump, que es lo que repuebla las 20 ranuras",
            trasConexion + 1,
            link.dumpRequests.get(),
        )

        coordinator.cancel()
        repo.close()
    }

    @Test
    fun `la relectura por cadena no entra en bucle con el propio dump`() = runBlocking {
        // El dump vuelve a traer `06 20`, así que sin guard esto giraría para siempre. Es el
        // mismo riesgo que ya tenía el canal, y se ataja igual: `loadedChainType`.
        val link = DelayedFakeLink(this, replyDelayMs = 5L)
        val repo = repository(link, this)

        val coordinator = startTestReloadCoordinatorWithChain(repo, settleMillis = 60L)

        delay(400)
        link.reportChainType(ChainPreset.CHAIN_3_1.value)
        delay(1_200) // tiempo de sobra para que un bucle se delatara

        assertEquals(
            "una recarga de conexión + una por el cambio de cadena, y ahí se para",
            2,
            link.dumpRequests.get(),
        )

        coordinator.cancel()
        repo.close()
    }

    @Test
    fun `cadenas seguidas coalescen en una sola relectura`() = runBlocking {
        val link = DelayedFakeLink(this, replyDelayMs = 5L)
        val repo = repository(link, this)

        val coordinator = startTestReloadCoordinatorWithChain(repo, settleMillis = 60L)

        delay(400)
        val trasConexion = link.dumpRequests.get()

        link.reportChainType(ChainPreset.CHAIN_2_1.value); delay(20)
        link.reportChainType(ChainPreset.CHAIN_3_1.value); delay(20)
        link.reportChainType(ChainPreset.CHAIN_4_1.value)
        delay(700)

        assertEquals(
            "tres cambios rápidos son una sola recarga, como con el canal",
            trasConexion + 1,
            link.dumpRequests.get(),
        )

        coordinator.cancel()
        repo.close()
    }

    @Test
    fun `regression - three rapid channel changes trigger one reload, for the last channel`() = runBlocking {
        val link = DelayedFakeLink(this, replyDelayMs = 5L)
        val repo = repository(link, this)
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        var loadedChannel: Int? = null

        val coordinator = startTestReloadCoordinator(repo, settleMillis = 60L, inFlight, maxInFlight) {
            loadedChannel = it
        }

        delay(400) // deja terminar del todo la recarga de conexión antes de tocar el canal
        link.reportChannel(1); delay(30)
        link.reportChannel(2); delay(30)
        link.reportChannel(3)

        delay(600)

        assertEquals(
            "un dump para la conexión + uno para el tramo 1A→2A→3A coalescido, no tres",
            2,
            link.dumpRequests.get(),
        )
        assertEquals(3, repo.channel.state.value)
        assertEquals(3, loadedChannel)

        coordinator.cancel()
        repo.close()
    }

    @Test
    fun `regression - at most one dump is ever in flight, even racing the connection reload`() = runBlocking {
        // Retraso grande a propósito: la recarga de conexión sigue en vuelo cuando el canal
        // cambia, que es justo el escenario que antes producía dos dumps simultáneos.
        val link = DelayedFakeLink(this, replyDelayMs = 200L)
        val repo = repository(link, this)
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        var loadedChannel: Int? = null

        val coordinator = startTestReloadCoordinator(repo, settleMillis = 60L, inFlight, maxInFlight) {
            loadedChannel = it
        }

        delay(20) // la recarga de conexión todavía no ha recibido su respuesta
        link.reportChannel(3)

        // ⚠️ Este margen creció de 800 ms a 4 s el 2026-09-06, y no por hacer sitio "por si
        // acaso": los tres slots de Contour (`60 00 0F 30`/`38`/`40`) caen **fuera** del dump,
        // así que cada recarga añade 6 GET de respaldo, y `loadFromDump` los hace **en serie**.
        // Con `replyDelayMs = 200` son 1,2 s extra por recarga, y aquí hay dos recargas.
        // El invariante que prueba este test —nunca dos dumps a la vez— no cambió; lo que
        // cambió es cuánto tarda una recarga completa. Ver BACKLOG.md, "Pendiente por probar".
        delay(4_000)

        assertEquals("nunca deben coincidir dos dumps en vuelo", 1, maxInFlight.get())
        assertEquals(3, repo.channel.state.value)
        assertEquals(3, loadedChannel)

        coordinator.cancel()
        repo.close()
    }

    // --- Parámetros internos fijos de Delay 1 y Reverb (CLAUDE.md §5.2) --------------------
    //
    // ⚠️ Sin confirmar contra el amplificador. Estos tests comprueban el contrato de cada
    // control —dirección, escala, byteWidth y rechazo de los selectores de frecuencia—, no que
    // el amp obedezca. Los checksums están calculados, no escritos a mano (ver CLAUDE.md, la
    // reverb costó tres candidatas por confiar demasiado en cálculos manuales).

    @Test
    fun `delay time writes 2 bytes at its own address`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.delayTime.setLevel(1500)
        delay(debounce * 3)

        assertEquals("F0 41 00 00 00 00 33 12 60 00 05 02 0B 5C 32 F7", link.hex())
        repo.close()
    }

    @Test
    fun `delay feedback, effect level and direct mix write their own addresses`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.delayFeedback.setLevel(50)
        delay(debounce * 2)
        repo.delayEffectLevel.setLevel(90)
        delay(debounce * 2)
        repo.delayDirectMix.setLevel(75)
        delay(debounce * 2)

        assertEquals(
            listOf(
                "F0 41 00 00 00 00 33 12 60 00 05 04 32 65 F7",
                "F0 41 00 00 00 00 33 12 60 00 05 06 5A 3B F7",
                "F0 41 00 00 00 00 33 12 60 00 05 07 4B 49 F7",
            ),
            List(link.sent.size) { index -> link.hex(index) },
        )
        repo.close()
    }

    @Test
    fun `delay high cut writes its address and rejects a value outside the 15 frequencies`() =
        runBlocking {
            val link = FakeLink()
            val repo = repository(link, this)

            repo.delayHighCut.set(0x05)
            yield()
            assertEquals("F0 41 00 00 00 00 33 12 60 00 05 05 05 11 F7", link.hex())

            repo.delayHighCut.set(0x0F)
            yield()
            assertEquals("un valor fuera del catálogo no debe salir al cable", 1, link.sent.size)
            assertEquals("la caché se queda con el último valor aceptado", 0x05, repo.delayHighCut.state.value)

            repo.close()
        }

    @Test
    fun `reverb pre delay writes 2 bytes at its own address`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.reverbPreDelay.setLevel(250)
        delay(debounce * 3)

        assertEquals("F0 41 00 00 00 00 33 12 60 00 05 43 01 7A 5D F7", link.hex())
        repo.close()
    }

    @Test
    fun `reverb density and direct mix write their own addresses`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.reverbDensity.setLevel(7)
        delay(debounce * 2)
        repo.reverbDirectMix.setLevel(60)
        delay(debounce * 2)

        assertEquals(
            listOf(
                "F0 41 00 00 00 00 33 12 60 00 05 47 07 4D F7",
                "F0 41 00 00 00 00 33 12 60 00 05 49 3C 16 F7",
            ),
            List(link.sent.size) { index -> link.hex(index) },
        )
        repo.close()
    }

    @Test
    fun `reverb low cut writes its address and rejects a value outside the 18 frequencies`() =
        runBlocking {
            val link = FakeLink()
            val repo = repository(link, this)

            repo.reverbLowCut.set(0x05)
            yield()
            assertEquals("F0 41 00 00 00 00 33 12 60 00 05 45 05 51 F7", link.hex())

            repo.reverbLowCut.set(0x12)
            yield()
            assertEquals("un valor fuera del catálogo no debe salir al cable", 1, link.sent.size)

            repo.close()
        }

    @Test
    fun `reverb high cut writes its address and rejects a value outside the 15 frequencies`() =
        runBlocking {
            val link = FakeLink()
            val repo = repository(link, this)

            repo.reverbHighCut.set(0x05)
            yield()
            assertEquals("F0 41 00 00 00 00 33 12 60 00 05 46 05 50 F7", link.hex())

            repo.reverbHighCut.set(0x0F)
            yield()
            assertEquals("un valor fuera del catálogo no debe salir al cable", 1, link.sent.size)

            repo.close()
        }

    @Test
    fun `delay and reverb internal parameters do not take each other's messages`() = runBlocking {
        // 05 02-07 (delay) y 05 40-49 (reverb) son bloques adyacentes en la misma LSB `05`: un
        // desplazamiento de un byte al cablear cualquiera de los dos caería aquí.
        val link = FakeLink()
        val repo = repository(link, this)
        yield()

        link.receive(levelReply(50, KatanaAddresses.DELAY_FEEDBACK))
        yield()

        assertEquals(50, repo.delayFeedback.state.value)
        assertNull("effect level no debe moverse", repo.delayEffectLevel.state.value)
        assertNull("direct mix de delay no debe moverse", repo.delayDirectMix.state.value)
        assertNull("density de reverb no debe moverse", repo.reverbDensity.state.value)
        assertNull("direct mix de reverb no debe moverse", repo.reverbDirectMix.state.value)
        repo.close()
    }

    // --- Parámetros con paso fraccionario (CLAUDE.md §5.2) ---------------------------------
    //
    // ⚠️ Sin confirmar contra el amplificador. `KatanaFractionalParameter` es el primer control
    // de este repositorio cuyo `displayValue`/`setLevel` trabajan en `Double`, no en `Int`.

    @Test
    fun `reverb time writes its own address, in raw bytes derived from the display seconds`() =
        runBlocking {
            val link = FakeLink()
            val repo = repository(link, this)

            repo.reverbTime.setLevel(5.1)
            delay(debounce * 3)

            assertEquals("F0 41 00 00 00 00 33 12 60 00 05 42 32 27 F7", link.hex())
            repo.close()
        }

    @Test
    fun `mod chorus pre delay low and high write their own addresses`() = runBlocking {
        val link = FakeLink()
        val repo = repository(link, this)

        repo.modChorusPreDelayLow.setLevel(20.0)
        delay(debounce * 2)
        repo.modChorusPreDelayHigh.setLevel(10.0)
        delay(debounce * 2)

        assertEquals(
            listOf(
                "F0 41 00 00 00 00 33 12 60 00 02 3A 28 3C F7",
                "F0 41 00 00 00 00 33 12 60 00 02 3E 14 4C F7",
            ),
            List(link.sent.size) { index -> link.hex(index) },
        )
        repo.close()
    }

    @Test
    fun `fractional parameters clamp instead of rejecting, like every continuous level`() =
        runBlocking {
            val link = FakeLink()
            val repo = repository(link, this)

            repo.reverbTime.setLevel(-5.0)
            delay(debounce * 2)
            repo.modChorusPreDelayLow.setLevel(999.0)
            delay(debounce * 2)

            assertEquals(0.1, repo.reverbTime.displayValue!!, 0.0)
            assertEquals(40.0, repo.modChorusPreDelayLow.displayValue!!, 0.0)
            repo.close()
        }

    @Test
    fun `reading reverb time queries its address and converts the reply to seconds`() =
        runBlocking {
            val link = FakeLink(answer = { levelReply(50, KatanaAddresses.REVERB_TIME) })
            val repo = repository(link, this)

            assertEquals(5.1, repo.reverbTime.read()!!.let(repo.reverbTime.scale::toDisplay), 0.0)
            repo.close()
        }

    @Test
    fun `fractional parameters do not take each other's messages, or the neighbouring blocks`() =
        runBlocking {
            // 05 42 (reverb time) está pegado a 05 41 (tipo de reverb) y 05 43 (pre delay,
            // 2 bytes); 02 3A/3E (chorus pre delay) están dentro del bloque de 2x2 Chorus.
            val link = FakeLink()
            val repo = repository(link, this)
            yield()

            link.receive(levelReply(30, KatanaAddresses.REVERB_TIME))
            yield()

            assertEquals(30, repo.reverbTime.state.value)
            assertNull("tipo de reverb no debe moverse", repo.reverbTypeActive.state.value)
            assertNull("pre delay de reverb no debe moverse", repo.reverbPreDelay.state.value)
            assertNull("chorus pre delay low no debe moverse", repo.modChorusPreDelayLow.state.value)
            assertNull("chorus pre delay high no debe moverse", repo.modChorusPreDelayHigh.state.value)
            repo.close()
        }
}
