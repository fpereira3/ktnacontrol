package dev.alonx3.ktnacontrol.device

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.RolandMessage
import dev.alonx3.ktnacontrol.protocol.RolandSysEx
import dev.alonx3.ktnacontrol.protocol.awaitRolandReply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Local copy of the amp's state, and the single place that writes to it.
 *
 * Deliberately minimal for now — one parameter, reverb level — to settle the read / write /
 * cache pattern before scaling it to the rest of the amplifier (CLAUDE.md §4.2).
 *
 * The cache is fed from **two** directions, which is the whole point:
 *  - what the app writes, applied optimistically;
 *  - what the amp reports on its own, which only happens with edit mode on.
 *
 * **No echo loop**: messages arriving from the amp update the cache and are never sent back.
 * That rule lives here, in the one class allowed to write.
 *
 * Known gap, on purpose: dragging a slider fires dozens of writes and there is no
 * coalescing yet. The debounce of ~100 ms that CLAUDE.md §4.2 asks for comes next.
 */
class KatanaRepository(
    private val link: KatanaLink,
    private val scope: CoroutineScope,
    /**
     * Diagnostics hook: reports what goes out and which addresses come back.
     *
     * Kept on purpose after the reverb investigation. Finding the right address for a
     * parameter took three candidates and only audio told them apart, and the other five
     * effects still have to go through the same thing — see CLAUDE.md §5.
     */
    private val onDiagnostic: (String) -> Unit = {},
) {

    private val _reverbLevel = MutableStateFlow<Int?>(null)

    /** Reverb level, `0..100`, or null until it is read for the first time. */
    val reverbLevel: StateFlow<Int?> = _reverbLevel.asStateFlow()

    private val listener: Job = scope.launch {
        link.incoming.collect { message -> onIncoming(message) }
    }

    /**
     * Asks the amp for the current reverb level and caches it.
     *
     * @return the value read, or null on timeout.
     */
    suspend fun readReverbLevel(): Int? {
        val value = readByte(KatanaAddresses.REVERB_LEVEL)
        if (value != null) _reverbLevel.value = value
        return value
    }

    /**
     * Writes the reverb level, updating the cache **before** the write goes out.
     *
     * Optimistic on purpose: a Roland write is fire-and-forget and never acknowledged (the
     * DT1 semantics already confirmed with edit mode), so there is no confirmation to wait
     * for. Waiting would just make the slider lag behind the finger for nothing.
     */
    fun setReverbLevel(value: Int) {
        val clamped = value.coerceIn(KatanaAddresses.REVERB_LEVEL_RANGE)
        _reverbLevel.value = clamped
        val message =
            RolandSysEx.set(KatanaAddresses.REVERB_LEVEL, byteArrayOf(clamped.toByte()))
        scope.launch {
            // TEMPORARY: answers question (a) — exact bytes, address used, and write result.
            onDiagnostic(
                "SET reverb=$clamped a ${KatanaAddresses.REVERB_LEVEL} " +
                    "[${KatanaAddresses.REVERB_LEVEL.toByteArray().hex()}] → ${message.hex()}"
            )
            val ok = link.send(message)
            onDiagnostic("   sendRaw ${if (ok) "OK" else "FALLÓ"}")
        }
    }

    /** Stops listening. Call when the connection goes away. */
    fun close() {
        listener.cancel()
    }

    private suspend fun readByte(address: Address): Int? {
        val query = RolandSysEx.get(address, size = 1)
        val reply = awaitRolandReply(link.incoming, address) { link.send(query) }
        return reply?.data?.firstOrNull()?.toInt()?.and(0xFF)
    }

    /**
     * Applies a message the amp sent us, if it is one we track.
     *
     * With edit mode on this is how a front-panel knob reaches the UI. Anything else — other
     * addresses, replies to queries we did not make, malformed messages — is ignored here;
     * the diagnostics screen still logs all of it.
     */
    private fun onIncoming(message: ByteArray) {
        val data = RolandSysEx.parse(message) as? RolandMessage.Data ?: return

        if (data.address != KatanaAddresses.REVERB_LEVEL) {
            // TEMPORARY: answers questions (c) and (d) — what address the amp actually
            // reports, compared byte by byte with the one we look for.
            onDiagnostic(
                "entrante ${data.address} [${data.address.toByteArray().hex()}] " +
                    "≠ REVERB_LEVEL ${KatanaAddresses.REVERB_LEVEL} " +
                    "[${KatanaAddresses.REVERB_LEVEL.toByteArray().hex()}]" +
                    diffAgainstReverbLevel(data.address) +
                    ", ${data.data.size} B: ${data.data.hex()}"
            )
            return
        }

        onDiagnostic("entrante COINCIDE con REVERB_LEVEL: ${data.data.hex()}")
        val value = data.data.firstOrNull()?.toInt()?.and(0xFF) ?: return
        _reverbLevel.value = value.coerceIn(KatanaAddresses.REVERB_LEVEL_RANGE)
    }

    /** Which of the 4 address bytes differ, and by how much overall. */
    private fun diffAgainstReverbLevel(address: Address): String {
        val mine = KatanaAddresses.REVERB_LEVEL.toByteArray()
        val theirs = address.toByteArray()
        val differing = theirs.indices.filter { index -> theirs[index] != mine[index] }
        if (differing.isEmpty()) return " (bytes iguales pero equals() dice distinto ⚠️)"
        return " (difieren los bytes $differing; distancia " +
            "${address.value - KatanaAddresses.REVERB_LEVEL.value})"
    }

    private fun ByteArray.hex(): String =
        joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }
}
