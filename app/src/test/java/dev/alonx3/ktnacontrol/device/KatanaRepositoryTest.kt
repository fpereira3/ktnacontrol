package dev.alonx3.ktnacontrol.device

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.AmpCategory
import dev.alonx3.ktnacontrol.protocol.AmpType
import dev.alonx3.ktnacontrol.protocol.BoostType
import dev.alonx3.ktnacontrol.protocol.DelayType
import dev.alonx3.ktnacontrol.protocol.EffectColor
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.MidiBytes
import dev.alonx3.ktnacontrol.protocol.ModFxType
import dev.alonx3.ktnacontrol.protocol.ReverbType
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
}
