package dev.alonx3.ktnacontrol.device

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.MemoryImage
import dev.alonx3.ktnacontrol.protocol.MidiBytes
import dev.alonx3.ktnacontrol.protocol.RolandMessage
import dev.alonx3.ktnacontrol.protocol.RolandSysEx
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Un [KatanaLink] que en vez de un cable es una imagen de memoria en RAM (CLAUDE.md §4.5).
 *
 * Es lo que permite **editar un preset sin amplificador** reutilizando los controles tal cual:
 * `KatanaRepository(OfflineKatanaLink(image), scope)` da el juego completo de sliders y
 * selectores, con su caché, su debounce y su regla anti-eco, sin que `KatanaControl` ni
 * `KatanaRepository` ni un solo composable sepan que no hay nada enchufado.
 *
 * No hizo falta inventar una abstracción para esto: [KatanaLink] **ya era** el backend de un
 * parámetro, solo que hasta ahora tenía una sola implementación. Y el patrón tampoco es nuevo
 * —los tests JVM llevan desde el principio usando un `KatanaLink` falso (CLAUDE.md §6)—; esto
 * es el mismo patrón ascendido a código de producción.
 *
 * **Qué hace con cada mensaje:**
 * - **SET** (`0x12`): guarda los bytes en [image]. No emite nada, igual que el amplificador no
 *   confirma una escritura (§4.2) — así que la regla anti-eco se cumple sin hacer nada.
 * - **GET** (`0x11`): contesta desde [image], **de forma síncrona dentro de `send`**.
 *
 * ⚠️ **Lo de "síncrona dentro de `send`" es lo que lo hace funcionar, y no es casualidad que
 * funcione.** `awaitRolandReply` y `sendAndCollectUntilQuiet` se suscriben con
 * `CoroutineStart.UNDISPATCHED` **antes** de llamar a `send` (§4.4). Eso estaba puesto para no
 * perder una respuesta rápida del amplificador; resulta ser exactamente lo que hace falta para
 * que una respuesta emitida desde dentro de `send` llegue a quien la espera.
 */
class OfflineKatanaLink(
    /** La memoria que se está editando. Los SET la modifican en el sitio. */
    val image: MemoryImage,
) : KatanaLink {

    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = INCOMING_BUFFER)

    override val incoming: Flow<ByteArray> = _incoming.asSharedFlow()

    /** Direcciones que se pidieron y la imagen no tenía. Diagnóstico, no error. */
    private val _missedReads = mutableListOf<Address>()
    val missedReads: List<Address> get() = _missedReads.toList()

    override suspend fun send(message: ByteArray): Boolean {
        val command = RolandSysEx.commandOf(message) ?: return false
        val parsed = RolandSysEx.parse(message) as? RolandMessage.Data ?: return false

        return when (command) {
            RolandSysEx.COMMAND_SET -> {
                image.write(parsed.address, parsed.data)
                true
            }

            RolandSysEx.COMMAND_GET -> answer(parsed.address, MidiBytes.decode(parsed.data))
            else -> false
        }
    }

    /**
     * Contesta a un GET con lo que la imagen tenga en ese rango, **troceado en tramos
     * contiguos** igual que hace el amplificador.
     *
     * Un rango que la imagen no cubre entero no se rellena: se contesta solo con lo que hay.
     * Ese es justo el caso que el resto del proyecto ya sabe tratar —"el dump no cubrió esta
     * dirección" es una respuesta normal, no un error (§4.4)— así que un control cuyo valor no
     * está en el fichero se queda en null y la UI enseña "—", que es lo correcto.
     *
     * @return true si se emitió al menos un mensaje.
     */
    private suspend fun answer(base: Address, size: Int): Boolean {
        if (size <= 0) return false
        val requested = MemoryImage.empty()
        var found = false
        for (offset in 0 until size) {
            val address = base + offset
            val byte = image.byteAt(address)
            if (byte != null) {
                requested.write(address, byteArrayOf(byte.toByte()))
                found = true
            }
        }
        if (!found) {
            _missedReads += base
            return false
        }
        // Trocear a MAX_REPLY_PAYLOAD reproduce lo que hace el amplificador de verdad (8
        // mensajes de 241 bytes para el dump, §4.4), así que el camino de reensamblado se
        // ejercita igual offline que en vivo en vez de quedar sin probar.
        requested.toDump().chunks.forEach { chunk ->
            chunk.data.toList().chunked(MAX_REPLY_PAYLOAD).forEachIndexed { index, part ->
                val address = chunk.base + index * MAX_REPLY_PAYLOAD
                _incoming.emit(RolandSysEx.set(address, part.toByteArray()))
            }
        }
        return true
    }

    companion object {
        /**
         * Bytes de datos por mensaje de respuesta.
         *
         * 241 es lo que manda el Katana real en el dump (§4.4). No hay ninguna razón técnica
         * para respetarlo aquí —no hay cable ni paquetes de 512 B— salvo la que importa: que
         * el camino que se ejercita offline sea el mismo que se ejercita en vivo.
         */
        const val MAX_REPLY_PAYLOAD = 241

        /**
         * Holgura del `SharedFlow`.
         *
         * Un dump completo son ~8 mensajes; 64 deja margen de sobra sin que `emit` llegue a
         * suspender por falta de sitio, que es lo que colgaría un `send` síncrono.
         */
        const val INCOMING_BUFFER = 64
    }
}
