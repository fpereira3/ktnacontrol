package dev.alonx3.ktnacontrol.protocol

/**
 * La memoria de un preset, **escribible**: un mapa dirección → byte.
 *
 * Es la gemela mutable de [MemoryDump] (CLAUDE.md §4.5). El dump es lo que el amplificador
 * contestó y es de solo lectura; esto es lo que se está editando, venga de un `.tsl`, de un
 * volcado del amplificador o de un preset nuevo en blanco.
 *
 * ⚠️ **Guarda bytes, no parámetros, y esa es toda la gracia.** Un `.tsl` son 1141 bytes y
 * `AmpState` entiende 24 valores: si la edición pasara por el modelo de dominio, cambiar el
 * Gain de un preset importado y volver a guardarlo **borraría** los parámetros internos de
 * Mod/FX, el EQ y la cadena, porque `AmpState` no los modela. Aquí lo que la app no entiende
 * se conserva intacto por la vía más simple que hay: nunca se toca.
 *
 * Kotlin puro, sin `android.*`, con tests JVM.
 */
class MemoryImage private constructor(
    /** Dirección (como número base 128, [Address.value]) → byte `0x00`..`0xFF`. */
    private val bytes: MutableMap<Int, Int>,
) {

    /** Cuántos bytes tiene la imagen. */
    val size: Int get() = bytes.size

    /** El byte en [address], o null si la imagen no lo tiene. */
    fun byteAt(address: Address): Int? = bytes[address.value]

    /**
     * Los [width] bytes consecutivos desde [address], o null si falta alguno.
     *
     * Todo o nada, por la misma razón que [MemoryDump.bytesAt]: un control de varios bytes
     * tiene un solo valor repartido, y devolver una lectura parcial se inventaría un valor.
     */
    fun bytesAt(address: Address, width: Int): ByteArray? {
        val result = ByteArray(width)
        for (offset in 0 until width) {
            result[offset] = (bytes[(address + offset).value] ?: return null).toByte()
        }
        return result
    }

    /** Escribe [data] a partir de [address], creando las direcciones que no existieran. */
    fun write(address: Address, data: ByteArray) {
        data.forEachIndexed { offset, byte ->
            bytes[(address + offset).value] = byte.toInt() and 0xFF
        }
    }

    /** Una copia independiente, para poder editar sin tocar el original. */
    fun copy(): MemoryImage = MemoryImage(HashMap(bytes))

    /**
     * La misma memoria en forma de [MemoryDump], **agrupada en tramos contiguos**.
     *
     * Es el puente hacia todo lo que ya existe: `AmpState.from(image.toDump())` lee un preset
     * de fichero exactamente igual que lee el amplificador en vivo, sin que ninguno de los dos
     * tipos haya tenido que cambiar.
     *
     * Los huecos entre bloques del `.tsl` son reales (CLAUDE.md §5, "Formato `.tsl`"), así que
     * agrupar por contigüidad es lo que reproduce la forma que el amplificador manda de verdad:
     * varios trozos, cada uno con su base.
     */
    fun toDump(): MemoryDump {
        if (bytes.isEmpty()) return MemoryDump(emptyList())
        val ordered = bytes.keys.sorted()
        val chunks = mutableListOf<MemoryDump.Chunk>()
        var runStart = ordered.first()
        var previous = runStart
        val current = mutableListOf<Byte>()
        current += bytes.getValue(runStart).toByte()

        for (key in ordered.drop(1)) {
            if (key == previous + 1) {
                current += bytes.getValue(key).toByte()
            } else {
                chunks += MemoryDump.Chunk(Address.fromValue(runStart), current.toByteArray())
                current.clear()
                runStart = key
                current += bytes.getValue(key).toByte()
            }
            previous = key
        }
        chunks += MemoryDump.Chunk(Address.fromValue(runStart), current.toByteArray())
        return MemoryDump(chunks)
    }

    companion object {
        /** Una imagen sin ningún byte. Todo lo que se le pida devuelve null. */
        fun empty(): MemoryImage = MemoryImage(HashMap())

        /** Copia los bytes de un dump — el camino desde un `.tsl` o desde el amplificador. */
        fun from(dump: MemoryDump): MemoryImage {
            val image = MemoryImage(HashMap())
            // En orden inverso para que, si dos trozos se solaparan, gane el primero — la
            // misma regla que MemoryDump.byteAt, que documenta "el primero gana".
            dump.chunks.reversed().forEach { chunk -> image.write(chunk.base, chunk.data) }
            return image
        }
    }
}
