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
     *
     * **Probado en el amplificador (2026-09-03) y aceptado.** El slider llega a 100 cuando la
     * perilla física está a punto del tope, quedando un tramo mínimo de recorrido. No se sabe
     * si el 100 real está en el tope físico o si ese resto es holgura mecánica, y la
     * diferencia entre 98 y 100 es inaudible, así que **se da el rango por bueno**. Si alguna
     * vez hace falta zanjarlo, no hace falta el oído: con Edit Mode activo, girar la perilla
     * hasta el tope y leer el valor que reporta el amplificador lo dice sin ambigüedad.
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
     * **Gain (perilla GAIN), `60 00 06 51`.** Dirección de lectura y escritura.
     *
     * ✅ Confirmado con audio real (2026-09-03): el slider cambia la ganancia audiblemente y
     * la perilla física reporta por aquí.
     *
     * Es la que llegó con menos aval de las cinco: **las dos fuentes de Mk2 se
     * contradecían**, y el audio le dio la razón a la alta de todos modos.
     *  - `reference/TuxKatana/params/amplifier.yaml:5` la lista como `am_gain_lvl` en `SEND`,
     *    o sea como dirección de **escritura**.
     *  - `reference/TuxKatana/doc/Adresses.txt:36-38` dice lo contrario y con flechas
     *    explícitas: `60 00 00 22 -> write value` y `60 00 06 51 <- read status`. Es el
     *    **único** sitio del fichero donde el autor anota dirección de esa forma.
     *
     * O sea que Adresses.txt afirmaba justo el patrón "escritura baja / reporte alto" que ya
     * había fallado con el reverb — y volvió a fallar aquí: el audio confirma que `06 51` es
     * la de control. [GAIN_LEVEL_LOW] queda documentada sin haberse llegado a necesitar.
     *
     * `reference/FxFloorboard/midi.xml:3982` la nombra `Panel Knob: Gain`.     *
     * **El rango `0..100` es una suposición razonada**, no un dato: ninguna fuente de Mk2 lo
     * documenta. Sale de `reference/katana-midi-bridge/parameters/amplifier.json:57-72`, que
     * es el mapa del **MK1**, y del formato `normal` de
     * `reference/TuxKatana/params/slider_formats.yaml:1-3`. Coincide con el que ya se acepta
     * para los seis niveles de efecto (ver [REVERB_LEVEL_RANGE]).
     */
    val GAIN_LEVEL = Address(0x60, 0x00, 0x06, 0x51)

    /**
     * Rango de [GAIN_LEVEL]: `0..100`. **Probado en el amplificador (2026-09-03) y aceptado**,
     * con la misma observación que el resto de perillas: el slider llega a 100 poco antes del
     * tope físico, sin que se pueda zanjar de oído si eso es holgura mecánica o no. Ver
     * [REVERB_LEVEL_RANGE].
     */
    val GAIN_LEVEL_RANGE = 0..100

    /**
     * ⚠️ Alternativa "baja" de Gain, `60 00 00 22`, **que nunca hizo falta probar**:
     * [GAIN_LEVEL] funcionó pese a la anotación en contra de `Adresses.txt`. Se conserva por
     * si Gain diera problemas más adelante — en `amplifier.yaml:11` la misma dirección se
     * llama `am_unk2_lvl`, "no sé qué es", así que tampoco es una candidata sólida.
     */
    val GAIN_LEVEL_LOW = Address(0x60, 0x00, 0x00, 0x22)

    /**
     * **Volume (perilla VOLUME), `60 00 06 52`.** Dirección de lectura y escritura.
     *
     * ✅ Confirmado con audio real (2026-09-03), mismo caso que [GAIN_LEVEL]: las fuentes se
     * contradecían —`amplifier.yaml:6` la pone como `am_vol_lvl` en `SEND`, y
     * `reference/TuxKatana/doc/Adresses.txt:39-41` da `60 00 00 28 -> write` como la de
     * escritura— y el audio confirmó que `06 52` es la buena.
     *
     * Un matiz solo de esta: `amplifier.yaml:15` la repite en el bloque `RECV` **comentado**
     * (`# "60 00 06 52": "am_vol_lvl"`), lo que sugiere que además reporta. Reportar y aceptar
     * escritura no son excluyentes —es justo lo que hacen las seis confirmadas—, así que no
     * cuenta ni a favor ni en contra.
     *
     * `reference/FxFloorboard/midi.xml:3983` la nombra `Panel Knob: Volume`.     *
     * **El rango `0..100` es una suposición razonada**, no un dato: ninguna fuente de Mk2 lo
     * documenta. Sale de `reference/katana-midi-bridge/parameters/amplifier.json:57-72`, que
     * es el mapa del **MK1**, y del formato `normal` de
     * `reference/TuxKatana/params/slider_formats.yaml:1-3`. Coincide con el que ya se acepta
     * para los seis niveles de efecto (ver [REVERB_LEVEL_RANGE]).
     */
    val VOLUME_LEVEL = Address(0x60, 0x00, 0x06, 0x52)

    /** Rango de [VOLUME_LEVEL]: `0..100`. Probado y aceptado, ver [GAIN_LEVEL_RANGE]. */
    val VOLUME_LEVEL_RANGE = 0..100

    /**
     * ⚠️ Alternativa "baja" de Volume, `60 00 00 28`, **que nunca hizo falta probar**. En
     * `amplifier.yaml:10` la misma dirección se llama `am_unk1_lvl`.
     */
    val VOLUME_LEVEL_LOW = Address(0x60, 0x00, 0x00, 0x28)

    /**
     * **Bass (perilla BASS), `60 00 06 53`.** Dirección de lectura y escritura.
     *
     * ✅ Confirmado con audio real (2026-09-03). `amplifier.yaml:7` solo citaba la dirección
     * baja (`am_bass_lvl: 60 00 00 24`) y ni mencionaba `06 53`; la alta salía de
     * `reference/TuxKatana/doc/Adresses.txt:45-47` y de
     * `reference/FxFloorboard/midi.xml:3984` (`Panel Knob: Bass`), y fue la que acertó.     *
     * **El rango `0..100` es una suposición razonada**, no un dato: ninguna fuente de Mk2 lo
     * documenta. Sale de `reference/katana-midi-bridge/parameters/amplifier.json:57-72`, que
     * es el mapa del **MK1**, y del formato `normal` de
     * `reference/TuxKatana/params/slider_formats.yaml:1-3`. Coincide con el que ya se acepta
     * para los seis niveles de efecto (ver [REVERB_LEVEL_RANGE]).
     */
    val BASS_LEVEL = Address(0x60, 0x00, 0x06, 0x53)

    /** Rango de [BASS_LEVEL]: `0..100`. Probado y aceptado, ver [GAIN_LEVEL_RANGE]. */
    val BASS_LEVEL_RANGE = 0..100

    /**
     * ⚠️ Alternativa "baja" de Bass, `60 00 00 24` (`amplifier.yaml:7`), **que nunca hizo
     * falta probar**.
     */
    val BASS_LEVEL_LOW = Address(0x60, 0x00, 0x00, 0x24)

    /**
     * **Middle (perilla MIDDLE), `60 00 06 54`.** Dirección de lectura y escritura.
     *
     * ✅ Confirmado con audio real (2026-09-03). Mismo caso que [BASS_LEVEL]:
     * `amplifier.yaml:8` solo citaba la baja (`am_middle_lvl: 60 00 00 25`); la alta salía de
     * `reference/TuxKatana/doc/Adresses.txt:48-50` y `reference/FxFloorboard/midi.xml:3985`
     * (`Panel Knob: Middle`), y fue la que acertó.     *
     * **El rango `0..100` es una suposición razonada**, no un dato: ninguna fuente de Mk2 lo
     * documenta. Sale de `reference/katana-midi-bridge/parameters/amplifier.json:57-72`, que
     * es el mapa del **MK1**, y del formato `normal` de
     * `reference/TuxKatana/params/slider_formats.yaml:1-3`. Coincide con el que ya se acepta
     * para los seis niveles de efecto (ver [REVERB_LEVEL_RANGE]).
     */
    val MIDDLE_LEVEL = Address(0x60, 0x00, 0x06, 0x54)

    /** Rango de [MIDDLE_LEVEL]: `0..100`. Probado y aceptado, ver [GAIN_LEVEL_RANGE]. */
    val MIDDLE_LEVEL_RANGE = 0..100

    /**
     * ⚠️ Alternativa "baja" de Middle, `60 00 00 25` (`amplifier.yaml:8`), **que nunca hizo
     * falta probar**.
     */
    val MIDDLE_LEVEL_LOW = Address(0x60, 0x00, 0x00, 0x25)

    /**
     * **Treble (perilla TREBLE), `60 00 06 55`.** Dirección de lectura y escritura.
     *
     * ✅ Confirmado con audio real (2026-09-03). Mismo caso que [BASS_LEVEL]:
     * `amplifier.yaml:9` solo citaba la baja (`am_treble_lvl: 60 00 00 26`); la alta salía de
     * `reference/TuxKatana/doc/Adresses.txt:51-52` y `reference/FxFloorboard/midi.xml:3986`
     * (`Panel Knob: Treble`), y fue la que acertó.     *
     * **El rango `0..100` es una suposición razonada**, no un dato: ninguna fuente de Mk2 lo
     * documenta. Sale de `reference/katana-midi-bridge/parameters/amplifier.json:57-72`, que
     * es el mapa del **MK1**, y del formato `normal` de
     * `reference/TuxKatana/params/slider_formats.yaml:1-3`. Coincide con el que ya se acepta
     * para los seis niveles de efecto (ver [REVERB_LEVEL_RANGE]).
     */
    val TREBLE_LEVEL = Address(0x60, 0x00, 0x06, 0x55)

    /** Rango de [TREBLE_LEVEL]: `0..100`. Probado y aceptado, ver [GAIN_LEVEL_RANGE]. */
    val TREBLE_LEVEL_RANGE = 0..100

    /**
     * ⚠️ Alternativa "baja" de Treble, `60 00 00 26` (`amplifier.yaml:9`), **que nunca hizo
     * falta probar**.
     */
    val TREBLE_LEVEL_LOW = Address(0x60, 0x00, 0x00, 0x26)

    // --- Selectores: amp type, colores y on/off ------------------------------------------
    //
    // ⚠️ TODO ESTE BLOQUE ESTÁ SIN CONFIRMAR contra el amplificador (2026-09-03). Las
    // direcciones salen de la documentación de `reference/` y están implementadas para poder
    // probarlas, no porque se sepa que funcionan. Vale lo de siempre (CLAUDE.md §5): un SET
    // bien formado con checksum correcto no demuestra nada.

    /**
     * ⚠️ **Amp Type — perilla del panel, `60 00 06 50`. SIN CONFIRMAR.**
     *
     * Cinco valores, `00`..`04`, uno por posición de la perilla AMP TYPE:
     * Acoustic / Clean / Crunch / Lead / Brown. Ver [AmpCategory].
     *
     * **No confundir con [AMP_TYPE_FULL]**, que es otro espacio de valores completamente
     * distinto para lo mismo. Es el error más fácil de cometer aquí:
     *  - `60 00 06 50` = **posición de la perilla**, `00..04`. `amplifier.yaml:3` lo llama
     *    `am_num` — un *número*, no un tipo.
     *  - `60 00 00 21` = **modelo de amplificador**, `0x00..0x20` con huecos, 30 valores.
     *
     * Fuentes: `reference/FxFloorboard/midi.xml:44062-44068`,
     * `reference/TuxKatana/doc/Adresses.txt:35` y
     * `reference/katana-midi-bridge/parameters/amplifier.json:44-48`.
     *
     * Es la única entrada del bloque de perillas (`06 50`–`06 5B`) que **no** es un nivel
     * continuo, y por eso quedó fuera de `LevelId` desde el principio.
     */
    val AMP_TYPE_PANEL = Address(0x60, 0x00, 0x06, 0x50)

    /**
     * ⚠️ **Amp Type — modelo completo, `60 00 00 21`. SIN CONFIRMAR.**
     *
     * Los 30 modelos de [AmpType], del `0x00` al `0x20` con huecos. `midi.xml:3416` la nombra
     * `PREAMP: Type`, y `midi.xml:37311-37341` trae la tabla de valores que le corresponde.
     *
     * ⚠️ **Es una dirección "baja"**, y ahí está la duda: las once direcciones confirmadas
     * hasta ahora son todas del bloque alto `60 00 06 5x`, y una fuente ya afirmó
     * equivocadamente que una baja era la de escritura (ver [GAIN_LEVEL]). Puede que esta
     * acepte escritura, puede que no, y puede que solo funcione [AMP_TYPE_PANEL] con sus
     * cinco categorías. Se cablean **las dos** precisamente para poder distinguirlo con una
     * sola sesión de pruebas.
     */
    val AMP_TYPE_FULL = Address(0x60, 0x00, 0x00, 0x21)

    /**
     * ⚠️ **Variación del amplificador (el LED "VARIATION"), `60 00 06 5C`. SIN CONFIRMAR.**
     *
     * `00` off, `01` on. Fuentes: `reference/FxFloorboard/midi.xml:44107-44110`
     * (`<DATA value="5C" ... abbr="led state" desc="Effect" customdesc="Variation">`),
     * `reference/TuxKatana/doc/Adresses.txt:42-43` y `amplifier.yaml:4` (`am_var_sw`).
     *
     * Ojo: la variación **también** se puede pedir por [AMP_TYPE_FULL] eligiendo uno de los
     * cinco `Var [...]` (`0x1C`–`0x20`). Si las dos vías funcionan habrá que decidir cuál usa
     * la UI; por ahora se exponen las dos.
     */
    val AMP_VARIATION = Address(0x60, 0x00, 0x06, 0x5C)

    /**
     * ⚠️ **Selector de color (verde/rojo/amarillo) de cada efecto. SIN CONFIRMAR.**
     *
     * El botón que hay bajo cada perilla de efecto, en direcciones consecutivas:
     *
     * | Efecto | Dirección | Nombre en `midi.xml` |
     * | --- | --- | --- |
     * | Boost | `60 00 06 39` | `Booster GRY color select` |
     * | Mod | `60 00 06 3A` | `MOD GRY color select` |
     * | FX | `60 00 06 3B` | `FX GRY color select` |
     * | Delay | `60 00 06 3C` | `Delay1 GRY color select` |
     * | Reverb | `60 00 06 3D` | `Rev/Delay2 GRY color select` |
     *
     * Valores `00|01|02` = verde/rojo/amarillo, ver [EffectColor]. **Tres fuentes de Mk2
     * coinciden** en direcciones y valores: `reference/FxFloorboard/midi.xml:3959-3963` —donde
     * "GRY" deletrea Green/Red/Yellow—, `reference/TuxKatana/doc/Adresses.txt:70, 83, 98, 112,
     * 123` y los `*_bank_sel` de `reference/TuxKatana/params/*.yaml`. Es la mejor
     * documentación que ha tenido ninguna dirección de este proyecto antes de probarla — lo
     * que **sigue sin ser una prueba**.
     */
    val BOOST_COLOR = Address(0x60, 0x00, 0x06, 0x39)

    /** Ver [BOOST_COLOR]. `mo_bank_sel` en `mod.yaml:6`. ⚠️ Sin confirmar. */
    val MOD_COLOR = Address(0x60, 0x00, 0x06, 0x3A)

    /** Ver [BOOST_COLOR]. `fx_bank_sel` en `fx.yaml:7`. ⚠️ Sin confirmar. */
    val FX_COLOR = Address(0x60, 0x00, 0x06, 0x3B)

    /** Ver [BOOST_COLOR]. `de_bank_sel` en `delay.yaml:28`. ⚠️ Sin confirmar. */
    val DELAY_COLOR = Address(0x60, 0x00, 0x06, 0x3C)

    /** Ver [BOOST_COLOR]. `re_bank_sel` en `reverb.yaml:7`. ⚠️ Sin confirmar. */
    val REVERB_COLOR = Address(0x60, 0x00, 0x06, 0x3D)

    /**
     * ⚠️ **Familia de color alternativa, del mapa MK1: `60 00 12 10`–`60 00 12 14`.**
     *
     * `reference/katana-midi-bridge/parameters/color_assign.json:74-84` define
     * `colorActiveIndex` en `baseAddr [96,0,18,16]` = `60 00 12 10`, longitud 5, con
     * `categoryOffset` boost=0, mod=1, **delay=2, efx=3**, reverb=4 — nótese que el orden es
     * distinto del bloque Mk2, donde FX va antes que Delay.
     *
     * Es el mapa del **MK1**, así que aplica la regla de siempre: sus direcciones por
     * parámetro no valen para el Mk2 (CLAUDE.md §5). Se conserva como plan B por si
     * [BOOST_COLOR] y compañía no funcionaran. `60 00 12 14` es además la dirección que ya
     * estaba en el proyecto como `REVERB_ACTIVE_COLOR`, y esto explica de dónde salía.
     */
    val EFFECT_COLOR_MK1: List<Address> = List(5) { index ->
        Address(0x60, 0x00, 0x12, 0x10 + index)
    }

    /**
     * ⚠️ **On/off de cada efecto. SIN CONFIRMAR.**
     *
     * | Efecto | Dirección | Nombre en `midi.xml` | Fuente TuxKatana |
     * | --- | --- | --- | --- |
     * | Boost | `60 00 00 10` | `BOOSTER: On/Off` | `booster.yaml:2` `booster_sw` |
     * | Mod | `60 00 01 00` | `MOD: Off/On` | `mod.yaml:2` `mod_sw` |
     * | FX | `60 00 03 00` | `FX: Off/On` | `fx.yaml:2` `fx_sw` |
     * | Delay | `60 00 05 00` | `DELAY 1: D1 On/Off` | `delay.yaml:2` `delay_sw` |
     * | Reverb | `60 00 05 40` | `REVERB: On/Off` | `reverb.yaml:2` `reverb_sw` |
     *
     * Las cuatro primeras están además en `reference/TuxKatana/doc/Adresses.txt:67, 81, 95,
     * 109`; **la de reverb no aparece en ese fichero** y sale solo de `reverb.yaml` y de
     * `midi.xml`.
     *
     * ⚠️ **Son direcciones "bajas"**, no del bloque `06 5x`. Nada confirmado en este proyecto
     * vive fuera del bloque alto, así que estas cinco son las más inciertas de todo el lote
     * pese a estar bien documentadas.
     *
     * ⚠️ **Y el sentido de los valores es una suposición.** Se asume `00` = off, `01` = on,
     * porque es lo que hace `EDIT_MODE` (confirmado) y lo que dicen los bloques `<DATA>` de
     * `midi.xml` (`00 name="Off"`, `01 name="On"`). Pero
     * `reference/TuxKatana/doc/Adresses.txt:81` anota `[00|01] # [ON|OFF]`, que leído en orden
     * diría lo contrario. Si al activar el switch el efecto se apaga, ahí está el motivo.
     */
    val BOOST_ENABLED = Address(0x60, 0x00, 0x00, 0x10)

    /** Ver [BOOST_ENABLED]. ⚠️ Sin confirmar. */
    val MOD_ENABLED = Address(0x60, 0x00, 0x01, 0x00)

    /** Ver [BOOST_ENABLED]. ⚠️ Sin confirmar. */
    val FX_ENABLED = Address(0x60, 0x00, 0x03, 0x00)

    /** Ver [BOOST_ENABLED]. ⚠️ Sin confirmar. */
    val DELAY_ENABLED = Address(0x60, 0x00, 0x05, 0x00)

    /** Ver [BOOST_ENABLED]. ⚠️ Sin confirmar; es la única que `Adresses.txt` no menciona. */
    val REVERB_ENABLED = Address(0x60, 0x00, 0x05, 0x40)

    /** Valores de un on/off: `00` off, `01` on. Ver la advertencia de [BOOST_ENABLED]. */
    val SWITCH_VALUES: List<Int> = listOf(0x00, 0x01)

    /** Payload de "encendido" para los on/off y para [AMP_VARIATION]. */
    const val SWITCH_ON = 0x01

    /** Payload de "apagado". */
    const val SWITCH_OFF = 0x00


    /**
     * **Presence, `60 00 06 56`.** Dirección de **lectura y escritura**.
     *
     * ✅ Confirmado con audio real (2026-09-02): mover el slider cambia el brillo del sonido
     * de forma audible, y la perilla física reporta por esta misma dirección con edit mode
     * activo.
     *
     * A diferencia del reverb, la candidata "alta" acertó **al primer intento**: no hizo falta
     * descartar la baja ([PRESENCE_LEVEL_LOW], que sigue sin probar). Encaja con lo que
     * documenta `reference/FxFloorboard/midi.xml:3987`,
     * `<PARAM value="3C" name="Panel Knob: Presence" desc="06" customdesc="56"/>`, en la misma
     * lista donde `06 5B` es `Panel Knob: Reverb/Delay2` — la del reverb.
     * `reference/TuxKatana/doc/Adresses.txt:134-138` la empareja con la baja.
     */
    val PRESENCE_LEVEL = Address(0x60, 0x00, 0x06, 0x56)

    /**
     * ⚠️ Rango de [PRESENCE_LEVEL]: `0..100`, **suposición razonada**, no un dato documentado
     * —a diferencia de la dirección, que sí está confirmada—.
     *
     * Ninguna fuente de Mk2 documenta el rango de `60 00 06 56`; `amplifier.yaml` se salta esa
     * dirección entera. `0..100` sale por analogía con dos sitios: el mapa del **MK1** en
     * `reference/katana-midi-bridge/parameters/amplifier.json:68-72`, que da `presence` como
     * `byteRange [0, 100]`, y el formato `normal` de
     * `reference/TuxKatana/params/slider_formats.yaml:1-3`, el que usan los niveles de panel.
     * Coincide además con [REVERB_LEVEL_RANGE].
     *
     * **Recordatorio para el futuro**: si aparece comportamiento raro en los extremos —que el
     * amplificador ignore valores cerca de 100, o que el slider no cubra todo el recorrido
     * real de la perilla física— este rango es el primer sospechoso, no la dirección.
     *
     * **Probado en el amplificador (2026-09-03) y aceptado.** El slider llega a 100 cuando la
     * perilla física está a punto del tope, quedando un tramo mínimo de recorrido. No se sabe
     * si el 100 real está en el tope físico o si ese resto es holgura mecánica, y la
     * diferencia entre 98 y 100 es inaudible, así que **se da el rango por bueno**. Si alguna
     * vez hace falta zanjarlo, no hace falta el oído: con Edit Mode activo, girar la perilla
     * hasta el tope y leer el valor que reporta el amplificador lo dice sin ambigüedad.
     */
    val PRESENCE_LEVEL_RANGE = 0..100

    /**
     * ⚠️ Alternativa "baja" de Presence, `60 00 00 27`, **tampoco probada**.
     *
     * Sale de `reference/TuxKatana/doc/Adresses.txt:137`. Encaja por posición con la secuencia
     * de EQ de `reference/TuxKatana/params/amplifier.yaml` —bass `00 24`, middle `00 25`,
     * treble `00 26`—, que se salta justamente el `00 27`, y con el orden
     * bass/mid/treble/presence de `amplifier.json`.
     *
     * [PRESENCE_LEVEL] funcionó a la primera, así que **nunca llegó a probarse**. Se conserva
     * por si Presence resultara tener más adelante el mismo problema que tuvo el reverb; no
     * debería hacer falta. Si algún día se recurre a ella, se prueba con audio igual que la
     * otra: no se acepta por descarte.
     */
    val PRESENCE_LEVEL_LOW = Address(0x60, 0x00, 0x00, 0x27)

    /**
     * **Boost (perilla BOOSTER), `60 00 06 57`.** Dirección de lectura y escritura.
     *
     * ✅ Confirmado con audio real (2026-09-02): el slider cambia el nivel de boost de forma
     * audible y la perilla física reporta por esta misma dirección.
     *
     * Candidata "alta" del bloque de perillas del panel (CLAUDE.md §5). Las fuentes de
     * Mk2 son más explícitas que con Presence: `reference/TuxKatana/params/booster.yaml:14`
     * la lista como `bo_vol_lvl` en la sección `SEND`, y
     * `reference/FxFloorboard/midi.xml:3988` la nombra
     * `<PARAM value="3D" name="Panel Knob: Booster" desc="06" customdesc="57"/>`.
     * `reference/TuxKatana/doc/Adresses.txt:83-85` la pone bajo `BOOSTER → Levels`.
     *
     * El `Unimplemented:` con que `booster.yaml` repite esta dirección al final resultó ser
     * ruido: TuxKatana no la usa, pero el amplificador sí la acepta.
     *
     * Ojo al interpretar el log: BOOSTER es una **perilla continua** en el panel, así que
     * girarla debe producir mensajes por esta dirección. El **botón de color** que lleva
     * debajo es otra cosa y vive en [BOOST_COLOR]; que no reporte por aquí es lo
     * esperado. El on/off del efecto es otra dirección más, `60 00 00 10`.
     */
    val BOOST_LEVEL = Address(0x60, 0x00, 0x06, 0x57)

    /**
     * ⚠️ Rango de [BOOST_LEVEL]: `0..100`, **suposición razonada**, no un dato documentado. Igual que
     * [PRESENCE_LEVEL_RANGE] y por los mismos motivos: ninguna fuente de Mk2 lo documenta.
     * `booster.yaml` no anota rango para `bo_vol_lvl`, y `Adresses.txt:84` solo apunta valores
     * observados (`60 00 06 57: [49|--|55]`, o sea 73 y 85 en decimal) — que al menos
     * descartan el `complexRange` de `Off + 0..50` con que
     * `reference/katana-midi-bridge/parameters/amplifier.json:73-77` codifica el booster del
     * MK1, porque 73 y 85 se salen de ahí.
     *
     * **Probado en el amplificador (2026-09-03) y aceptado.** El slider llega a 100 cuando la
     * perilla física está a punto del tope, quedando un tramo mínimo de recorrido. No se sabe
     * si el 100 real está en el tope físico o si ese resto es holgura mecánica, y la
     * diferencia entre 98 y 100 es inaudible, así que **se da el rango por bueno**. Si alguna
     * vez hace falta zanjarlo, no hace falta el oído: con Edit Mode activo, girar la perilla
     * hasta el tope y leer el valor que reporta el amplificador lo dice sin ambigüedad.
     */
    val BOOST_LEVEL_RANGE = 0..100

    /**
     * ⚠️ Alternativa "baja" de Boost, `60 00 00 12` (`bo_drive_lvl`), **sin probar**.
     *
     * Fuentes: `reference/TuxKatana/params/booster.yaml:6` y
     * `reference/TuxKatana/doc/Adresses.txt:83`. Solo se prueba si [BOOST_LEVEL] falla, y
     * entonces con audio, no por descarte.
     */
    val BOOST_LEVEL_LOW = Address(0x60, 0x00, 0x00, 0x12)

    /**
     * **Mod (perilla MOD), `60 00 06 58`.** Dirección de lectura y escritura.
     *
     * ✅ Confirmado con audio real (2026-09-02), igual que [BOOST_LEVEL]: el slider cambia el
     * sonido y la perilla física reporta por aquí.
     *
     * Candidata "alta" del bloque de perillas del panel (CLAUDE.md §5). Fuentes de Mk2:
     * `reference/TuxKatana/params/mod.yaml:7` la lista como `mo_vol_lvl` en la sección `SEND`
     * —y, a diferencia de Boost, `mod.yaml` **no** tiene sección `Unimplemented` donde
     * repetirla—; `reference/FxFloorboard/midi.xml:3989` la nombra
     * `<PARAM value="3E" name="Panel Knob: MOD" desc="06" customdesc="58"/>`; y
     * `reference/TuxKatana/doc/Adresses.txt:95-101` la pone bajo `MOD → Green → Level`.
     *
     * Al leer el log: MOD es una **perilla continua**, así que girarla debe reportar por aquí.
     * El botón de color de debajo es [MOD_COLOR] y el on/off del efecto es
     * `60 00 01 00`; que esos no reporten por esta dirección es lo esperado.
     */
    val MOD_LEVEL = Address(0x60, 0x00, 0x06, 0x58)

    /**
     * ⚠️ Rango de [MOD_LEVEL]: `0..100`, **suposición razonada**, no un dato documentado, por los mismos motivos
     * que [PRESENCE_LEVEL_RANGE] y [BOOST_LEVEL_RANGE]: ninguna fuente de Mk2 lo documenta.
     * `mod.yaml` no anota rango para `mo_vol_lvl`, y los valores que apunta
     * `reference/TuxKatana/doc/Adresses.txt:99` (`60 00 06 58 -> [1E|1B|55]`, o sea 30, 27 y
     * 85) son observaciones sueltas por color, no un rango.
     *
     * **Probado en el amplificador (2026-09-03) y aceptado.** El slider llega a 100 cuando la
     * perilla física está a punto del tope, quedando un tramo mínimo de recorrido. No se sabe
     * si el 100 real está en el tope físico o si ese resto es holgura mecánica, y la
     * diferencia entre 98 y 100 es inaudible, así que **se da el rango por bueno**. Si alguna
     * vez hace falta zanjarlo, no hace falta el oído: con Edit Mode activo, girar la perilla
     * hasta el tope y leer el valor que reporta el amplificador lo dice sin ambigüedad.
     */
    val MOD_LEVEL_RANGE = 0..100

    /**
     * ⚠️ Alternativas "bajas" de Mod, **sin probar**: `60 00 02 38` y `60 00 02 3C`.
     *
     * `reference/TuxKatana/doc/Adresses.txt:99-101` lista **las dos** bajo `MOD → Green →
     * Level`, sin decir cuál es cuál. Solo se prueban si [MOD_LEVEL] falla, y entonces con
     * audio y por separado.
     */
    val MOD_LEVEL_LOW = listOf(
        Address(0x60, 0x00, 0x02, 0x38),
        Address(0x60, 0x00, 0x02, 0x3C),
    )

    /**
     * **FX (perilla FX), `60 00 06 59`.** Dirección de lectura y escritura.
     *
     * ✅ Confirmado con audio real (2026-09-03): el slider cambia el sonido y la perilla
     * física reporta por aquí.
     *
     * Candidata "alta" del bloque de perillas del panel (CLAUDE.md §5). Fuentes de Mk2:
     * `reference/TuxKatana/params/fx.yaml:8` (`fx_vol_lvl`, sección `SEND`, sin
     * `Unimplemented` donde repetirla), `reference/FxFloorboard/midi.xml:3990`
     * (`<PARAM value="3F" name="Panel Knob: FX" desc="06" customdesc="59"/>`) y
     * `reference/TuxKatana/doc/Adresses.txt:104-113` (`FX → Green → Level`).
     *
     * Botón de color en [FX_COLOR]; on/off del efecto en `60 00 03 00`.
     */
    val FX_LEVEL = Address(0x60, 0x00, 0x06, 0x59)

    /**
     * ⚠️ Rango de [FX_LEVEL]: `0..100`, **suposición razonada**, no un dato documentado, mismo caso que
     * [PRESENCE_LEVEL_RANGE]: ninguna fuente de Mk2 documenta el rango de esta dirección.
     *
     * **Probado en el amplificador (2026-09-03) y aceptado.** El slider llega a 100 cuando la
     * perilla física está a punto del tope, quedando un tramo mínimo de recorrido. No se sabe
     * si el 100 real está en el tope físico o si ese resto es holgura mecánica, y la
     * diferencia entre 98 y 100 es inaudible, así que **se da el rango por bueno**. Si alguna
     * vez hace falta zanjarlo, no hace falta el oído: con Edit Mode activo, girar la perilla
     * hasta el tope y leer el valor que reporta el amplificador lo dice sin ambigüedad.
     */
    val FX_LEVEL_RANGE = 0..100

    /**
     * ⚠️ Alternativa "baja" de FX, `60 00 04 14`, **que nunca hizo falta probar**: [FX_LEVEL]
     * funcionó a la primera. Sale de `reference/TuxKatana/doc/Adresses.txt:112`, donde además
     * lleva un `?` del propio autor. Se conserva por si FX diera problemas más adelante.
     */
    val FX_LEVEL_LOW = Address(0x60, 0x00, 0x04, 0x14)

    /**
     * **Delay (perilla DELAY), `60 00 06 5A`.** Dirección de lectura y escritura.
     *
     * ✅ Confirmado con audio real (2026-09-03): el slider cambia el sonido y la perilla
     * física reporta por aquí.
     *
     * Candidata "alta" del bloque de perillas del panel (CLAUDE.md §5). Fuentes de Mk2:
     * `reference/TuxKatana/params/delay.yaml:29` (`de_vol_lvl`, sección `SEND`),
     * `reference/FxFloorboard/midi.xml:3991`
     * (`<PARAM value="40" name="Panel Knob: Delay 1" desc="06" customdesc="5A"/>`) y
     * `reference/TuxKatana/doc/Adresses.txt:116-129` (`DELAY → Green → Level`).
     *
     * ⚠️ **Qué controla exactamente: el mix global de los dos delays, no "el nivel del
     * delay 1".** El Katana Mk2 tiene **dos** delays pero **una sola perilla DELAY** en el
     * panel; según el dueño del amplificador, esa perilla ajusta el mix global entre ambos.
     * Eso da la razón a la línea comentada `# "60 00 06 5A": glob_mix_lvl` de la sección
     * `RECV` de `delay.yaml`, que se había dado por muerta: probablemente sea el nombre
     * correcto, y el `Panel Knob: Delay 1` de `midi.xml` sea el nombre de la perilla física,
     * no de lo que hay detrás.
     *
     * La prueba de audio **no distingue** una cosa de otra: se hizo con el delay 2 apagado, y
     * con el 2 apagado el mix global se comporta exactamente como el nivel del delay 1. En la
     * práctica da igual mientras solo se use un delay; importa el día que se quiera controlar
     * cada delay por separado, y entonces esta **no** es la dirección que buscar.
     *
     * `06 5B` es el otro caso de perilla compartida: `midi.xml` la llama "Reverb/Delay2".
     *
     * Botón de color en [DELAY_COLOR]; on/off del efecto en `60 00 05 00`.
     */
    val DELAY_LEVEL = Address(0x60, 0x00, 0x06, 0x5A)

    /**
     * ⚠️ Rango de [DELAY_LEVEL]: `0..100`, **suposición razonada**, no un dato documentado.
     * `delay.yaml` sí anota
     * rangos para varios parámetros del delay (`de_time_lvl` es `1ms..2s`, por ejemplo) pero
     * **no para `de_vol_lvl`**, que es esta.
     *
     * **Probado en el amplificador (2026-09-03) y aceptado.** El slider llega a 100 cuando la
     * perilla física está a punto del tope, quedando un tramo mínimo de recorrido. No se sabe
     * si el 100 real está en el tope físico o si ese resto es holgura mecánica, y la
     * diferencia entre 98 y 100 es inaudible, así que **se da el rango por bueno**. Si alguna
     * vez hace falta zanjarlo, no hace falta el oído: con Edit Mode activo, girar la perilla
     * hasta el tope y leer el valor que reporta el amplificador lo dice sin ambigüedad.
     */
    val DELAY_LEVEL_RANGE = 0..100

    /**
     * ⚠️ Alternativas "bajas" de Delay, **que nunca hicieron falta probar**: `60 00 05 06`
     * (`de_effect_lvl`) y `60 00 05 04` (`de_feedback_lvl`, que `Adresses.txt:127` marca con
     * `?`). Se conservan por si Delay diera problemas más adelante.
     */
    val DELAY_LEVEL_LOW = listOf(
        Address(0x60, 0x00, 0x05, 0x06),
        Address(0x60, 0x00, 0x05, 0x04),
    )

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
