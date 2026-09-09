package dev.alonx3.ktnacontrol.device

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.PresetSave
import dev.alonx3.ktnacontrol.protocol.RolandSysEx
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La secuencia de guardado en `KatanaRepository`: **nombre primero, commit después**, y la
 * lectura de vuelta como única verificación posible.
 *
 * El orden no es un detalle de estilo: el commit copia lo que haya en el búfer del
 * amplificador, así que un commit que saliera antes que el nombre guardaría el nombre viejo, y
 * como no hay confirmación por SysEx **nadie se enteraría** hasta abrir el preset semanas
 * después. Por eso aquí se comprueba el orden explícitamente y no solo que ambos salgan.
 */
class PresetSaveSequenceTest {

    /**
     * Un amplificador de mentira que apunta lo que se le manda y contesta a los GET de nombre
     * de preset con lo que tenga "guardado".
     */
    private class SavingFakeAmp(
        /** Si false, no contesta al GET del nombre: el caso de "no hay forma de confirmar". */
        private val answersNameRead: Boolean = true,
        /** Si false, el commit se ignora y el nombre guardado no cambia: guardado que no entra. */
        private val commitWorks: Boolean = true,
    ) : KatanaLink {

        val sent = mutableListOf<ByteArray>()
        private val messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
        override val incoming: Flow<ByteArray> = messages

        /** Lo que el amp diría tener en cada canal, antes de guardar nada. */
        private val stored = mutableMapOf<Int, String>().apply {
            PresetSave.CHANNELS.forEach { put(it, "VIEJO $it") }
        }

        /** El nombre que se escribió en `60 00 00 00` y que el commit copiaría. */
        private var buffered: String? = null

        override suspend fun send(message: ByteArray): Boolean {
            sent += message
            val bytes = message.map { it.toInt() and 0xFF }
            if (bytes.size < 14) return true
            val address = Address(bytes[8], bytes[9], bytes[10], bytes[11])
            val payload = message.copyOfRange(12, message.size - 2)

            when (bytes[7]) {
                RolandSysEx.COMMAND_SET -> when (address) {
                    KatanaAddresses.CURRENT_PRESET_NAME -> buffered = PresetSave.decodeName(payload)
                    KatanaAddresses.PRESET_SAVE -> if (commitWorks) {
                        // Lo que hace el amplificador de verdad: copiar el búfer al canal.
                        val channel = payload.last().toInt()
                        buffered?.let { stored[channel] = it }
                    }
                }

                RolandSysEx.COMMAND_GET -> {
                    if (!answersNameRead) return true
                    val channel = PresetSave.CHANNELS.firstOrNull {
                        KatanaAddresses.presetName(it) == address
                    } ?: return true
                    messages.emit(
                        RolandSysEx.set(address, PresetSave.encodeName(stored.getValue(channel)))
                    )
                }
            }
            return true
        }

        /** Los SET que llegaron, en orden, como pares (dirección, datos). */
        fun writes(): List<Pair<Address, ByteArray>> = sent
            .filter { it.size > 12 && (it[7].toInt() and 0xFF) == RolandSysEx.COMMAND_SET }
            .map { message ->
                val bytes = message.map { it.toInt() and 0xFF }
                Address(bytes[8], bytes[9], bytes[10], bytes[11]) to
                    message.copyOfRange(12, message.size - 2)
            }

        fun storedName(channel: Int): String = stored.getValue(channel)
    }

    /**
     * Monta el repositorio, corre [block] y **siempre lo cierra**: sin `close()` el colector de
     * `link.incoming` mantiene vivo el `runBlocking`. Misma disciplina que el resto de tests.
     */
    private fun <T> withRepository(amp: SavingFakeAmp, block: suspend (KatanaRepository) -> T): T =
        runBlocking {
            val repository = KatanaRepository(link = amp, scope = this, debounceMillis = 0L)
            try {
                block(repository)
            } finally {
                repository.close()
            }
        }

