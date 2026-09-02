package dev.alonx3.ktnacontrol.protocol

/**
 * A Roland parameter address: 4 bytes of 7 bits each (CLAUDE.md §5).
 *
 * Addresses are base-128 numbers, so `0x00 0x00 0x00 0x7F` plus one is
 * `0x00 0x00 0x01 0x00` — [plus] does that carry correctly, which is exactly the arithmetic
 * needed to walk a memory dump in chunks.
 */
data class Address(
    val byte0: Int,
    val byte1: Int,
    val byte2: Int,
    val byte3: Int,
) {
    init {
        listOf(byte0, byte1, byte2, byte3).forEachIndexed { index, byte ->
            require(byte in 0..MidiBytes.MAX_BYTE_VALUE) {
                "el byte $index de la dirección debe estar en 0x00..0x7F, era $byte"
            }
        }
    }

    /** The address as a plain number, so offsets can be added to it. */
    val value: Int
        get() = MidiBytes.decode(toByteArray())

    fun toByteArray(): ByteArray = byteArrayOf(
        byte0.toByte(),
        byte1.toByte(),
        byte2.toByte(),
        byte3.toByte(),
    )

    /** Adds a byte offset, carrying in base 128. */
    operator fun plus(offset: Int): Address = fromValue(value + offset)

    /** Distance in bytes between two addresses. */
    operator fun minus(other: Address): Int = value - other.value

    /** Hex, the way the protocol docs write it: `10 00 00 00`. */
    override fun toString(): String =
        toByteArray().joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }

    companion object {
        /** Number of bytes in an address. */
        const val SIZE = 4

        fun fromBytes(bytes: ByteArray): Address {
            require(bytes.size == SIZE) {
                "una dirección son $SIZE bytes, llegaron ${bytes.size}"
            }
            return Address(
                byte0 = bytes[0].toInt() and 0xFF,
                byte1 = bytes[1].toInt() and 0xFF,
                byte2 = bytes[2].toInt() and 0xFF,
                byte3 = bytes[3].toInt() and 0xFF,
            )
        }

        fun fromValue(value: Int): Address = fromBytes(MidiBytes.encode(value, SIZE))
    }
}
