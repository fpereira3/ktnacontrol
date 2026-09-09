package dev.alonx3.ktnacontrol.protocol.tsl

import dev.alonx3.ktnacontrol.protocol.MemoryDump
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Un preset leído de un `.tsl`, ya con sus bytes colocados en sus direcciones.
 *
 * **El puente con el resto del proyecto es [memory].** Un `.tsl` y un dump de memoria acaban en
 * la misma forma —trozos de bytes con una dirección base cada uno— así que en cuanto el JSON
 * está colocado, `AmpState.from(preset.memory)` lee exactamente igual que lee el amplificador
 * en vivo. Ni `AmpState` ni `MemoryDump` han tenido que cambiar para esto.
 */
data class TslPreset(
    /** El nombre guardado en `UserPatch%PatchName`, ya sin el relleno de espacios. */
    val name: String,
    /** La nota libre del usuario (`memo`), o vacía. Es el único campo de texto del formato. */
    val memo: String,
    /** Los bloques colocados en sus direcciones, listos para leer como si fueran un dump. */
    val memory: MemoryDump,
    /** Qué no se pudo cargar y por qué — ver [TslUnavailable]. */
    val unavailable: List<TslUnavailable>,
    /**
     * El `paramSet` original, clave → bytes en hexadecimal, **tal cual venía en el fichero**.
     *
     * ⚠️ **Es lo que hace que guardar un preset editado no pierda nada.** [memory] solo tiene lo
     * que se pudo colocar con confianza: los bloques en disputa (`Contour`,
     * `GafcExp1AsgnMinMax`) y cualquier clave desconocida quedan fuera a propósito. Sin esta
     * copia, abrir un preset y volver a guardarlo **borraría** esos 82 bytes, que es justo el
     * fallo que la política de "no cargar lo dudoso" quería evitar en la otra dirección.
     *
     * Así que al exportar se escribe desde [memory] lo que se entiende, y de aquí, **verbatim**,
     * lo que no. Ver `TslWriter`.
     */
    val rawParamSet: Map<String, List<String>> = emptyMap(),
) {
    /** Cuántos bytes se cargaron de verdad. */
    val loadedBytes: Int get() = memory.dataByteCount
}

/**
 * Algo que el fichero no trae, o que trae y no se puede colocar con confianza.
 *
 * Existe como valor y no como excepción porque **no es un error**: un `.tsl` al que le falta
 * un bloque sigue siendo perfectamente utilizable para todo lo demás, y la respuesta correcta
 * es enseñar "—" en ese campo, no negarse a abrir el fichero (CLAUDE.md §6: los errores se
 * modelan como tipos, no como excepciones que cruzan capas).
 */
data class TslUnavailable(
    /** La clave afectada, o una descripción cuando es algo que el formato entero no guarda. */
    val what: String,
    val reason: String,
)

/** Lo que sale de leer un `.tsl`. Los fallos son valores, no excepciones (CLAUDE.md §6). */
sealed interface TslParseResult {
    /** El fichero se leyó. Puede traer más de un preset: `data[0]` es una lista. */
    data class Parsed(val presets: List<TslPreset>) : TslParseResult

    /** El fichero no es un `.tsl` de Katana Mk2 utilizable; [reason] ya es legible. */
    data class Invalid(val reason: String) : TslParseResult
}

/**
 * Lee un `.tsl` de Boss Tone Studio (CLAUDE.md §5, "Formato `.tsl`").
 *
 * ⚠️ **Un `.tsl` es JSON de texto, no un volcado binario.** No hay offsets fijos dentro del
 * fichero: la unidad de direccionamiento es **el nombre de la clave**, y [TslBlockMap] es lo
 * que traduce cada clave a una dirección del amplificador.
 *
 * ⚠️ **No todo `.tsl` es de Katana.** El otro fichero de `reference/`, `default.tsl`, tiene
 * `device: "GT"` y un esquema completamente distinto (`liveSetData` + `patchList`). Comprobar
 * `device` antes de parsear no es paranoia: son formatos que solo comparten la extensión, y
 * parsear uno como el otro daría basura en vez de un error.
 */
object TslParser {

    /** El único valor de `device` que este parser entiende. */
    const val EXPECTED_DEVICE = "KATANA MkII"

    /**
     * La revisión que trae el bloque extra del firmware 2 (`UserPatch%Patch_Mk2V2`).
     *
     * `reference/TuxKatana/lib/tsl.py:26` anota que `"0002"` es lo que *"seem to tell the
     * `UserPatch%Patch_Mk2V2` presence"*. No se usa para rechazar nada: una revisión distinta
     * se lee igual y lo que falte se reporta como no disponible.
     */
    const val FORMAT_REV_MK2V2 = "0002"

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    /**
     * @param text el contenido completo del fichero.
     * @return los presets, o el motivo por el que no se pudo leer.
     */
    fun parse(text: String): TslParseResult {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
            return TslParseResult.Invalid("el fichero no es JSON válido: ${it.message}")
        }

