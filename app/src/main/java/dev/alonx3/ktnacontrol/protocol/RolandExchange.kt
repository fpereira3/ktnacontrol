package dev.alonx3.ktnacontrol.protocol

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
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
