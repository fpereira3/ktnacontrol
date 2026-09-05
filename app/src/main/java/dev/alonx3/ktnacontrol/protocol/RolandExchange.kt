package dev.alonx3.ktnacontrol.protocol

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long to wait for the amp to answer a query before giving up on it. */
const val DEFAULT_REPLY_TIMEOUT_MS = 800L

/** How long to keep watching the stream after a fire-and-forget write. */
const val DEFAULT_WATCH_WINDOW_MS = 1_500L

/**
 * Sends a query and waits for *its own* answer, correlating by address.
 *
 * Replies arrive asynchronously on a single shared stream, so firing several queries in a
 * row and assuming the answers come back in the same order is a race. This pairs each
 * request with the first Roland message carrying the address that was asked for, and lets
 * the caller drive the next query only once this one settled.
 *
 * Two details make it correct rather than merely likely to work:
 *
 *  - The collector starts with [CoroutineStart.UNDISPATCHED], so it is already subscribed
 *    to [messages] before [send] runs. Subscribing after sending would lose a reply that
 *    arrives quickly.
 *  - [messages] must be a **shared** stream with a single underlying reader. Handing this a
 *    cold flow that opens its own read loop would put two readers on the same endpoint,
 *    stealing each other's messages.
 *
 * Pure Kotlin — no `android.*` — so the correlation can be tested on the JVM with a fake
 * flow.
 *
 * @return the matching reply, or null if [timeoutMillis] elapsed first. A timeout is not an
 *   error here: the caller logs it and moves on to the next address.
 */
suspend fun awaitRolandReply(
    messages: Flow<ByteArray>,
    address: Address,
    timeoutMillis: Long = DEFAULT_REPLY_TIMEOUT_MS,
    send: suspend () -> Unit,
): RolandMessage.Data? = withTimeoutOrNull(timeoutMillis) {
    coroutineScope {
        val reply = async(start = CoroutineStart.UNDISPATCHED) {
            messages
                .mapNotNull { raw -> RolandSysEx.parse(raw) as? RolandMessage.Data }
                .first { data -> data.address == address }
        }
        send()
        reply.await()
    }
}

/** Silencio que da por terminada una respuesta multi-mensaje. */
const val DEFAULT_QUIET_MS = 250L

/** Tope absoluto por si el amplificador no contesta o se queda a medias. */
const val DEFAULT_COLLECT_TIMEOUT_MS = 3_000L

/** Cada cuánto se mira si ya hubo silencio. */
private const val QUIET_POLL_MS = 25L

/**
 * El payload más grande que puede tener la lectura o el reporte espontáneo de **un**
 * control: el canal activo es el único de 2 bytes (CLAUDE.md §5.1), todo lo demás es 1.
 * Refleja el mismo umbral que ya usa `KatanaRepository.onIncoming`, invertido
 * (`data.data.size !in 1..2`) — los dos consumidores del stream parten el mismo tráfico con
 * el mismo criterio.
 */
const val MAX_CONTROL_PAYLOAD = 2

/**
 * Lo que dejó [sendAndCollectUntilQuiet] al terminar: los mensajes que sí eran parte de lo
 * pedido, y las direcciones de los que no.
 *
 * [rejected] existe para diagnóstico —un resumen agrupado, no una entrada por mensaje— y
 * [invalidCount] para lo que ni siquiera se pudo parsear como Roland.
 */
data class QuietCollection(
    /** Lo que sí es respuesta a lo pedido, ya parseado. */
    val accepted: List<RolandMessage.Data>,
    /** Direcciones de mensajes bien formados que [accept] rechazó, uno por mensaje visto. */
    val rejected: List<Address>,
    /** Mensajes que no se pudieron parsear en absoluto — cabecera, checksum, etc. */
    val invalidCount: Int = 0,
)

/**
 * Acepta solo mensajes Roland que parezcan un trozo del dump de memoria pedido con [base] y
 * [size]: dentro del rango pedido, y demasiado grandes para ser el reporte de un solo
 * control.
 *
 * Ninguna de las dos condiciones basta por sí sola (BACKLOG.md, "Propuesta de diseño:
 * filtrado del dump y serialización de recargas"): **37 de los 38 controles registrados**
 * viven dentro de `[60 00 00 00, +1920)`, así que el rango solo deja pasar casi cualquier
 * reporte espontáneo — una perilla, un color, un tipo de efecto. Y el tamaño solo tragaría
 * las respuestas de 16 bytes del nombre de dispositivo/preset si alguna vez se solaparan con
 * un dump. Las dos juntas es lo que de verdad distingue un trozo de dump de cualquier otra
 * cosa que pase por el cable.
 */
fun blockReplyIn(base: Address, size: Int): (RolandMessage.Data) -> Boolean = { data ->
    val offset = data.address - base
    offset >= 0 && offset < size && data.data.size > MAX_CONTROL_PAYLOAD
}

