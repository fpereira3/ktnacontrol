package dev.alonx3.ktnacontrol.protocol

/**
 * The amp's memory as it answered a dump request, indexed so any address can be looked up.
 *
 * **The dump is not one block.** Asking for `60 00 00 00` with a size of `00 00 0F 00` gets
 * back several Roland messages, each with its own base address and its own length: the
 * amplifier skips the gaps where no parameters live. `reference/TuxKatana/HOW.md` traces 5
 * messages of 241 data bytes plus one of 139; the real Mk2 answered with 8 messages and 1860
 * data bytes. **Neither the count nor the total is fixed** — it depends on which ranges are
 * occupied — so nothing here assumes either.
 *
 * That is also why this is a lookup and not an offset into a flat array: a parameter's
 * address either falls inside one of the chunks that came back, or it did not come back at
 * all, and "did not come back" is a normal answer rather than an error.
 *
 * Address arithmetic is base-128 ([Address.value]), which is exactly how the amp lays the
 * bytes out: consecutive data bytes are consecutive 7-bit addresses.
 */
class MemoryDump(
    /** The pieces the amp sent, in arrival order. */
    val chunks: List<Chunk>,
) {

    /**
     * One message's worth of the dump: where it starts and the bytes that followed.
     *
     * Deliberately **not** a `data class`: it holds a [ByteArray], whose `equals` is identity,
     * so the generated `equals` would be a trap for anyone comparing two chunks.
     */
    class Chunk(val base: Address, val data: ByteArray) {

        /** Addresses covered by this chunk: `base` .. `base + data.size - 1`. */
        fun contains(address: Address): Boolean {
            val offset = address - base
            return offset >= 0 && offset < data.size
        }

        /** The byte this chunk holds for [address], or null if it does not cover it. */
        fun byteAt(address: Address): Int? {
            val offset = address - base
            return if (offset in data.indices) data[offset].toInt() and 0xFF else null
        }

        override fun toString(): String = "$base: ${data.size} B"
    }

    /** Total data bytes across every chunk. */
    val dataByteCount: Int = chunks.sumOf { it.data.size }

    /**
     * The byte the amp holds at [address], or null when the dump did not cover it.
     *
     * With overlapping chunks the first one wins. The amp has never sent overlapping ranges,
     * but "first wins" is a decision rather than an accident.
     */
    fun byteAt(address: Address): Int? =
        chunks.firstNotNullOfOrNull { chunk -> chunk.byteAt(address) }

    /** Every address of [addresses] the dump covers, with its byte. Misses are left out. */
    fun valuesAt(addresses: Iterable<Address>): Map<Address, Int> =
        addresses.mapNotNull { address -> byteAt(address)?.let { address to it } }.toMap()

    override fun toString(): String =
        "MemoryDump(${chunks.size} trozos, $dataByteCount B: ${chunks.joinToString()})"

    companion object {
        /** Builds a dump from the parsed Roland messages the amp answered with. */
        fun from(messages: List<RolandMessage.Data>): MemoryDump =
            MemoryDump(messages.map { Chunk(it.address, it.data) })
    }
}
