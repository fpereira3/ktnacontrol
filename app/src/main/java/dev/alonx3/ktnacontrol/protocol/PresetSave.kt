package dev.alonx3.ktnacontrol.protocol

/**
 * Guardar el estado editado en uno de los ocho canales del amplificador (CLAUDE.md §5,
 * "Guardado de presets").
 *
 * **No se mandan los datos del preset.** El amplificador ya tiene el estado editado en su
 * búfer —es lo que la app viene modificando parámetro a parámetro—, así que guardar son dos
 * mensajes y ninguno lleva el sonido dentro:
 *
 * ```
 * 1. Nombre   SET 60 00 00 00 -> 16 bytes ASCII      ([nameMessage])
 * 2. Commit   SET 7F 00 01 04 -> 00 xx               ([commitMessage])
 * ```
 *
 * ✅ **El commit está confirmado para el Mk2 en código**, no deducido del MK1:
 * `reference/FxFloorboard/patchWriteDialog.cpp:346` construye literalmente
 * `"F0410000000033127F00010400" + addr + "00F7"`, y `midiIO.cpp:504-512` recalcula el checksum
 * antes de mandarlo con la misma fórmula de siempre (ese `00` de la plantilla es un marcador,
 * no el checksum real).
 *
 * ⚠️ **Es destructivo e irreversible sobre el amplificador**: sobrescribe el canal destino y no
 * hay deshacer. Y es **fire-and-forget**: ninguna fuente documenta una respuesta, y todo apunta
 * a que no la hay (misma semántica DT1 que el edit mode, §4.2). El único indicio de éxito es
 * **leer de vuelta el nombre del canal destino** en `10 0N 00 00` y ver si cambió — que es lo
 * que hace `KatanaRepository.savePreset`.
 *
 * ⚠️ **Nada de esto está probado contra el amplificador.**
 */
object PresetSave {

    /** Longitud del nombre de un preset: 16 bytes ASCII, como todos los nombres del amp. */
    const val NAME_LENGTH = KatanaAddresses.PRESET_NAME_SIZE

    /** Relleno de un nombre más corto de [NAME_LENGTH]: espacio, como hace el propio amp. */
    const val NAME_PADDING = ' '

    /**
     * Con qué se sustituye un carácter que no cabe en ASCII imprimible.
     *
     * Hace falta porque los datos SysEx son de **7 bits** y `RolandSysEx.set` rechaza cualquier
     * byte por encima de `0x7F`: sin esto, un nombre con eñe o acento —perfectamente normal en
     * un teclado en castellano— haría fallar el guardado entero en vez de guardar un nombre
     * aproximado.
     */
    const val NAME_REPLACEMENT = '?'

    /** El primer canal guardable. **`00` es PANEL y NO está aquí** — ver [CHANNELS]. */
    const val FIRST_CHANNEL = 1

    /**
     * Los ocho canales que se pueden sobrescribir: `01`..`08`, en el orden A1-A4, B1-B4.
     *
     * ⚠️ **PANEL (`00`) se deja fuera a propósito.** La lista de destinos de FxFloorboard sí lo
     * incluye y su código no lo excluye, pero **ninguna fuente dice qué hace el amplificador
     * con él** y "guardar en el panel" no tiene un significado obvio —el panel es precisamente
     * el estado no guardado— (CLAUDE.md §5, "¿`00` (PANEL) es un destino legal?" → TBD).
     * Ofrecerlo sin saberlo sería invitar a una operación destructiva de efecto desconocido.
     *
     * Es la **misma numeración** que [KatanaAddresses.ACTIVE_CHANNEL] (§5.1), quitándole el
     * `00`; **no** la de Program Change, donde el panel es el 4 y parte los bancos por la mitad.
     */
    val CHANNELS: List<Int> = (FIRST_CHANNEL..KatanaAddresses.PRESET_COUNT).toList()

    /**
     * Convierte [name] en los 16 bytes ASCII que espera el amplificador: recorta lo que sobre,
     * rellena con [NAME_PADDING] lo que falte, y sustituye por [NAME_REPLACEMENT] lo que no sea
     * ASCII imprimible (`0x20`..`0x7E`).
     *
     * Los caracteres de control quedan fuera igual que los acentos: un `\n` dentro del nombre
     * de un preset no significaría nada para el amp y sí podría desconcertar a quien lea el
     * volcado en hexadecimal.
     */
    fun encodeName(name: String): ByteArray {
        val bytes = ByteArray(NAME_LENGTH) { NAME_PADDING.code.toByte() }
        name.take(NAME_LENGTH).forEachIndexed { index, character ->
            val code = character.code
            val printable = code in 0x20..0x7E
            bytes[index] = (if (printable) code else NAME_REPLACEMENT.code).toByte()
        }
        return bytes
    }

    /**
     * Lo contrario de [encodeName], para comparar lo que se pidió guardar con lo que el
     * amplificador dice tener después. Recorta el relleno del final.
     */
    fun decodeName(bytes: ByteArray): String =
        bytes.map { (it.toInt() and 0x7F).toChar() }.joinToString("").trimEnd(NAME_PADDING)

    /**
     * El mensaje que escribe el nombre en `60 00 00 00`, paso 1 del guardado.
     *
     * ⚠️ Esa dirección es también donde empieza el dump ([KatanaAddresses.MEMORY_DUMP]): son la
     * misma dirección sirviendo a dos cosas, no un descuido. Los 16 primeros bytes del bloque
     * efectivo son el nombre del preset en uso, y el dump empieza justo ahí.
     */
    fun nameMessage(name: String): ByteArray =
        RolandSysEx.set(KatanaAddresses.CURRENT_PRESET_NAME, encodeName(name))

    /**
     * El commit que copia el estado editado al canal [channel] (`01`..`08`), paso 2.
     *
     * El dato son **2 bytes**, `00 xx`, igual que el canal activo de §5.1 —
     * `patchWriteDialog.cpp` manda `"00" + addr`— y el checksum sale de la fórmula de siempre.
     * Para CH A1 (`01`) el mensaje completo es
     * `F0 41 00 00 00 00 33 12 7F 00 01 04 00 01 7B F7`.
     *
     * @throws IllegalArgumentException si [channel] no es uno de [CHANNELS]. Se rechaza en vez
     *   de clampear: el destino de una operación destructiva no es algo que convenga adivinar,
     *   y un `0` colado por descuido sería el PANEL, cuyo efecto nadie ha comprobado.
     */
    fun commitMessage(channel: Int): ByteArray {
        require(channel in CHANNELS) {
            "el canal destino debe estar entre ${CHANNELS.first()} y ${CHANNELS.last()}, era $channel"
        }
        return RolandSysEx.set(
            KatanaAddresses.PRESET_SAVE,
            MidiBytes.encode(channel, KatanaAddresses.PRESET_SAVE_SIZE),
        )
    }

    /**
     * El nombre que la UI muestra para el canal [channel]: `A1`..`A4`, `B1`..`B4`.
     *
     * Los ocho valores son consecutivos (`01`..`08`) pero el panel los agrupa en dos bancos de
     * cuatro, y decirle a alguien que va a sobrescribir "el canal 7" cuando en el amplificador
     * pone "B3" es la clase de detalle que hace fallar una confirmación.
     */
    fun channelLabel(channel: Int): String {
        require(channel in CHANNELS) { "canal fuera de rango: $channel" }
        val bank = if (channel <= 4) "A" else "B"
        val number = if (channel <= 4) channel else channel - 4
        return "$bank$number"
    }
}
