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
    const val OVERHEAD = 7 + 1 + Address.SIZE + 1 + 1

    /**
     * Cuántos bytes de datos lleva un mensaje ya formado: todo menos [OVERHEAD].
     *
     * Para contar lo que se ha escrito de verdad sin volver a partir el payload por fuera.
     * Negativo es imposible en un mensaje válido, así que se recorta a 0.
     */
    fun payloadSizeOf(message: ByteArray): Int = (message.size - OVERHEAD).coerceAtLeast(0)

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
     * Cuántos bytes de datos lleva como mucho un SET, y por qué **128** y no otro número.
     *
     * ⚠️ **Es criterio propio, apoyado en un precedente; ninguna fuente dice cuál es el máximo
     * que acepta el amplificador** (CLAUDE.md §5, "Formato `.tsl`" → TBD). Lo que sí hay es el
     * único tamaño de SET masivo que se observa en una fuente de Mk2: el volcado de patch de
     * `reference/FxFloorboard/sysxWriter.cpp:377-390` es una tira de mensajes de **128 bytes de
     * datos cada uno** (12 de cabecera + 128 + checksum + `F7` = 142). Si el editor de PC parte
     * ahí, 128 es el tamaño del que se sabe que alguien habla con este amplificador.
     *
     * ⚠️ **Y NO es un límite del transporte**, aunque CLAUDE.md lo dijera: un SET de 221 bytes
     * son `14 + 221 = 235` bytes de mensaje, que empaquetados en tramas USB-MIDI de 4 bytes dan
     * `ceil(235/3) × 4 = 316` bytes en el cable — **caben de sobra** en los 512 de
     * `wMaxPacketSize` (§4.1). Así que trocear no lo obliga el USB; lo aconseja no ser el
     * primero en probar si el amplificador digiere un SET de 221 bytes de una sentada.
     */
    const val MAX_SET_PAYLOAD = 128

    /**
     * El mismo SET de [set], partido en varios mensajes cuando [data] pasa de [chunkSize].
     *
     * Cada trozo va a **su propia dirección**: la del anterior más su longitud, con el acarreo
     * en base 128 que hace [Address.plus]. Eso importa de verdad aquí — los bloques largos de
     * un `.tsl` cruzan el límite de página: `UserPatch%Fx(1)` empieza en `60 00 01 00` y su
     * segundo trozo cae en `60 00 02 00`, no en `60 00 01 80`, que ni siquiera es una dirección
     * legal.
     *
     * El último trozo lleva **solo lo que queda**, sin relleno: rellenar con ceros escribiría
     * bytes que nadie pidió escribir, en direcciones que pueden ser de otro parámetro.
     *
     * @param chunkSize bytes de datos por mensaje. Ver [MAX_SET_PAYLOAD] para el porqué del
     *   valor por defecto.
     * @return los mensajes en el orden en que hay que mandarlos. Un [data] vacío no da ningún
     *   mensaje: no hay nada que escribir, y un SET sin datos no significa nada.
     */
    fun setChunked(
        address: Address,
        data: ByteArray,
        chunkSize: Int = MAX_SET_PAYLOAD,
    ): List<ByteArray> {
        require(chunkSize > 0) { "el tamaño de trozo debe ser positivo, era $chunkSize" }
        if (data.isEmpty()) return emptyList()
        return data.indices.step(chunkSize).map { offset ->
            val end = minOf(offset + chunkSize, data.size)
            set(address + offset, data.copyOfRange(offset, end))
        }
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

    /**
     * El byte de comando de un mensaje ya formado: [COMMAND_GET] o [COMMAND_SET].
     *
     * [parse] **no lo devuelve** —un GET y un SET dan los dos un [RolandMessage.Data], donde el
     * "dato" de un GET es en realidad su tamaño de 4 bytes— y hasta ahora nadie lo necesitaba,
     * porque el amplificador solo manda SET. Lo necesita quien tiene que *contestar* a un GET:
     * el link offline (CLAUDE.md §4.5).
     *
     * @return el comando, o null si el mensaje es demasiado corto o no es uno de los dos.
     */
    fun commandOf(message: ByteArray): Int? {
        if (message.size <= HEADER.size) return null
        val command = message[HEADER.size].toInt() and 0xFF
        return command.takeIf { it == COMMAND_GET || it == COMMAND_SET }
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