    @Test
    fun `el nombre sale antes que el commit, y solo esos dos SET`() {
        val amp = SavingFakeAmp()
        withRepository(amp) { it.savePreset(name = "Crunch", channel = 3, settleMillis = 0L) }

        val writes = amp.writes()
        assertEquals("un guardado son exactamente dos SET", 2, writes.size)
        assertEquals(KatanaAddresses.CURRENT_PRESET_NAME, writes[0].first)
        assertEquals(KatanaAddresses.PRESET_SAVE, writes[1].first)
        assertEquals("Crunch", PresetSave.decodeName(writes[0].second))
        assertEquals(3, writes[1].second.last().toInt())
    }

    @Test
    fun `guardar en un canal distinto del que se edita no toca ningun otro`() {
        val amp = SavingFakeAmp()
        val result = withRepository(amp) {
            it.savePreset(name = "Solo Lead", channel = 6, settleMillis = 0L)
        }

        assertEquals(6, result.channel)
        assertEquals("B2", result.channelLabel)
        assertEquals("Solo Lead", amp.storedName(6))
        // Los otros siete siguen intactos: el commit lleva el destino dentro, no lo adivina.
        (PresetSave.CHANNELS - 6).forEach { channel ->
            assertEquals("VIEJO $channel", amp.storedName(channel))
        }
    }

    @Test
    fun `la lectura de vuelta confirma el guardado cuando el nombre coincide`() {
        val amp = SavingFakeAmp()
        val result = withRepository(amp) {
            it.savePreset(name = "Clean A2", channel = 2, settleMillis = 0L)
        }

        assertTrue(result.nameSent)
        assertTrue(result.commitSent)
        assertEquals("Clean A2", result.storedName)
        assertTrue(result.nameMatches)
        assertTrue(result.verdict.contains("el nombre entró"))
    }

    @Test
    fun `si el commit no entra, el nombre leido no coincide y el veredicto no miente`() {
        // El amp acepta los mensajes y no guarda: sin la lectura de vuelta, la app no tendría
        // ninguna forma de distinguir esto de un guardado correcto.
        val amp = SavingFakeAmp(commitWorks = false)
        val result = withRepository(amp) {
            it.savePreset(name = "No entra", channel = 4, settleMillis = 0L)
        }

        assertEquals("VIEJO 4", result.storedName)
        assertFalse(result.nameMatches)
        assertTrue(result.verdict.contains("o el guardado no entró"))
    }

    @Test
    fun `si el amp no contesta al GET, se dice que no se pudo confirmar y no que fallo`() {
        val amp = SavingFakeAmp(answersNameRead = false)
        val result = withRepository(amp) {
            it.savePreset(name = "Sin eco", channel = 8, settleMillis = 0L)
        }

        assertNull(result.storedName)
        assertFalse(result.nameMatches)
        assertTrue(result.verdict.contains("sin forma de confirmar"))
        // Los dos SET sí salieron: no contestar al GET no significa que el guardado fallara.
        assertTrue(result.nameSent && result.commitSent)
    }

    @Test
    fun `el nombre saneado es el que se compara, no el que se tecleo`() {
        // Con acentos, lo que llega al amp lleva `?`. Si `savePreset` comparase contra el texto
        // original, un guardado correcto se reportaría como fallido para siempre.
        val amp = SavingFakeAmp()
        val result = withRepository(amp) {
            it.savePreset(name = "Distorsión", channel = 1, settleMillis = 0L)
        }

        assertEquals("Distorsi?n", result.requestedName)
        assertEquals("Distorsi?n", result.storedName)
        assertTrue(result.nameMatches)
    }

    @Test
    fun `un canal fuera de rango se rechaza sin mandar nada`() {
        val amp = SavingFakeAmp()
        val failed = runCatching {
            withRepository(amp) { it.savePreset(name = "X", channel = 0, settleMillis = 0L) }
        }.isFailure

        assertTrue("el canal 0 (PANEL) debería rechazarse", failed)
        assertTrue("no debería haber salido ningún mensaje", amp.sent.isEmpty())
    }
}
