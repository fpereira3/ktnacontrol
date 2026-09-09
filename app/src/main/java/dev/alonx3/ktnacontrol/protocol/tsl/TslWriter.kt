package dev.alonx3.ktnacontrol.protocol.tsl

import dev.alonx3.ktnacontrol.protocol.ChainBlock
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.MemoryImage
import dev.alonx3.ktnacontrol.protocol.PresetSave
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Qué se dejó fuera del `.tsl` escrito, y por qué. Un valor, no una excepción (CLAUDE.md §6). */
data class TslOmission(
    /** La clave que no se escribió. */
    val key: String,
    val reason: String,
)

/** Lo que salió de serializar: el JSON y qué se quedó por el camino. */
data class TslWriteResult(
    val json: String,
    /** Claves que **no** aparecen en el fichero. Ver la política en [TslWriter]. */
    val omitted: List<TslOmission>,
    /** Claves copiadas del fichero de origen sin interpretarlas. */
    val passedThrough: List<String>,
)

/**
 * Escribe un `.tsl` de Boss Tone Studio: el camino inverso a [TslParser].
 *
 * ## La política de lo que no es de fiar
 *
 * Un `.tsl` son 22 bloques y la app no entiende todos con la misma confianza (ver
 * [TslConfidence]). Al escribir hay tres casos y **cada uno tiene una decisión explícita**,
 * no un valor por defecto silencioso:
 *
 * | Caso | Qué se hace |
 * | --- | --- |
 * | La imagen tiene los bytes del bloque | **se escriben** |
 * | No los tiene, pero el fichero de origen sí traía la clave | **se copia verbatim** |
 * | Ni una cosa ni la otra | **se omite la clave** y se avisa |
 *
 * ⚠️ **La copia verbatim es la que evita el fallo grave**, y es lo que permite editar sin
 * perder nada: los cuatro bloques en disputa (los tres `Contour` y `GafcExp1AsgnMinMax`, 82
 * bytes) no se cargan a propósito al importar, así que sin este paso, abrir un preset y volver
 * a guardarlo los **borraría**. Se copian sin mirarlos: no hace falta saber a qué dirección van
 * para saber que pertenecen a este preset.
 *
 * ⚠️ **Y omitir es deliberado, no pereza: la alternativa era escribir ceros y es peor.** Un
 * bloque de Contour a ceros es un Shape 1 con Freq Shift -50 — un valor perfectamente legal,
 * indistinguible de uno que el usuario eligiera, que al cargarlo en el amplificador le
 * **cambiaría el sonido en silencio**. Una clave que falta, como mucho, hace que el lector se
 * queje. Entre un fallo ruidoso y uno callado, el ruidoso. Es la misma regla que ya rige la
 * importación (CLAUDE.md §5, "Formato `.tsl`") aplicada en la otra dirección.
 *
 * ⚠️ **Sin probar contra Boss Tone Studio**: ninguna fuente dice si BTS acepta un `.tsl` con
 * claves ausentes. Lo que sí es seguro es que esta app lo relee sin problema, porque una clave
 * que falta ya es un caso normal para [TslParser].
 */
object TslWriter {

    /** El `device` que identifica un `.tsl` de Katana Mk2. El mismo que exige [TslParser]. */
    const val DEVICE = TslParser.EXPECTED_DEVICE

    /** La revisión de formato que se escribe. La del firmware 2, que es la que trae todo. */
    const val FORMAT_REV = TslParser.FORMAT_REV_MK2V2

    private val json = Json { prettyPrint = false }