/**
 * Envía y recoge una respuesta de **varios mensajes**, terminando en cuanto el amplificador
 * se calla en vez de esperar siempre una ventana fija.
 *
 * Para el dump esa diferencia es todo: los 8 mensajes llegan en ~275 ms, así que esperar una
 * ventana de 3 s dejaba los controles sin poblar durante 2,7 s de más — más lento que los 24
 * GET en serie a los que sustituye, que tardaban ~540 ms.
 *
 * No se puede saber de antemano cuántos mensajes vendrán —depende de qué rangos estén
 * ocupados— así que la condición de parada es **el silencio**, no una cuenta: [quietMillis]
 * sin nada nuevo. El hueco observado entre mensajes es de ~30 ms, así que 250 ms es un
 * margen de ocho veces.
 *
 * ⚠️ **El silencio se mide solo sobre lo aceptado.** Un mensaje que [accept] rechaza —un
 * reporte espontáneo que llegó mientras el dump estaba en vuelo, por ejemplo un cambio de
 * canal— **no reinicia el contador**. Antes de este filtro sí lo hacía, y era la causa
 * directa de que la ventana se estirara hasta [timeoutMillis] cada vez que algo se movía
 * durante la recogida (BACKLOG.md, "El estado se desincroniza al cambiar de canal rápido").
 *
 * [timeoutMillis] es el tope absoluto, para cuando no llega nada en absoluto.
 *
 * Se suscribe con [CoroutineStart.UNDISPATCHED] antes de [send] por lo mismo que
 * [awaitRolandReply], y necesita igualmente un stream **compartido**.
 *
 * @param accept decide si un mensaje bien formado es parte de lo pedido; por defecto acepta
 *   todo, que es el comportamiento de antes de este filtro. [blockReplyIn] da el predicado
 *   que sí distingue un dump.
 */
suspend fun sendAndCollectUntilQuiet(
    messages: Flow<ByteArray>,
    accept: (RolandMessage.Data) -> Boolean = { true },
    quietMillis: Long = DEFAULT_QUIET_MS,
    timeoutMillis: Long = DEFAULT_COLLECT_TIMEOUT_MS,
    send: suspend () -> Unit,
): QuietCollection = coroutineScope {
    val accepted = ArrayList<RolandMessage.Data>()
    val rejected = ArrayList<Address>()
    var invalidCount = 0
    val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
        messages.collect { raw ->
            when (val parsed = RolandSysEx.parse(raw)) {
                is RolandMessage.Data ->
                    if (accept(parsed)) accepted.add(parsed) else rejected.add(parsed.address)
                is RolandMessage.Invalid -> invalidCount++
            }
        }
    }
    send()
    withTimeoutOrNull(timeoutMillis) {
        var seen = -1
        var quietFor = 0L
        while (quietFor < quietMillis || accepted.isEmpty()) {
            delay(QUIET_POLL_MS)
            if (accepted.size == seen) {
                quietFor += QUIET_POLL_MS
            } else {
                seen = accepted.size
                quietFor = 0L
            }
        }
    }
    watcher.cancel()
    QuietCollection(accepted, rejected, invalidCount)
}

/**
 * Sends something the amp does not acknowledge, then watches the stream for a while and
 * reports everything that showed up.
 *
 * A Roland write (`0x12`) is fire-and-forget: there is no reply to correlate by address, so
 * [awaitRolandReply] does not apply. Evidence, both from `reference/`:
 *
 *  - `TuxKatana/HOW.md` shows the edit-mode write with only a send arrow and no reply,
 *    while every query in the same trace has one.
 *  - `TuxKatana/lib/controller.py`'s `set_edit_mode()` sends and moves on, whereas
 *    `get_name()` and `get_presets()` wait for an answer.
 *
 * So this is deliberately observational: it does not decide whether the write worked, it
 * just shows whether anything came back — which for a write is expected to be nothing.
 *
 * Subscribes with [CoroutineStart.UNDISPATCHED] before [send] for the same reason as
 * [awaitRolandReply], and likewise needs a **shared** [messages] stream.
 *
 * @return every message seen during the window, in arrival order; empty if none.
 */
suspend fun sendAndCollect(
    messages: Flow<ByteArray>,
    windowMillis: Long = DEFAULT_WATCH_WINDOW_MS,
    send: suspend () -> Unit,
): List<ByteArray> = coroutineScope {
    val collected = ArrayList<ByteArray>()
    val watcher = async(start = CoroutineStart.UNDISPATCHED) {
        withTimeoutOrNull(windowMillis) {
            messages.collect { message -> collected.add(message) }
        }
    }
    send()
    watcher.await() // Always ends by timeout: the stream itself never completes.
    collected
}
