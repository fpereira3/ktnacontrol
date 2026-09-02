package dev.alonx3.ktnacontrol.device

import kotlinx.coroutines.flow.Flow

/**
 * What [KatanaRepository] needs from a connection, and nothing more.
 *
 * The repository talks to this instead of to `usb/` directly, so it can be tested against a
 * fake on the JVM (CLAUDE.md §6) — and so a change of transport stays confined to `usb/`,
 * which is exactly what made the move off `MidiManager` cheap (§4.1).
 */
interface KatanaLink {

    /**
     * Complete `F0…F7` messages coming from the amp, including the ones it volunteers on
     * its own when edit mode is on.
     *
     * Must be a **shared** stream with a single underlying reader: several collectors on a
     * cold flow would each open their own read loop and steal each other's messages.
     */
    val incoming: Flow<ByteArray>

    /**
     * Sends one complete `F0…F7` message.
     *
     * @return true if it went out, false if the write failed.
     */
    suspend fun send(message: ByteArray): Boolean
}