        val device = root["device"]?.stringOrNull()
            ?: return TslParseResult.Invalid("al fichero le falta el campo \"device\"")
        if (device != EXPECTED_DEVICE) {
            // El caso real que esto ataja: un `.tsl` de la serie GT, que usa las mismas
            // extensión y envoltura pero otro esquema entero.
            return TslParseResult.Invalid(
                "este .tsl es de «$device», no de «$EXPECTED_DEVICE»: formatos distintos que " +
                    "solo comparten la extensión"
            )
        }

        val formatRev = root["formatRev"]?.stringOrNull()
        val fileName = root["name"]?.stringOrNull().orEmpty()

        val data = root["data"] as? JsonArray
            ?: return TslParseResult.Invalid("al fichero le falta el campo \"data\"")
        val entries = data.firstOrNull() as? JsonArray
            ?: return TslParseResult.Invalid("\"data\" está vacío: el fichero no trae presets")

        val presets = entries.mapIndexedNotNull { index, entry ->
            (entry as? JsonObject)?.let { readPreset(it, fileName, formatRev, index) }
        }
        if (presets.isEmpty()) {
            return TslParseResult.Invalid("\"data\" no trae ningún preset legible")
        }
        return TslParseResult.Parsed(presets)
    }

    private fun readPreset(
        entry: JsonObject,
        fileName: String,
        formatRev: String?,
        index: Int,
    ): TslPreset {
        val paramSet = entry["paramSet"] as? JsonObject ?: JsonObject(emptyMap())
        val memo = (entry["memo"] as? JsonObject)?.get("memo")?.stringOrNull().orEmpty()

        val chunks = mutableListOf<MemoryDump.Chunk>()
        val unavailable = mutableListOf<TslUnavailable>()

        // Se recorren los bloques que **conocemos**, no las claves del fichero: una clave que
        // no esté en el mapa no tiene dirección, así que no hay dónde ponerla.
        TslBlockMap.blocks.forEach { block ->
            val raw = paramSet[block.key] as? JsonArray
            if (raw == null) {
                unavailable += TslUnavailable(block.key, "el fichero no trae esta clave")
                return@forEach
            }
            if (!block.trusted) {
                unavailable += TslUnavailable(
                    block.key,
                    "dirección en disputa entre fuentes, no se carga para no mostrar un valor " +
                        "equivocado" + (block.note?.let { " ($it)" } ?: ""),
                )
                return@forEach
            }
            val bytes = raw.toBytesOrNull()
            if (bytes == null) {
                unavailable += TslUnavailable(block.key, "sus bytes no son hexadecimal de 2 dígitos")
                return@forEach
            }
            // El tamaño del mapa es el esperado; el que manda es el del fichero. Truncar o
            // rellenar a ciegas sería inventar bytes que nadie escribió.
            if (bytes.size != block.size) {
                unavailable += TslUnavailable(
                    block.key,
                    "trae ${bytes.size} bytes y se esperaban ${block.size}: se cargan los del " +
                        "fichero, pero puede ser de otra revisión del formato",
                )
            }
            chunks += MemoryDump.Chunk(block.address, bytes)
        }

        // Claves del fichero que no sabemos colocar: se avisan en vez de ignorarlas en silencio.
        paramSet.keys.filter { TslBlockMap.forKey(it) == null }.forEach { key ->
            unavailable += TslUnavailable(key, "clave desconocida: no hay dirección para ella")
        }

        if (formatRev != null && formatRev != FORMAT_REV_MK2V2) {
            unavailable += TslUnavailable(
                "formatRev $formatRev",
                "revisión distinta de la esperada ($FORMAT_REV_MK2V2): puede faltar el bloque " +
                    "del firmware 2",
            )
        }
        TslBlockMap.KNOWN_GAPS.forEach { gap ->
            unavailable += TslUnavailable("(formato)", gap)
        }

        val memory = MemoryDump(chunks)
        val name = memory.presetName()
            ?: fileName.takeIf { it.isNotBlank() }
            ?: "Preset ${index + 1}"

        return TslPreset(
            name = name,
            memo = memo,
            memory = memory,
            unavailable = unavailable,
            rawParamSet = paramSet.mapValues { (_, value) ->
                (value as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                    .orEmpty()
            },
        )
    }

    /**
     * El nombre guardado en `60 00 00 00`: 16 bytes ASCII rellenados con espacios.
     *
     * Devuelve null si el bloque no vino, para que el llamador pueda caer al nombre del fichero
     * en vez de enseñar una cadena vacía.
     */
    private fun MemoryDump.presetName(): String? {
        val block = TslBlockMap.forKey(TslBlockMap.NAME_KEY) ?: return null
        val bytes = bytesAt(block.address, block.size) ?: return null
        return bytes
            .map { (it.toInt() and 0x7F).toChar() }
            .joinToString("")
            .trimEnd()
            .takeIf { it.isNotBlank() }
    }

    /** `["4B","41",…]` → bytes. Null si algo no es un hexadecimal de dos dígitos. */
    private fun JsonArray.toBytesOrNull(): ByteArray? {
        val bytes = ByteArray(size)
        forEachIndexed { index, element ->
            val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            val value = text.trim().toIntOrNull(radix = 16) ?: return null
            if (value !in 0..0xFF) return null
            bytes[index] = value.toByte()
        }
        return bytes
    }

    private fun kotlinx.serialization.json.JsonElement.stringOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}
