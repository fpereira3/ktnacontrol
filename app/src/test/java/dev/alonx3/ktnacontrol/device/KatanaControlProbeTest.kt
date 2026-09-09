package dev.alonx3.ktnacontrol.device

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.RolandSysEx
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [KatanaControl.probeWrite]: la prueba que separa "la dirección es correcta pero no suena"
 * de "la dirección no acepta la escritura".
 *
 * Existe porque tres controles —Solo del amplificador, Bright y Gain SW— no produjeron ningún
 * efecto audible, y desde fuera las dos causas se ven exactamente igual. Es el mismo chequeo
 * que resolvió `60 00 05 48` con el nivel de reverb (CLAUDE.md §5).
 *
 * Cada caso de aquí es una de las conclusiones que el diagnóstico tiene que saber distinguir.
 */
class KatanaControlProbeTest {

    /**
     * Un amplificador de mentira con una sola dirección, que se puede configurar para
     * comportarse como cada una de las hipótesis.
     */
    private class FakeAmp(
        private var value: Int,
        /** Si false, el SET se recibe y se tira: el patrón de una dirección de solo lectura. */
        private val acceptsWrites: Boolean = true,
        /** Si false, el GET se queda sin contestar: la dirección ni siquiera se lee. */
        private val answersReads: Boolean = true,
    ) : KatanaLink {

        val sent = mutableListOf<ByteArray>()
        private val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
        override val incoming: Flow<ByteArray> = messages

        override suspend fun send(message: ByteArray): Boolean {
            sent += message
            // `RolandMessage` no distingue GET de SET —los dos son `Data` al parsear—, así
            // que el comando se mira en el byte crudo, igual que hace `genericGetEcho` en
            // KatanaRepositoryTest.
            val bytes = message.map { it.toInt() and 0xFF }
            if (bytes.size < 14) return true
            val address = Address(bytes[8], bytes[9], bytes[10], bytes[11])
            when (bytes[7]) {
                RolandSysEx.COMMAND_GET -> {
                    if (!answersReads) return true
                    messages.emit(RolandSysEx.set(address, byteArrayOf(value.toByte())))
                }

                RolandSysEx.COMMAND_SET -> {
                    if (acceptsWrites) value = bytes[12]
                }
            }
            return true
        }

        /** Cuántos SET llegaron, para comprobar que la prueba cancela lo pendiente. */
        fun writeCount(): Int =
            sent.count { it.size > 7 && (it[7].toInt() and 0xFF) == RolandSysEx.COMMAND_SET }
    }

    /**
     * Monta un repositorio contra [amp], corre [block] y **siempre lo cierra**.
     *
     * El cierre no es opcional: `KatanaRepository` deja un colector vivo sobre
     * `link.incoming`, y sin `close()` el `runBlocking` nunca termina porque espera a ese
     * hijo. Es la misma disciplina que sigue KatanaRepositoryTest.
     */
    private fun <T> withRepository(amp: FakeAmp, block: suspend (KatanaRepository) -> T): T =
        runBlocking {
            val repository = KatanaRepository(link = amp, scope = this, debounceMillis = 50L)
            try {
                block(repository)
            } finally {
                repository.close()
            }
        }

    private fun probe(
        amp: FakeAmp,
        block: suspend (KatanaRepository) -> WriteProbe?,
    ): WriteProbe? = withRepository(amp, block)

    @Test
    fun `una direccion que acepta la escritura se ve como aceptada`() {
        val amp = FakeAmp(value = 0x00, acceptsWrites = true)
        val result = probe(amp) { it.ampBright.probeWrite(0x01) }

        requireNotNull(result)
        assertEquals(KatanaAddresses.AMP_BRIGHT, result.address)
        assertEquals(0x00, result.before)
        assertEquals(0x01, result.requested)
        assertEquals(0x01, result.after)
        assertTrue(result.accepted)
        assertTrue(result.readable)
        assertTrue(result.verdict.contains("la escritura SÍ entra"))
    }

    @Test
    fun `una direccion de solo lectura se ve como que ignora la escritura`() {
        // El patrón de `60 00 06 5C` (la variación): contesta al GET, acepta el mensaje y
        // sigue reportando su valor real. Es lo que hay que poder distinguir.
        val amp = FakeAmp(value = 0x00, acceptsWrites = false)
        val result = probe(amp) { it.ampBright.probeWrite(0x01) }

        requireNotNull(result)
        assertEquals(0x00, result.before)
        assertEquals(0x00, result.after)
        assertFalse(result.accepted)
        assertTrue(result.readable)
        assertTrue(result.verdict.contains("NO se movió"))
    }

