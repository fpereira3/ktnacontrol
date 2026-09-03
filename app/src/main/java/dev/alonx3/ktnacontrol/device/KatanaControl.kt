package dev.alonx3.ktnacontrol.device

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.RolandMessage
import dev.alonx3.ktnacontrol.protocol.RolandSysEx
import dev.alonx3.ktnacontrol.protocol.awaitRolandReply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How long to wait for the writes to settle before sending (CLAUDE.md §4.2). */
const val DEFAULT_WRITE_DEBOUNCE_MS = 100L

/**
 * One single-byte control of the amp: its address, the cached value, and the read/write path.
 *
 * Everything that is the same for every control lives here — the cache, the GET, the
 * optimistic and coalesced SET, the anti-echo rule — so a new *kind* of control only has to
 * say what values it accepts. Two kinds exist:
 *  - [KatanaParameter], a continuous level over an [IntRange];
 *  - [KatanaEnumParameter], a choice out of a fixed list of byte values.
 *
 * Created through [KatanaRepository]; it is the repository that owns the link and dispatches
 * incoming messages, because there must be exactly one place that writes to the amp.
 */
sealed class KatanaControl(
    /** Where the amp keeps this control — the same address for reading and writing. */
    val address: Address,
    private val link: KatanaLink,
    private val scope: CoroutineScope,
    protected val onDiagnostic: (String) -> Unit,
) {

    private val _state = MutableStateFlow<Int?>(null)

    /** Current raw byte value, or null until it is read or the amp reports it. */
    val state: StateFlow<Int?> = _state.asStateFlow()

    private var pendingWrite: Job? = null

    /**
     * Brings a requested value into something this control actually accepts, or returns null
     * to **reject** it.
     *
     * A continuous level clamps and so never rejects; a selector has gaps between its legal
     * values, where "nearest" would be a guess, so it rejects instead.
     */
    protected abstract fun coerce(value: Int): Int?

    /**
     * How long to let writes settle before sending.
     *
     * A continuous level is dragged and needs coalescing; a discrete choice is a single tap
     * and does not, so it sends straight away.
     */
    protected abstract val debounceMillis: Long

    /**
     * Asks the amp for the current value and caches it.
     *
     * @return the value read, or null on timeout.
     */
    suspend fun read(): Int? {
        val query = RolandSysEx.get(address, size = 1)
        val reply = awaitRolandReply(link.incoming, address) { link.send(query) }
        val value = reply?.data?.firstOrNull()?.toInt()?.and(0xFF) ?: return null
        val accepted = coerce(value)
        if (accepted == null) {
            onDiagnostic("GET $address devolvió $value, que este control no acepta")
            return null
        }
        _state.value = accepted
        return accepted
    }

    /**
     * Sets the value: the cache updates **now**, the write goes out once things settle.
     *
     * Optimistic because a Roland write is fire-and-forget and never acknowledged, so there
     * is nothing to wait for — waiting would only make the control lag behind the finger.
     *
     * **Coalesced** when [debounceMillis] is non-zero: each call cancels the pending send and
     * restarts the timer, so dragging a slider sends only the last value (CLAUDE.md §4.2).
     */
    fun set(value: Int) {
        val accepted = coerce(value)
        if (accepted == null) {
            onDiagnostic("SET $address = $value ignorado: valor no válido para este control")
            return
        }
        _state.value = accepted

        pendingWrite?.cancel()
        pendingWrite = scope.launch {
            if (debounceMillis > 0) delay(debounceMillis)
            val message = RolandSysEx.set(address, byteArrayOf(accepted.toByte()))
            val sent = link.send(message)
            onDiagnostic("SET $address = $accepted ${if (sent) "OK" else "FALLÓ"}")
        }
    }

    /**
     * Applies a message the amp sent on its own, if it is for this control.
     *
     * **Never sends anything**: that is the anti-echo rule of CLAUDE.md §4.2, and it lives
     * here because this is the only class that could break it.
     *
     * @return true if the message was consumed.
     */
    internal fun applyIncoming(data: RolandMessage.Data): Boolean {
        if (data.address != address) return false
        val value = data.data.firstOrNull()?.toInt()?.and(0xFF) ?: return false
        val accepted = coerce(value)
        if (accepted == null) {
            // El mensaje era para esta dirección, así que se consume igual: reenviarlo a
            // otro control sería peor. Se registra y la caché se queda como estaba.
            onDiagnostic("entrante $address = $value fuera de las opciones conocidas")
            return true
        }
        _state.value = accepted
        return true
    }

    /** Drops any write that had not gone out yet. */
    internal fun close() {
        pendingWrite?.cancel()
        pendingWrite = null
    }
}

/**
 * A continuous level, `0..100` for every one confirmed so far.
 *
 * Values outside [range] are clamped before anything is sent.
 */
class KatanaParameter internal constructor(
    address: Address,
    val range: IntRange,
    link: KatanaLink,
    scope: CoroutineScope,
    override val debounceMillis: Long,
    onDiagnostic: (String) -> Unit,
) : KatanaControl(address, link, scope, onDiagnostic) {

    override fun coerce(value: Int): Int = value.coerceIn(range)

    init {
        require(!range.isEmpty()) { "un parámetro necesita un rango no vacío" }
    }
}

/**
 * A choice out of a fixed list of raw byte values: an amp model, an effect colour, an on/off.
 *
 * Two things make it different from [KatanaParameter], and both come from the values not
 * being a contiguous range:
 *  - **Clamping makes no sense.** [AmpType][dev.alonx3.ktnacontrol.protocol.AmpType] uses
 *    `0x00`..`0x20` with gaps, so "nearest legal value" is meaningless. A value that is not
 *    in [options] is **rejected**: the cache keeps what it had and nothing is sent. That
 *    matters most for what the amp *reports* — if it ever sends a value outside the table,
 *    the right answer is to leave the UI alone and let it show up in the diagnostics log,
 *    not to snap the control to some neighbour.
 *  - **No debounce.** A radio button or a switch is a single tap, not a drag, so there is no
 *    flood to coalesce and waiting 100 ms would only make the amp feel sluggish.
 */
class KatanaEnumParameter internal constructor(
    address: Address,
    /** Every raw byte value the amp accepts here, in the order the UI should offer them. */
    val options: List<Int>,
    link: KatanaLink,
    scope: CoroutineScope,
    onDiagnostic: (String) -> Unit,
) : KatanaControl(address, link, scope, onDiagnostic) {

    init {
        require(options.isNotEmpty()) { "un selector necesita al menos una opción" }
        require(options.all { it in 0..0x7F }) {
            "los valores SysEx son de 7 bits: $options se sale de 0..127"
        }
    }

    override val debounceMillis: Long = 0L

    override fun coerce(value: Int): Int? = value.takeIf { it in options }

    /** Whether [value] is one of the [options] this control offers. */
    fun accepts(value: Int): Boolean = value in options
}
