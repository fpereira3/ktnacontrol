package dev.alonx3.ktnacontrol.protocol

/**
 * Conversions between plain [Int] values and Roland's 7-bit byte arrays (CLAUDE.md §5).
 *
 * Every data and address byte the amp exchanges is in `0x00..0x7F`, so a value spread over N
 * bytes is `Σ byte[i] << 7 * (n - 1 - i)` — base 128, not base 256.
 *
 * Pure Kotlin, no `android.*`: this whole package is testable on the JVM.
 */
object MidiBytes {

    /** Largest value one MIDI byte can hold. */
    const val MAX_BYTE_VALUE = 0x7F

    /** Bits carried per byte. */
    const val BITS_PER_BYTE = 7

    /** True when every byte of [bytes] fits in 7 bits. */
    fun isSevenBit(bytes: ByteArray): Boolean =
        bytes.all { byte -> byte.toInt() and 0xFF <= MAX_BYTE_VALUE }

    /**
     * Encodes [value] as [length] 7-bit bytes, most significant first.
     *
     * `encode(16, 4)` gives `00 00 00 10`; `encode(1920, 4)` gives `00 00 0F 00`.
     */
    fun encode(value: Int, length: Int): ByteArray {
        require(length > 0) { "length debe ser > 0, era $length" }
        require(value >= 0) { "value no puede ser negativo, era $value" }
        val capacity = 1 shl (BITS_PER_BYTE * length)
        require(length >= Int.SIZE_BITS / BITS_PER_BYTE || value < capacity) {
            "$value no cabe en $length bytes de 7 bits"
        }

        return ByteArray(length) { index ->
            val shift = BITS_PER_BYTE * (length - 1 - index)
            ((value ushr shift) and MAX_BYTE_VALUE).toByte()
        }
    }

    /**
     * Decodes a 7-bit byte array back into an [Int], most significant byte first.
     *
     * The inverse of [encode]; bits above the 7th of any byte are ignored rather than
     * rejected, so a stray status byte cannot corrupt the result silently.
     */
    fun decode(bytes: ByteArray): Int =
        bytes.fold(0) { accumulator, byte ->
            (accumulator shl BITS_PER_BYTE) or (byte.toInt() and MAX_BYTE_VALUE)
        }
}
