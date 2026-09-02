package dev.alonx3.ktnacontrol.protocol

/**
 * Known Katana MK2 addresses (CLAUDE.md §5).
 *
 * Only what is actually used so far; the full map lives in
 * `reference/katana-midi-bridge/doc/katana_sysex.txt` and
 * `reference/TuxKatana/params/midi.yaml`, and gets copied here as each block is
 * implemented — with the source noted, per CLAUDE.md §7.
 */
object KatanaAddresses {

    /**
     * Device name, 16 ASCII bytes (`KATANA Mk2`).
     *
     * Source: `reference/TuxKatana/params/midi.yaml` (`DEV_NAME`), with the exact request
     * and reply traced in `reference/TuxKatana/HOW.md`.
     */
    val DEVICE_NAME = Address(0x10, 0x00, 0x00, 0x00)

    /** Length in bytes of the name at [DEVICE_NAME]. */
    const val DEVICE_NAME_SIZE = 16

    /** How many user presets the amp stores. */
    const val PRESET_COUNT = 8

    /** Preset names are 16 ASCII bytes each, same as [DEVICE_NAME_SIZE]. */
    const val PRESET_NAME_SIZE = 16

    /** Name of preset 1, at `10 01 00 00`. */
    val FIRST_PRESET_NAME = Address(0x10, 0x01, 0x00, 0x00)

    /**
     * Gap between consecutive preset names: exactly `+1` on the address's second byte, which
     * in base 128 is 16384. Written as an address so the intent stays readable instead of
     * turning into a magic number.
     */
    private val PRESET_NAME_STRIDE = Address(0x00, 0x01, 0x00, 0x00).value

    /**
     * The 8 preset name addresses, `10 01 00 00` … `10 08 00 00`, derived with [Address]
     * arithmetic rather than written out one by one.
     *
     * Source: `reference/TuxKatana/params/midi.yaml` (`PRESET_1`…`PRESET_8`); the request
     * and reply for presets 1 and 2 are traced in `reference/TuxKatana/HOW.md`.
     */
    val PRESET_NAMES: List<Address> = List(PRESET_COUNT) { index ->
        FIRST_PRESET_NAME + index * PRESET_NAME_STRIDE
    }

    /**
     * Start of the "effective" block: the amp's current state.
     *
     * The first 16 bytes are the name of the preset in use. A dump is requested with size
     * [MEMORY_DUMP_SIZE], but that is a **range limit, not a byte count**: the amp skips
     * areas where no parameters are defined, so fewer bytes come back — and they arrive
     * **split over several messages**, each with its own address, not as one big one.
     * `reference/TuxKatana/HOW.md` traces 5 messages of 241 data bytes plus a final one of
     * 139.
     *
     * Source: `reference/katana-midi-bridge/doc/katana_sysex.txt` ("Effective Block") and
     * the trace in `reference/TuxKatana/HOW.md`.
     */
    val MEMORY_DUMP = Address(0x60, 0x00, 0x00, 0x00)

    /** Range asked for in a dump: `00 00 0F 00` = 1920. */
    const val MEMORY_DUMP_SIZE = 1920

    /** The current preset name occupies the first bytes of the dump. */
    const val PRESET_NAME_IN_DUMP = 16

    /**
     * **Nivel de reverb del Katana Mk2: `60 00 06 5B`, rango 0..100.** Dirección definitiva,
     * de **lectura y escritura a la vez** — no es "la de reporte" de un par.
     *
     * ✅ Confirmado con audio real: un SET aquí **cambia el sonido** del amplificador, y los
     * mensajes espontáneos al girar la perilla física llegan por esta misma dirección con el
     * valor que toca. Es la única de las tres candidatas que hace ambas cosas.
     *
     * Fuentes: `reference/TuxKatana/params/reverb.yaml:19` (`re_vol_lvl`, en la sección
     * `SEND`) y `reference/TuxKatana/doc/Adresses.txt:132`. `set_mapping.py:39-43` mete
     * `SEND` y `RECV` en el mismo mapa, y la cadena `effect.py:62` → `direct_mry` →
     * `memory.set_value` → `controller.py:82 send(..., SET=True)` confirma que TuxKatana
     * escribe ahí.
     */
    val REVERB_LEVEL = Address(0x60, 0x00, 0x06, 0x5B)

    /**
     * Rango válido de [REVERB_LEVEL].
     *
     * `katana_sysex.txt` dice `0..100` y `reverb.yaml` comenta `'00' -> '63'` (0..99). Se usa
     * `0..100`, que es lo que se probó por los extremos sin problemas.
     */
    val REVERB_LEVEL_RANGE = 0..100

