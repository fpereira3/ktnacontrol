package dev.alonx3.ktnacontrol.protocol

/**
 * Reassembles complete `F0…F7` messages out of an arbitrary byte stream (CLAUDE.md §4.1).
 *
 * A single `bulkTransfer` can return a SysEx cut in half, or several of them back to back,
 * so the transport can never assume one read equals one message. Feed it whatever arrives
 * and it hands back only the messages that are actually complete.
 *
 * Pure Kotlin, testable on the JVM. **Stateful and not thread-safe**: one instance per
 * reader, which in practice means one per read loop.
 */
class SysExFramer(private val maxMessageSize: Int = DEFAULT_MAX_MESSAGE_SIZE) {

    private val buffer = ArrayList<Byte>(INITIAL_CAPACITY)
    private var collecting = false

    /** Bytes held from an incomplete message, useful for diagnostics. */
    val pendingBytes: Int get() = buffer.size

    /**
     * Feeds [chunk] and returns every message completed by it — possibly none, possibly
     * several.
     *
     * Bytes outside an `F0…F7` pair are dropped, so noise or a partial message read before
     * the app started listening cannot corrupt the next valid one. An `F0` arriving while a
     * message is still open restarts it: the truncated one is discarded rather than merged
     * into the new one.
     */
    fun feed(chunk: ByteArray): List<ByteArray> {
        val messages = mutableListOf<ByteArray>()
        for (byte in chunk) {
            when (byte.toInt() and 0xFF) {
                START_OF_EXCLUSIVE -> {
                    buffer.clear()
                    buffer.add(byte)
                    collecting = true
                }

                END_OF_EXCLUSIVE -> if (collecting) {
                    buffer.add(byte)
                    messages.add(buffer.toByteArray())
                    buffer.clear()
                    collecting = false
                }

                else -> if (collecting) {
                    buffer.add(byte)
                    // A message that never terminates must not grow without bound.
                    if (buffer.size > maxMessageSize) reset()
                }
            }
        }
        return messages
    }

    /** Drops any partially collected message. */
    fun reset() {
        buffer.clear()
        collecting = false
    }

    companion object {
        const val START_OF_EXCLUSIVE = 0xF0
        const val END_OF_EXCLUSIVE = 0xF7

        /**
         * Ceiling for a single message, with room to spare over anything the amp sends.
         *
         * The worst case is the memory dump: 1920 data bytes plus 14 of overhead (7 header,
         * 1 command, 4 address, 1 checksum, 1 terminator) = 1934, and in practice it arrives
         * split into chunks of ~255 bytes. This leaves better than 2x margin while still
         * bounding a message whose `F7` never comes.
         */
        const val DEFAULT_MAX_MESSAGE_SIZE = 4096

        private const val INITIAL_CAPACITY = 256
    }
}
