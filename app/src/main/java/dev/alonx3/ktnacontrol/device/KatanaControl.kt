package dev.alonx3.ktnacontrol.device

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.FractionalLevelScale
import dev.alonx3.ktnacontrol.protocol.LevelScale
import dev.alonx3.ktnacontrol.protocol.MemoryDump
import dev.alonx3.ktnacontrol.protocol.MidiBytes
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
 *  - [KatanaParameter], a continuous level over a [LevelScale];
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
    /**
     * How many bytes of data this control's value occupies at [address].
     *
     * Every control in this project is 1 byte except the active channel (`00 01 00 00`),
     * which is 2 per `CURRENT_PRESET_LEN` in `reference/katana-midi-bridge/globals.py:15` —
     * see CLAUDE.md §5.1. Defaulting to 1 keeps every existing subclass unchanged.
     */
    private val byteWidth: Int = 1,
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
        val query = RolandSysEx.get(address, size = byteWidth)
        val reply = awaitRolandReply(link.incoming, address) { link.send(query) }
        val bytes = reply?.data?.takeIf { it.isNotEmpty() } ?: return null
        val value = MidiBytes.decode(bytes)
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
            val message = RolandSysEx.set(address, MidiBytes.encode(accepted, byteWidth))
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
        if (data.data.isEmpty()) return false
        val value = MidiBytes.decode(data.data)
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

    /**
     * Applies the value this control holds inside a memory dump, respecting [byteWidth].
     *
     * ⚠️ **Used to take a single already-looked-up byte, which silently truncated every
     * multi-byte control to its high byte** — for the active channel (the only 2-byte
     * control today, CLAUDE.md §5.1) that turned channels 1..8 into `0` = Panel whenever a
     * stray reply let it reach the dump path (BACKLOG.md, "El estado se desincroniza al
     * cambiar de canal rápido"). Reading all of [byteWidth]'s bytes here, instead of the
     * caller handing over one, is what fixes that for good and for any future multi-byte
     * control (Pitch Shifter / Harmonist's `Pre Delay`, CLAUDE.md §5.2).
     *
     * Same rules as [applyIncoming] —**never sends**, rejects what this control does not
     * accept— but without the address check, because the caller already looked the address up.
     *
     * @return true if the value was taken, false if the dump does not cover every byte of
     *   this control's address, or if this control rejects the decoded value.
     */
    internal fun applyDumpValue(dump: MemoryDump): Boolean {
        val bytes = dump.bytesAt(address, byteWidth) ?: return false
        val accepted = coerce(MidiBytes.decode(bytes)) ?: return false
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
 * A continuous level.
 *
 * The raw byte and the number a person sees are not always the same — see [LevelScale] — so
 * this class has **two** faces and it matters which one a caller uses:
 *  - [state] / [set] work in **raw bytes**, like every other control.
 *  - [level] / [setLevel] work in **what the UI shows**, `0..100`.
 *
 * Anything driving a slider wants the second pair. The raw pair stays public because the
 * diagnostics log and the tests reason in bytes, which is also the only honest way to talk
 * about what the amplifier actually holds.
 */
class KatanaParameter internal constructor(
    address: Address,
    /** How this level's raw byte maps to the number shown. */
    val scale: LevelScale,
    link: KatanaLink,
    scope: CoroutineScope,
    override val debounceMillis: Long,
    onDiagnostic: (String) -> Unit,
    /**
     * Bytes of data at [address]. `1` for every level except Delay Time and Reverb Pre Delay
     * (CLAUDE.md §5.2), which — like the active channel (`byteWidth` on
     * [KatanaEnumParameter]) — need 2 to reach their documented millisecond range.
     */
    byteWidth: Int = 1,
) : KatanaControl(address, link, scope, onDiagnostic, byteWidth) {

    /**
     * What the UI shows right now, or null until the value is known.
     *
     * A plain computed property, **not** a derived `StateFlow`: `stateIn` would launch a
     * collector that lives as long as the scope and that nothing ever cancels — the
     * repository is rebuilt on every reconnect, so those irían acumulándose. Whoever needs to
     * observe changes collects [state] and converts with [scale], which is what the ViewModel
     * does.
     */
    val displayValue: Int?
        get() = state.value?.let(scale::toDisplay)

    /** Sets the level from what the UI shows; the raw byte is derived through [scale]. */
    fun setLevel(display: Int) = set(scale.toRaw(display))

    /**
     * Clamps into the raw range, but lets "off" through untouched: it sits outside the range
     * on purpose (raw `0` while the scale runs `1..101`), and clamping it to `1` would turn a
     * reported "apagado" into "al mínimo".
     */
    override fun coerce(value: Int): Int =
        if (value == scale.offRawValue) value else value.coerceIn(scale.rawRange)
}

/**
 * A continuous level whose displayed value moves in a **fractional** step — 0.5 ms, 0.1 s —
 * that [KatanaParameter]'s `Int`-based [LevelScale] cannot represent. See
 * [dev.alonx3.ktnacontrol.protocol.FractionalLevelScale] for why this is a separate class
 * instead of widening [KatanaParameter] itself: everything else in this project shows a plain
 * `Int`, and this is the minority case.
 *
 * Same two-faces shape as [KatanaParameter] — [state] / [set] in raw bytes, [displayValue] /
 * [setLevel] in what the UI shows — just with `Double` on the display side. Never rejects: like
 * every continuous level, an out-of-range value clamps instead.
 */
class KatanaFractionalParameter internal constructor(
    address: Address,
    /** How this level's raw byte maps to the fractional number shown. */
    val scale: FractionalLevelScale,
    link: KatanaLink,
    scope: CoroutineScope,
    override val debounceMillis: Long,
    onDiagnostic: (String) -> Unit,
) : KatanaControl(address, link, scope, onDiagnostic) {

    /** What the UI shows right now, or null until the value is known. Same reasoning as [KatanaParameter.displayValue]. */
    val displayValue: Double?
        get() = state.value?.let(scale::toDisplay)

    /** Sets the level from what the UI shows; the raw byte is derived through [scale]. */
    fun setLevel(display: Double) = set(scale.toRaw(display))

    override fun coerce(value: Int): Int = value.coerceIn(scale.rawRange)
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
    /**
     * Bytes of data at [address]. `1` for every selector except the active channel
     * (CLAUDE.md §5.1) — see [KatanaControl]'s own `byteWidth`.
     */
    byteWidth: Int = 1,
) : KatanaControl(address, link, scope, onDiagnostic, byteWidth) {

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