    /**
     * ❌ **Intento descartado 1: `60 00 06 18`, del mapa del MK1.**
     *
     * Probado contra el amplificador: un SET con valores de 0 a 100 no produce **ningún**
     * cambio, ni audible ni en el estado interno, y un GET devuelve un `07` fijo que tampoco
     * se mueve al girar la perilla física.
     *
     * Sale de `reference/katana-midi-bridge/doc/katana_sysex.txt`, cuya primera línea dice
     * "Boss Katana 100 Combo — v1.7 - 2017-03-23": documenta el **MK1**, anterior al Mk2.
     * Ninguna fuente de Mk2 la menciona. Se conserva para que nadie la reintroduzca creyendo
     * que fue un despiste.
     */
    @Deprecated(
        message = "Dirección del mapa MK1; confirmado por audio que no afecta al Mk2.",
        replaceWith = ReplaceWith("REVERB_LEVEL"),
    )
    val REVERB_LEVEL_MK1 = Address(0x60, 0x00, 0x06, 0x18)

    /**
     * ❌ **Intento descartado 2: `60 00 05 48`, un valor derivado, no un punto de control.**
     *
     * Aparece como `re_effect_lvl` en la sección `SEND` de
     * `reference/TuxKatana/params/reverb.yaml:17` — una fuente de Mk2 que la documenta como
     * dirección de escritura. **Aun así, escribir ahí no hace nada**: tras mandar 100
     * repetidamente, un GET a [REVERB_LEVEL] seguía devolviendo 0, y no hubo cambio audible.
     *
     * Sí llegan mensajes espontáneos por esta dirección al mover la perilla física, pero con
     * un valor **relacionado y retardado** respecto al de [REVERB_LEVEL] (se observó un
     * desfase de ~15 entre ambos). Hipótesis de trabajo: es el nivel de efecto ya aplicado,
     * con inercia — algo que el amp *deriva*, no algo que se le manda.
     *
     * Lección: que una fuente de Mk2 liste una dirección bajo `SEND` **no basta**; hay que
     * probarla con audio real.
     */
    @Deprecated(
        message = "Valor derivado, no un punto de control: escribir ahí no tiene efecto.",
        replaceWith = ReplaceWith("REVERB_LEVEL"),
    )
    val REVERB_LEVEL_DERIVED = Address(0x60, 0x00, 0x05, 0x48)

    /**
     * Qué botón de color está activo para el reverb: `00` verde, `01` rojo, `02` amarillo.
     *
     * ⚠️ Es la dirección del ejemplo de checksum de `katana_sysex.txt` (`60 00 12 14 --> 01`,
     * checksum `79`), anotado ahí como "set Katana Reverb type 'red'". **No es un nivel**:
     * pertenece a Color Button Management → `[Select Active Color]` (líneas 1932-1942) y
     * elige cuál de los tres presets de color está en uso. Se anota para que el vector de
     * test no se vuelva a confundir con un parámetro continuo.
     */
    val REVERB_ACTIVE_COLOR = Address(0x60, 0x00, 0x12, 0x14)

    /** Valores válidos de [REVERB_ACTIVE_COLOR]: verde, rojo, amarillo. */
    val REVERB_ACTIVE_COLOR_RANGE = 0..2

    /**
     * Edit mode, also called "BTS control mode": `00` off, `01` on.
     *
     * With it on the amp reports parameters that change on their own — front-panel knobs and
     * derived values — which is what makes a faithful UI possible. It **changes the state of
     * the amplifier**, so CLAUDE.md §4.2 asks for it to be an explicit, visible setting
     * rather than something switched silently.
     *
     * Source: `reference/katana-midi-bridge/doc/katana_sysex.txt` ("Place in BTS control
     * mode"); the exact write is traced in `reference/TuxKatana/HOW.md`.
     */
    val EDIT_MODE = Address(0x7F, 0x00, 0x00, 0x01)

    /** Payload that turns [EDIT_MODE] on. */
    const val EDIT_MODE_ON: Byte = 0x01

    /** Payload that turns [EDIT_MODE] off. */
    const val EDIT_MODE_OFF: Byte = 0x00

    /**
     * Address of the name of preset [number], counting from 1 the way the amp's front panel
     * does.
     */
    fun presetName(number: Int): Address {
        require(number in 1..PRESET_COUNT) {
            "el preset debe estar entre 1 y $PRESET_COUNT, era $number"
        }
        return PRESET_NAMES[number - 1]
    }
}