    @Test
    fun `una direccion que no contesta al GET se distingue de una que no acepta escritura`() {
        val amp = FakeAmp(value = 0x00, answersReads = false)
        val result = probe(amp) { it.ampBright.probeWrite(0x01) }

        requireNotNull(result)
        assertNull(result.before)
        assertNull(result.after)
        assertFalse(result.readable)
        assertTrue(result.verdict.contains("no contesta al GET"))
    }

    @Test
    fun `escribir el valor que ya tenia no concluye nada`() {
        // Sin esto la prueba se engañaría sola: "after == requested" también se cumple cuando
        // el amp nunca se movió porque ya estaba ahí.
        val amp = FakeAmp(value = 0x01, acceptsWrites = false)
        val result = probe(amp) { it.ampBright.probeWrite(0x01) }

        requireNotNull(result)
        assertEquals(0x01, result.before)
        assertEquals(0x01, result.after)
        assertTrue(result.accepted)
        assertTrue(result.verdict.contains("no concluyente"))
    }

    @Test
    fun `la prueba no toca la cache con el valor pedido`() {
        // La diferencia con `set`, que sí es optimista. Si `probeWrite` actualizara la caché,
        // la UI mostraría el valor pedido y el diagnóstico diría lo que se quiere oír.
        val amp = FakeAmp(value = 0x00, acceptsWrites = false)
        val cached = withRepository(amp) { repository ->
            repository.ampBright.probeWrite(0x01)
            repository.ampBright.state.value
        }
        assertEquals(0x00, cached)
    }

    @Test
    fun `un selector rechaza un valor fuera de su lista y no manda nada`() {
        val amp = FakeAmp(value = 0x00)
        val result = probe(amp) { it.ampGainSw.probeWrite(0x09) }

        assertNull(result)
        assertTrue(amp.sent.isEmpty())
    }

    @Test
    fun `la prueba cancela una escritura pendiente en vez de dejarla colarse`() {
        // Un SET con debounce en vuelo entre el GET de antes y el de después falsearía el
        // resultado: parecería que el amp aceptó cuando lo que llegó fue el valor viejo.
        //
        // El caso solo existe en un nivel: un selector no debounce (CLAUDE.md §4.3), así que
        // su SET sale enseguida y nunca queda pendiente. Por eso aquí va el Solo Level.
        val amp = FakeAmp(value = 0x00, acceptsWrites = true)
        val result = withRepository(amp) { repository ->
            repository.ampSoloLevelPanel.setLevel(90)
            yield()
            repository.ampSoloLevelPanel.probeWrite(40)
        }

        requireNotNull(result)
        assertEquals(40, result.after)
        // Solo el SET de la prueba llegó al amplificador; el de 90 se canceló a medio esperar.
        assertEquals(1, amp.writeCount())
    }

    @Test
    fun `las dos candidatas del Solo son direcciones distintas y ambas se pueden probar`() {
        // El punto de toda la Parte 1: la candidata del PREAMP sigue cableada y la del panel
        // se añade aparte, no en su lugar.
        assertEquals(
            KatanaAddresses.AMP_SOLO_ENABLED,
            Address(0x60, 0x00, 0x00, 0x2B),
        )
        assertEquals(
            KatanaAddresses.AMP_SOLO_ENABLED_PANEL,
            Address(0x60, 0x00, 0x06, 0x14),
        )
        assertEquals(
            KatanaAddresses.AMP_SOLO_LEVEL_PANEL,
            Address(0x60, 0x00, 0x06, 0x15),
        )

        val amp = FakeAmp(value = 0x00, acceptsWrites = true)
        withRepository(amp) { repository ->
            val preamp = repository.ampSoloEnabled.probeWrite(0x01)
            val panel = repository.ampSoloEnabledPanel.probeWrite(0x01)

            requireNotNull(preamp)
            requireNotNull(panel)
            assertEquals(KatanaAddresses.AMP_SOLO_ENABLED, preamp.address)
            assertEquals(KatanaAddresses.AMP_SOLO_ENABLED_PANEL, panel.address)
        }
    }
}
