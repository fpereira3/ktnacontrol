package dev.alonx3.ktnacontrol.protocol

/**
 * Result of parsing an incoming Roland message. Errors are values, not exceptions crossing
 * layers (CLAUDE.md §6).
 */
sealed interface RolandMessage {

    /**
     * A well-formed message whose checksum matched.
     *
     * Not a `data class`: it holds a [ByteArray], whose identity-based `equals` would make
     * the generated one misleading.
     */
    class Data(val address: Address, val data: ByteArray) : RolandMessage

    /** Something was off; [reason] is already human-readable. */
    data class Invalid(val reason: String) : RolandMessage
}

/**
 * Builds and parses Boss Katana MK2 SysEx messages (CLAUDE.md §5).
 *
 * ```
 * F0 41 00 00 00 00 33  <cmd>  <address:4>  <size:4 | data:N>  <checksum>  F7
 * ```
 *
 * Pure Kotlin: this knows nothing about USB packet framing, which is `usb/`'s job.
 *
 * Address map and checksum algorithm from
 * `reference/katana-midi-bridge/doc/katana_sysex.txt` (Steven Hirsch, v1.7), cross-checked
 * against the byte-level traces in `reference/TuxKatana/HOW.md`.
 */
object RolandSysEx {

    /** `F0 41 00 00 00 00 33` — SysEx start, Roland, device id, Katana model id. */
    val HEADER = byteArrayOf(0xF0.toByte(), 0x41, 0x00, 0x00, 0x00, 0x00, 0x33)

    /** Query a range of parameters. */
    const val COMMAND_GET = 0x11

    /** Write parameters. Also what the amp uses to answer a [COMMAND_GET]. */
    const val COMMAND_SET = 0x12

    const val END_OF_EXCLUSIVE = 0xF7

    /** Header + command + address + checksum + terminator, i.e. everything but the payload. */
    private const val OVERHEAD = 7 + 1 + Address.SIZE + 1 + 1

    /**
     * Builds a query: "send me [size] bytes starting at [address]".
     *
     * For the device name that is `get(Address(0x10, 0, 0, 0), 16)`, which yields
     * `F0 41 00 00 00 00 33 11 10 00 00 00 00 00 00 10 60 F7`.
     */
    fun get(address: Address, size: Int): ByteArray =
        build(COMMAND_GET, address, MidiBytes.encode(size, Address.SIZE))

    /** Builds a write of [data] at [address]. */
    fun set(address: Address, data: ByteArray): ByteArray {
        require(MidiBytes.isSevenBit(data)) { "los datos deben ser bytes de 7 bits" }
        return build(COMMAND_SET, address, data)
    }

    /**
     * Roland's checksum over the address and payload: `(128 - (sum % 128)) % 128`.
     *
     * The outer modulo matters — when the sum is already a multiple of 128 the checksum is
     * `0x00`, not `0x80`, which would not even be a legal MIDI data byte.
     */
    fun checksum(addressAndPayload: ByteArray): Int {
        val sum = addressAndPayload.fold(0) { accumulator, byte ->
            accumulator + (byte.toInt() and 0xFF)
        }
        return (128 - (sum % 128)) % 128
    }

    /**
     * Parses a complete `F0…F7` message, validating the header, the command and the
     * checksum.
     *
     * Takes a single message: splitting a byte stream into messages is `SysExFramer`'s job.
     */
    fun parse(message: ByteArray): RolandMessage {
        if (message.size < OVERHEAD) {
            return RolandMessage.Invalid(
                "mensaje demasiado corto: ${message.size} bytes, mínimo $OVERHEAD"
            )
        }
        if (!message.copyOfRange(0, HEADER.size).contentEquals(HEADER)) {
            return RolandMessage.Invalid("cabecera Roland/Katana incorrecta")
        }
        if ((message.last().toInt() and 0xFF) != END_OF_EXCLUSIVE) {
            return RolandMessage.Invalid("el mensaje no termina en F7")
        }

        val command = message[HEADER.size].toInt() and 0xFF
        if (command != COMMAND_GET && command != COMMAND_SET) {
            return RolandMessage.Invalid("comando desconocido: 0x%02X".format(command))
        }

        // Everything between the command and the checksum: address + payload.
        val bodyStart = HEADER.size + 1
        val bodyEnd = message.size - 2
        val body = message.copyOfRange(bodyStart, bodyEnd)

        val expected = checksum(body)
        val received = message[message.size - 2].toInt() and 0xFF
        if (expected != received) {
            return RolandMessage.Invalid(
                "checksum inválido: llegó 0x%02X, esperado 0x%02X".format(received, expected)
            )
        }

        return RolandMessage.Data(
            address = Address.fromBytes(body.copyOfRange(0, Address.SIZE)),
            data = body.copyOfRange(Address.SIZE, body.size),
        )
    }

    private fun build(command: Int, address: Address, payload: ByteArray): ByteArray {
        val body = address.toByteArray() + payload
        return HEADER +
            command.toByte() +
            body +
            checksum(body).toByte() +
            END_OF_EXCLUSIVE.toByte()
    }
}
