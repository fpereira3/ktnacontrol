package dev.alonx3.ktnacontrol.protocol.tsl

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.MemoryImage
import dev.alonx3.ktnacontrol.protocol.RolandSysEx

/** Los mensajes que escriben un bloque del `.tsl` en el amplificador. */
data class TslBlockWrite(
    val block: TslBlock,
    /** Los SET ya construidos, en el orden en que hay que mandarlos. */
    val messages: List<ByteArray>,
    /** Cuántos bytes de datos van en total, sumando los trozos. */
    val dataBytes: Int,
) {
    val key: String get() = block.key
    val address: Address get() = block.address

    /** Más de uno significa que el bloque no cabía en un solo SET y se troceó. */
    val chunked: Boolean get() = messages.size > 1
}

/** Lo que hay que mandar para escribir un preset entero, y lo que se queda fuera. */
data class TslTransferPlan(
    val writes: List<TslBlockWrite>,
    /** Bloques que no se mandan, con el motivo. Un valor, no una excepción (CLAUDE.md §6). */
    val skipped: List<TslOmission>,
) {
    val messageCount: Int get() = writes.sumOf { it.messages.size }
    val dataBytes: Int get() = writes.sumOf { it.dataBytes }

    /** Todos los mensajes seguidos, que es como salen por el cable. */
    val messages: List<ByteArray> get() = writes.flatMap { it.messages }
}

/**
 * Convierte un preset —venga de un `.tsl` o de una edición offline— en los SET que lo escriben
 * en el amplificador (CLAUDE.md §5, "Formato `.tsl`").
 *
 * Es el camino inverso a [TslParser] pero hacia el cable, no hacia un fichero: los bytes de un
 * `.tsl` **son los mismos que viajan por SysEx**, sin transformar, así que escribir un preset
 * es "por cada clave, un SET a su dirección con sus bytes" — con el matiz de que dos claves no
 * caben en un solo SET.
 *
 * ⚠️ **`UserPatch%Fx(1)` y `UserPatch%Fx(2)` miden 221 bytes** y se parten en dos mensajes de
 * 128 + 93 con [RolandSysEx.setChunked]; el segundo trozo cruza el límite de página
 * (`60 00 01 00` + 128 = `60 00 02 00`), que es exactamente el acarreo en base 128 de
 * [Address.plus]. Ver [RolandSysEx.MAX_SET_PAYLOAD] para de dónde sale el 128 y para el dato
 * que corrige a CLAUDE.md: trocear no lo obliga el tamaño del paquete USB.
 *
 * ⚠️ **Esto solo construye los mensajes; mandarlos es destructivo.** Sobrescribe el búfer de
 * edición del amplificador —lo que el usuario tuviera sin guardar se pierde— y ninguna fuente
 * documenta una confirmación. Las cautelas están en `KatanaRepository.sendPreset`.
 *
 * ⚠️ **Nada de esto está probado contra el amplificador**, empezando por lo más básico: si
 * acepta un SET de 128 bytes de datos.
 */
object TslTransfer {

    /** Bytes de datos por mensaje. Ver [RolandSysEx.MAX_SET_PAYLOAD]. */
    const val CHUNK_SIZE = RolandSysEx.MAX_SET_PAYLOAD

    /**
     * Los SET que escriben [image] en el amplificador, bloque a bloque y en el orden del mapa.
     *
     * **Un bloque se manda solo si la imagen tiene sus bytes enteros.** La misma regla de "todo
     * o nada" de `MemoryImage.bytesAt`: media escritura dejaría el resto del bloque con lo que
     * hubiera antes, mezclando dos presets en uno sin decírselo a nadie.
     *
     * **Los bloques [TslConfidence.DISPUTED] no se mandan nunca**, ni aunque la imagen los
     * traiga. Es la misma política que al importar y al escribir un `.tsl`, y aquí es donde más
     * cara sale equivocarse: un desfase de dos direcciones en un Contour no muestra un número
     * raro, **escribe encima de otro parámetro del amplificador**.
     *
     * ⚠️ Los bloques que se omiten **se quedan como estaban en el amplificador**: el preset que
     * acaba sonando es el del fichero mezclado con los restos del anterior en esas direcciones.
     * Por eso [TslTransferPlan.skipped] no es diagnóstico interno, es algo que hay que contarle
     * al usuario.
     */
    fun plan(image: MemoryImage, chunkSize: Int = CHUNK_SIZE): TslTransferPlan {
        val writes = mutableListOf<TslBlockWrite>()
        val skipped = mutableListOf<TslOmission>()

        TslBlockMap.blocks.forEach { block ->
            if (!block.trusted) {
                skipped += TslOmission(
                    key = block.key,
                    reason = "dirección en disputa entre fuentes: no se escribe para no pisar " +
                        "otro parámetro" + (block.note?.let { " ($it)" } ?: ""),
                )
                return@forEach
            }
            val bytes = image.bytesAt(block.address, block.size)
            if (bytes == null) {
                skipped += TslOmission(
                    key = block.key,
                    reason = "el preset no trae los ${block.size} bytes de este bloque",
                )
                return@forEach
            }
            writes += TslBlockWrite(
                block = block,
                messages = RolandSysEx.setChunked(block.address, bytes, chunkSize),
                dataBytes = bytes.size,
            )
        }
        return TslTransferPlan(writes = writes, skipped = skipped)
    }
}