    /**
     * Serializa [image] como un `.tsl` de un solo preset.
     *
     * @param name el nombre del preset. Se escribe **también** en `UserPatch%PatchName`
     *   (`60 00 00 00`), que es donde el amplificador lo lee de verdad — el `name` de la
     *   envoltura es solo la etiqueta legible del fichero.
     * @param source de dónde salió el preset, si salió de un fichero: sus claves se usan para
     *   la copia verbatim de lo que la imagen no cubre. Null al exportar del amplificador o al
     *   crear uno desde cero, y entonces no hay nada que copiar.
     */
    fun write(
        image: MemoryImage,
        name: String,
        memo: String = "",
        source: TslPreset? = null,
    ): TslWriteResult {
        // El nombre manda sobre lo que hubiera en la imagen: es lo que el usuario acaba de
        // escribir. Se sanea igual que al guardar en el amplificador (7 bits, 16 bytes), así
        // que un nombre con eñe no produce un fichero que luego el amp rechace.
        val withName = image.copy()
        withName.write(KatanaAddresses.CURRENT_PRESET_NAME, PresetSave.encodeName(name))

        val omitted = mutableListOf<TslOmission>()
        val passedThrough = mutableListOf<String>()
        val params = mutableMapOf<String, JsonArray>()

        TslBlockMap.blocks.forEach { block ->
            val bytes = if (block.trusted) withName.bytesAt(block.address, block.size) else null
            when {
                bytes != null -> params[block.key] = bytes.toHexArray()

                source?.rawParamSet?.get(block.key)?.isNotEmpty() == true -> {
                    params[block.key] = JsonArray(
                        source.rawParamSet.getValue(block.key).map { JsonPrimitive(it) }
                    )
                    passedThrough += block.key
                }

                else -> omitted += TslOmission(
                    key = block.key,
                    reason = if (!block.trusted) {
                        "dirección en disputa entre fuentes y el fichero de origen no la traía"
                    } else {
                        "no hay bytes para este bloque ni en el preset ni en el fichero de origen"
                    },
                )
            }
        }

        // Claves que el fichero de origen traía y el mapa no conoce: se copian igual. No saber
        // qué son no es motivo para tirarlas — pertenecen a este preset.
        source?.rawParamSet?.forEach { (key, values) ->
            if (TslBlockMap.forKey(key) == null && values.isNotEmpty()) {
                params[key] = JsonArray(values.map { JsonPrimitive(it) })
                passedThrough += key
            }
        }

        val root = buildJsonObject {
            put("name", name)
            put("formatRev", FORMAT_REV)
            put("device", DEVICE)
            put(
                "data",
                buildJsonArray {
                    add(
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put(
                                        "memo",
                                        buildJsonObject {
                                            put("memo", memo)
                                            put("isToneCentralPatch", false)
                                        },
                                    )
                                    put("paramSet", JsonObject(params))
                                },
                            )
                        },
                    )
                },
            )
        }

        return TslWriteResult(
            json = json.encodeToString(JsonObject.serializer(), root),
            omitted = omitted,
            passedThrough = passedThrough,
        )
    }

    /**
     * Un preset **en blanco**, para empezar uno desde cero sin fichero y sin amplificador.
     *
     * ⚠️ **No es un preset de fábrica de Boss, y no pretende serlo.** Es un punto de partida
     * neutro construido con lo que la app sabe, y se hace así por dos razones: copiar un
     * `.tsl` de `reference/` metería material GPL dentro de la app (CLAUDE.md §7), y de todas
     * formas no hay ninguna fuente que diga cuál es "el preset vacío" correcto.
     *
     * **Todo a cero, con dos excepciones documentadas**, que son los sitios donde el cero no es
     * un valor neutro sino uno estructuralmente inválido:
     *
     * 1. **La cadena de efectos** (`60 00 06 00`–`06 13`) es un **array de permutación**: sus 20
     *    ranuras dicen qué bloque va en cada posición, así que veinte ceros significarían "el
     *    compresor veinte veces" y ningún otro efecto. Se siembra con la permutación identidad
     *    `00`..`13`, que sí es una cadena válida.
     * 2. **El nombre**, que se rellena con [name].
     *
     * Todo lo demás a cero es legal: los catálogos con huecos del proyecto (`AmpType` sin el
     * `0x19`, `BoostType` sin el `07`, `ModFxType`) **sí incluyen el `0x00`**, comprobado uno
     * por uno, así que ningún selector arranca en un valor que él mismo rechazaría.
     */
    fun blank(name: String): MemoryImage {
        val image = MemoryImage.empty()
        TslBlockMap.blocks.filter { it.trusted }.forEach { block ->
            image.write(block.address, ByteArray(block.size))
        }
        repeat(ChainBlock.SLOT_COUNT) { slot ->
            image.write(KatanaAddresses.chainSlot(slot), byteArrayOf(slot.toByte()))
        }
        image.write(KatanaAddresses.CURRENT_PRESET_NAME, PresetSave.encodeName(name))
        return image
    }

    private fun ByteArray.toHexArray(): JsonArray =
        JsonArray(map { JsonPrimitive("%02X".format(it.toInt() and 0xFF)) })
}
