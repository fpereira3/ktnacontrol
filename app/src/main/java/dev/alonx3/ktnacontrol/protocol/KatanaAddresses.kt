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
     * Escala de las **seis perillas de amp/EQ** (`60 00 06 51`–`06 56`): crudo y mostrado son
     * el mismo número, `0..100`.
     *
     * Fuente: `reference/FxFloorboard/midi.xml:44069-44086`, donde cada una lleva
     * `range 00/64/00/100` — `0x00`..`0x64` crudo mostrado como 0..100. Confirmado por oído
     * en las seis (2026-09-03), y el rango coincide con el mapa MK1 de
     * `reference/katana-midi-bridge/parameters/amplifier.json:57-72`.
     */
    val PANEL_LEVEL_SCALE: LevelScale = LevelScale.direct(0..100)

    /**
     * Escala de los **cinco niveles de efecto** (`60 00 06 57`–`06 5B`): `0` es Off y el
     * `0..100` que se muestra vive en el crudo `1..101`.
     *
     * ✅ **Corregido el 2026-09-03**, después de haber usado `0..100` directo durante toda la
     * fase de niveles. Ver [LevelScale] para las dos fuentes —`midi.xml` y la UI de Boss Tone
     * Studio— y para lo que explica: el slider llegaba a 100 con la perilla física un pelo
     * antes del tope porque el crudo 100 que mandaba la app es el 99 del amplificador.
     */
    val EFFECT_LEVEL_SCALE: LevelScale = LevelScale.offThenOneBased(0..100)

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
     *
     * ✅ **Identificada, con la extracción de los parámetros internos de Reverb (CLAUDE.md
     * §5.2): es el "Effect Level" del bloque interno de Reverb** —
     * `midi.xml:42928`, `<DATA value="48" desc="REV:" customdesc="Effect">`, entre
     * [REVERB_DENSITY] (`47`) y [REVERB_DIRECT_MIX] (`49`)—, no una dirección sin identificar.
     * Esto explica por qué "escribir ahí no hace nada" para el volumen del efecto: no es que
     * la dirección esté muerta, es que **no es la perilla del panel**, es un parámetro interno
     * de la reverb en sí — el "derivado y retardado" que se observaba era, con esta lectura,
     * probablemente el nivel interno tras aplicarle el propio efecto. No se reintroduce como
     * control nuevo: sigue sin haber prueba de que aceptar escritura aquí cambie el sonido, así
     * que se queda documentada, no cableada, hasta que el audio diga lo contrario.
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
     * para los seis niveles de efecto (ver [PANEL_LEVEL_SCALE]).
     */
    val GAIN_LEVEL = Address(0x60, 0x00, 0x06, 0x51)

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
     * para los seis niveles de efecto (ver [PANEL_LEVEL_SCALE]).
     */
    val VOLUME_LEVEL = Address(0x60, 0x00, 0x06, 0x52)

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
     * para los seis niveles de efecto (ver [PANEL_LEVEL_SCALE]).
     */
    val BASS_LEVEL = Address(0x60, 0x00, 0x06, 0x53)

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
     * para los seis niveles de efecto (ver [PANEL_LEVEL_SCALE]).
     */
    val MIDDLE_LEVEL = Address(0x60, 0x00, 0x06, 0x54)

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
     * para los seis niveles de efecto (ver [PANEL_LEVEL_SCALE]).
     */
    val TREBLE_LEVEL = Address(0x60, 0x00, 0x06, 0x55)

    /**
     * ⚠️ Alternativa "baja" de Treble, `60 00 00 26` (`amplifier.yaml:9`), **que nunca hizo
     * falta probar**.
     */
    val TREBLE_LEVEL_LOW = Address(0x60, 0x00, 0x00, 0x26)

    // --- Selectores: amp type, colores y on/off ------------------------------------------
    //
    // ✅ Probado contra el amplificador el 2026-09-03. Todo el bloque funciona **salvo
    // [AMP_VARIATION]**, que resultó ser de solo lectura; ver su KDoc.

    /**
     * **Amp Type — perilla del panel, `60 00 06 50`.** Lectura y escritura.
     *
     * ✅ Confirmado con el amplificador (2026-09-03): elegir una categoría cambia el canal.
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
     * **Amp Type — modelo completo, `60 00 00 21`.** Lectura y escritura.
     *
     * ✅ Confirmado con el amplificador (2026-09-03): los 30 modelos cambian el canal, tanto
     * los cinco base como sus variaciones y los "sneaky amps". `midi.xml:3416` la nombra
     * `PREAMP: Type`, y `midi.xml:37311-37341` trae la tabla que le corresponde.
     *
     * **Es la primera dirección "baja" que se confirma en este proyecto.** Hasta aquí todo lo
     * confirmado vivía en el bloque alto `60 00 06 5x`, y eso se había convertido casi en una
     * regla de trabajo. No lo es: el bloque alto es donde están las *perillas del panel*, no
     * donde está todo lo escribible.
     *
     * Es además la vía por la que la app cambia la **variación**, ya que [AMP_VARIATION] no
     * acepta escritura: ver [AmpCategory.typeValue].
     */
    val AMP_TYPE_FULL = Address(0x60, 0x00, 0x00, 0x21)

    /**
     * **Bright, `60 00 00 29`.** On/off: usa [SWITCH_VALUES].
     *
     * ❌ **Probado contra el amplificador el 2026-09-06: sin efecto.** Ni cambia el sonido ni
     * mueve el estado interno — el SET+GET inmediato de [dev.alonx3.ktnacontrol.device.WriteProbe]
     * dice que el valor no se movió. **Retirado de la UI**: un control que acepta el gesto y no
     * hace nada es peor que ninguno.
     *
     * ⚠️ **La dirección se conserva registrada a propósito, no borrada.** El precedente del
     * nivel de reverb (CLAUDE.md §5) es que una dirección plausible puede estar simplemente mal
     * identificada, y el día que aparezca otra candidata conviene tener esta documentada para
     * saber qué ya se descartó. Ver BACKLOG.md.
     *
     * Sale del
     * bloque PREAMP (`60 00 00 21`–`2C`): `midi.xml:37483` la nombra `PREAMP: Bright`, con
     * `00` Off / `01` On. Es la única fuente — ninguna otra documenta este bloque para el
     * Mk2 (ver BACKLOG.md, "Cambiar el tipo de amplificador no recarga nada").
     *
     * Ya viene en el dump: `60 00 00 2x` cae de lleno en `60 00 00 00` + 1920 B, así que no
     * hace falta ampliar ningún rango de GET para leerlo — solo faltaba registrar el control.
     */
    val AMP_BRIGHT = Address(0x60, 0x00, 0x00, 0x29)

    /**
     * **Gain SW, `60 00 00 2A`.** Tres posiciones: usa [GAIN_SW_VALUES].
     *
     * ❌ **Probado contra el amplificador el 2026-09-06: sin efecto**, igual que [AMP_BRIGHT] y
     * con el mismo método. **Retirado de la UI**; la dirección se conserva por la misma razón
     * que allí. Ver BACKLOG.md.
     *
     * `midi.xml:37487` la nombra `PREAMP: Gain SW` con tres
     * valores literales `Low` / `Middle` / `High` (`00`/`01`/`02`) — probablemente el rango
     * de la perilla GAIN, a la manera de un selector de "gama" del preamp, pero ninguna
     * fuente lo explica más allá del nombre — y la prueba de audio acabó diciendo que no cambia
     * nada.
     */
    val AMP_GAIN_SW = Address(0x60, 0x00, 0x00, 0x2A)

    /** Los tres valores de [AMP_GAIN_SW]: `00` Low, `01` Middle, `02` High. */
    val GAIN_SW_VALUES: List<Int> = listOf(0x00, 0x01, 0x02)

    /**
     * **Solo Sw del amplificador, `60 00 00 2B`.** On/off: usa [SWITCH_VALUES].
     *
     * ⚠️ Implementado, sin confirmar. `midi.xml:37492` la nombra `PREAMP: Solo Sw`. Mismo
     * concepto que [BOOST_SOLO_ENABLED] pero a nivel de preamp en vez de Booster, y ese ya
     * está confirmado con audio (2026-09-04) — se espera el mismo comportamiento aquí.
     */
    val AMP_SOLO_ENABLED = Address(0x60, 0x00, 0x00, 0x2B)

    /**
     * **Solo Level del amplificador, `60 00 00 2C`.** Escala directa `00/64` = 0..100:
     * [PANEL_LEVEL_SCALE].
     *
     * ⚠️ Implementado, sin confirmar. `midi.xml:37496` la nombra `PREAMP: Solo Level`. Mismo
     * concepto que [BOOST_SOLO_LEVEL] pero a nivel de preamp.
     */
    val AMP_SOLO_LEVEL = Address(0x60, 0x00, 0x00, 0x2C)

    /**
     * **Solo Sw del amplificador — SEGUNDA CANDIDATA, `60 00 06 14`.** Solo diagnóstico.
     *
     * ⚠️ **No sustituye a [AMP_SOLO_ENABLED]; convive con ella a propósito.** La
     * investigación de "Controles sin perilla física" (CLAUDE.md §5) encontró **dos**
     * direcciones plausibles para el Solo del amplificador y ninguna fuente que desempatara:
     * esta, en el bloque `panel` (`midi.xml:43514`, `name="Solo"` `desc="Solo"`
     * `customdesc="On/Off"`), y la del bloque PREAMP (`midi.xml:37492`), que es la que está
     * cableada como control normal. **La del PREAMP se probó con el amplificador y no hizo
     * nada**, así que toca instrumentar esta.
     *
     * Es el mismo procedimiento que costó tres candidatas con el nivel de reverb (§5, "Cómo
     * encontrar la dirección de un parámetro"): el proyecto no elige por analogía, prueba las
     * dos y deja que el oído decida.
     *
     * A favor de esta: vive en el bloque `06 xx`, donde están las once perillas y los cinco
     * selectores de color confirmados por audio. En contra: ese mismo bloque contiene
     * [AMP_VARIATION] (`06 5C`), que resultó ser de **solo lectura** — la vecindad no es
     * garantía de nada.
     */
    val AMP_SOLO_ENABLED_PANEL = Address(0x60, 0x00, 0x06, 0x14)

    /**
     * **Solo Level del amplificador — SEGUNDA CANDIDATA, `60 00 06 15`.** Solo diagnóstico.
     *
     * Escala directa `00/64` = 0..100 ([PANEL_LEVEL_SCALE]), igual que su gemela
     * [AMP_SOLO_LEVEL]. `midi.xml:43518` la da como `name="Solo"` `customdesc="Level"` con
     * `range 00/64/00/100`. Ver [AMP_SOLO_ENABLED_PANEL] para por qué hay dos.
     */
    val AMP_SOLO_LEVEL_PANEL = Address(0x60, 0x00, 0x06, 0x15)

    /**
     * **Variación del amplificador (el LED "VARIATION"), `60 00 06 5C` — SOLO LECTURA.**
     *
     * `00` off, `01` on. ✅ **Reporta** correctamente: pulsar el botón físico de variación
     * actualiza el valor al instante. ❌ **No acepta escritura**: confirmado con el
     * amplificador el 2026-09-03.
     *
     * El síntoma es característico y vale la pena saber reconocerlo: al mover el switch, la
     * UI se encendía y **volvía sola a apagado un instante después**. Eso no era un fallo del
     * control — era la app funcionando bien. La escritura optimista pone la caché en `01`, el
     * amplificador ignora el SET y sigue reportando su estado real `00` por esta misma
     * dirección, y el camino de mensajes espontáneos lo aplica. **Un valor que rebota solo es
     * la firma de una dirección de solo reporte**, y solo se ve porque el edit mode y la
     * actualización desde el amp están cableados.
     *
     * `midi.xml:44107-44110` la etiqueta `abbr="led state"` — el estado de un LED, no un
     * control—, que en retrospectiva ya lo decía. Lo mismo cabe esperar de `06 5D`–`06 61`,
     * los otros cinco `led state` del mismo bloque.
     *
     * **La variación se cambia por [AMP_TYPE_FULL]**, eligiendo el `Var [...]` de la categoría
     * actual (`0x1C`–`0x20`): ver [AmpCategory.typeValue]. Es un caso real de "se lee en una
     * dirección y se escribe en otra" — el patrón que se descartó para el reverb por ser una
     * suposición. Aquí no se supone: está medido en las dos direcciones.
     *
     * Fuentes de la dirección: `reference/FxFloorboard/midi.xml:44107-44110`,
     * `reference/TuxKatana/doc/Adresses.txt:42-43` y `amplifier.yaml:4` (`am_var_sw`).
     */
    val AMP_VARIATION = Address(0x60, 0x00, 0x06, 0x5C)

    /**
     * **Selector de color (verde/rojo/amarillo) de cada efecto.**
     *
     * ✅ Confirmado con el amplificador (2026-09-03) en los cinco: cambiar el chip cambia el
     * tipo de efecto, y pulsar el botón físico actualiza la app.
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
     * 123` y los `_bank_sel` de los YAML de `reference/TuxKatana/params/`. Era la mejor
     * documentación que había tenido ninguna dirección del proyecto antes de probarla, y esta
     * vez el hardware le dio la razón a las tres.
     */
    val BOOST_COLOR = Address(0x60, 0x00, 0x06, 0x39)

    /** Ver [BOOST_COLOR]. `mo_bank_sel` en `mod.yaml:6`. ✅ Confirmado. */
    val MOD_COLOR = Address(0x60, 0x00, 0x06, 0x3A)

    /** Ver [BOOST_COLOR]. `fx_bank_sel` en `fx.yaml:7`. ✅ Confirmado. */
    val FX_COLOR = Address(0x60, 0x00, 0x06, 0x3B)

    /** Ver [BOOST_COLOR]. `de_bank_sel` en `delay.yaml:28`. ✅ Confirmado. */
    val DELAY_COLOR = Address(0x60, 0x00, 0x06, 0x3C)

    /** Ver [BOOST_COLOR]. `re_bank_sel` en `reverb.yaml:7`. ✅ Confirmado. */
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
     * **On/off de cada efecto.**
     *
     * ✅ Confirmado con el amplificador (2026-09-03) en los cinco, y con ellos la suposición
     * del sentido de los valores: `00` apaga, `01` enciende. También reportan.
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
     * Son direcciones "bajas", fuera del bloque `06 5x`, y funcionan: junto con
     * [AMP_TYPE_FULL] son la prueba de que el bloque alto no tenía nada de especial más allá
     * de ser el de las perillas del panel.
     *
     * De paso queda resuelta una duda que estaba anotada aquí: `Adresses.txt:81` anota
     * `[00|01] # [ON|OFF]`, que leído en ese orden sugeriría `00` = ON. **Es al revés**, como
     * en todo lo demás.
     */
    val BOOST_ENABLED = Address(0x60, 0x00, 0x00, 0x10)

    /** Ver [BOOST_ENABLED]. ✅ Confirmado. */
    val MOD_ENABLED = Address(0x60, 0x00, 0x01, 0x00)

    /** Ver [BOOST_ENABLED]. ✅ Confirmado. */
    val FX_ENABLED = Address(0x60, 0x00, 0x03, 0x00)

    /** Ver [BOOST_ENABLED]. ✅ Confirmado. */
    val DELAY_ENABLED = Address(0x60, 0x00, 0x05, 0x00)

    /** Ver [BOOST_ENABLED]. ✅ Confirmado; es la única que `Adresses.txt` no menciona. */
    val REVERB_ENABLED = Address(0x60, 0x00, 0x05, 0x40)

    // --- Tipo de efecto por slot de color (CLAUDE.md §5.2) --------------------------------
    //
    // ✅ **Resuelto con el amplificador el 2026-09-03, empezando por Booster**: de las dos
    // candidatas, la que manda es la de **tipo activo** (la "baja"). Un SET ahí cambia el
    // sonido, se corresponde con el color encendido en el panel, y la sincronización va en las
    // dos direcciones: cambiar el color en el amplificador actualiza la app y al revés.
    //
    // Las direcciones `06 xx` por color **no** hicieron falta para escribir. Se conservan
    // registradas porque vienen en el dump y reportan, así que dan gratis el contenido de los
    // tres slots — que es justo lo que hará falta para editar presets.

    /**
     * **Tipo de Booster activo, `60 00 00 11`.** ✅ **Confirmado**: lectura y escritura.
     *
     * Uno de los 23 valores de [BoostType]. Refleja el tipo del **color seleccionado**, y esa
     * relación estaba anotada antes de probarla: `reference/TuxKatana/doc/Adresses.txt:72-74`
     * apunta `60 00 00 11: [0A|0B|0E]` observándolo en su propio amplificador. También la dan
     * `booster.yaml:3` (`bo_type`) y `midi.xml:37113-37137`.
     */
    val BOOST_TYPE_ACTIVE = Address(0x60, 0x00, 0x00, 0x11)

    /**
     * **Tipo de MOD activo, `60 00 01 01`.** ⚠️ **Sin confirmar**, pero es la gemela exacta de
     * [BOOST_TYPE_ACTIVE], que sí lo está.
     *
     * Uno de los 31 valores de [ModFxType]. Fuentes: `mod.yaml:3` (`mo_type`) y
     * `midi.xml:37924-37956`. `Adresses.txt:86` trae la misma observación que para Booster —
     * `60 00 01 01 -> [1D|14|13]` con los tres colores—, así que el patrón "la baja es la que
     * manda" tiene aquí el mismo aval documental que tuvo Booster antes de confirmarse.
     */
    val MOD_TYPE_ACTIVE = Address(0x60, 0x00, 0x01, 0x01)

    /**
     * **Tipo de FX activo, `60 00 03 01`.** ⚠️ **Sin confirmar**, misma estructura que
     * [MOD_TYPE_ACTIVE] y el mismo catálogo [ModFxType].
     *
     * Fuentes: `fx.yaml:3` (`fx_type`) y el bloque `LSB 03 "FX2"` de `midi.xml`.
     */
    val FX_TYPE_ACTIVE = Address(0x60, 0x00, 0x03, 0x01)

    /**
     * **Tipo de Delay 1 activo, `60 00 05 01`.** ⚠️ **Sin confirmar**, pero es la gemela
     * exacta de [BOOST_TYPE_ACTIVE], que sí lo está.
     *
     * Uno de los 11 valores de [DelayType]. Fuentes: `delay.yaml:3` (`de_type`) y el bloque
     * `LSB 05 "DD-RV-PDL"` de `midi.xml`, `DATA value="01" desc="Delay 1" customdesc="Type"`.
     */
    val DELAY_TYPE_ACTIVE = Address(0x60, 0x00, 0x05, 0x01)

    /**
     * **Tipo de Reverb activo, `60 00 05 41`.** ⚠️ **Sin confirmar**, misma estructura que
     * [DELAY_TYPE_ACTIVE] y el mismo bloque `LSB 05`.
     *
     * Uno de los 7 valores de [ReverbType]. Fuentes: `reverb.yaml:3` (`re_type`) y
     * `midi.xml`, `DATA value="41" desc="Reverb" customdesc="Type"`.
     */
    val REVERB_TYPE_ACTIVE = Address(0x60, 0x00, 0x05, 0x41)

    /**
     * **Tipo asignado a cada slot de color**, por efecto. ⚠️ **Solo reportan; no se usan para
     * escribir** — la escritura va por las direcciones de tipo activo de arriba.
     *
     * Bloque contiguo y regular, tres direcciones por efecto en orden verde/rojo/amarillo,
     * indexadas por el valor de [EffectColor] (`00|01|02`):
     *
     * | Efecto | Verde | Rojo | Amarillo |
     * | --- | --- | --- | --- |
     * | Booster | `60 00 06 24` | `06 25` | `06 26` |
     * | Mod | `60 00 06 27` | `06 28` | `06 29` |
     * | FX | `60 00 06 2A` | `06 2B` | `06 2C` |
     * | Delay 1 | `60 00 06 2D` | `06 2E` | `06 2F` |
     * | Reverb | `60 00 06 30` | `06 31` | `06 32` |
     *
     * El color **no es un efecto, es un slot con su propio tipo**, y eso es por preset. Dos
     * fuentes de Mk2 coinciden: los `*_type_G/R/Y` de `booster.yaml:11-13`, `mod.yaml:4-6`,
     * `fx.yaml:4-6`, `delay.yaml:25-27` y `reverb.yaml:4-6`, y `midi.xml:43567-43897`, que
     * nombra cada dirección con su efecto y su color explícitos
     * (`desc="Booster" customdesc="GREEN"`).
     *
     * El bloque sigue con Delay 2 (`06 33`–`35`) y el modo RV/DD2 (`06 36`–`38`); ver
     * CLAUDE.md §5.2.
     */
    val BOOST_TYPE_BY_COLOR: List<Address> = colorSlots(0x24)

    /** Ver [BOOST_TYPE_BY_COLOR]. `mo_type_G/R/Y` en `mod.yaml:4-6`. */
    val MOD_TYPE_BY_COLOR: List<Address> = colorSlots(0x27)

    /** Ver [BOOST_TYPE_BY_COLOR]. `fx_type_G/R/Y` en `fx.yaml:4-6`. */
    val FX_TYPE_BY_COLOR: List<Address> = colorSlots(0x2A)

    /** Ver [BOOST_TYPE_BY_COLOR]. `de_type_G/R/Y` en `delay.yaml:25-27`. */
    val DELAY_TYPE_BY_COLOR: List<Address> = colorSlots(0x2D)

    /** Ver [BOOST_TYPE_BY_COLOR]. `re_type_G/R/Y` en `reverb.yaml:4-6`. */
    val REVERB_TYPE_BY_COLOR: List<Address> = colorSlots(0x30)

    /** Valores de un on/off: `00` off, `01` on. ✅ Confirmado (2026-09-03). */
    val SWITCH_VALUES: List<Int> = listOf(0x00, 0x01)

    /** Payload de "encendido" para los on/off. */
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

    // --- Parámetros internos de Booster, `60 00 00 12`–`18` (CLAUDE.md §5.2) --------------
    //
    // ⚠️ Implementados, **pendientes de confirmar con audio** — ver BACKLOG.md, "Pendiente
    // por probar". Dos fuentes de Mk2 coinciden en las siete direcciones:
    // `reference/TuxKatana/params/booster.yaml:4-9` y
    // `reference/FxFloorboard/midi.xml:37109-37304` (el bloque `PRE`, `DATA value="1x"`).
    //
    // `60 00 00 19`–`1E` (Custom Type + Bottom/Top/Low/High/Character del modo "pedal
    // custom") quedan **sin implementar a propósito**: es el modo menos prioritario y tiene
    // su propio sub-catálogo (`midi.xml:37280-37304`).

    /**
     * **Drive, `60 00 00 12`.** Antes documentada como "alternativa baja de [BOOST_LEVEL],
     * sin probar" — con el bloque interno completo entendido, no es una alternativa a la
     * perilla del panel, es el parámetro de distorsión del propio Booster.
     *
     * Escala directa `00..78` (0..120): [BOOST_DRIVE_SCALE].
     */
    val BOOST_DRIVE = Address(0x60, 0x00, 0x00, 0x12)

    /** Rango de [BOOST_DRIVE]: `00/78/0/120`, directo. */
    val BOOST_DRIVE_SCALE: LevelScale = LevelScale.direct(0..120)

    /** **Bottom, `60 00 00 13`.** Escala centrada `00/64/-50/+50`: [CENTERED_TRIM_SCALE]. */
    val BOOST_BOTTOM = Address(0x60, 0x00, 0x00, 0x13)

    /** **Tone, `60 00 00 14`.** Misma escala que [BOOST_BOTTOM]. */
    val BOOST_TONE = Address(0x60, 0x00, 0x00, 0x14)

    /**
     * Escala compartida de los recortes de agudos/graves centrados en cero: `raw = display +
     * 50`. Además de [BOOST_BOTTOM]/[BOOST_TONE], es la misma forma que tendrán Bottom/Top/
     * Low/High/Character del modo custom (`60 00 00 1A`–`1E`) el día que se implementen.
     */
    val CENTERED_TRIM_SCALE: LevelScale = LevelScale.centered(50)

    /** **Solo Sw, `60 00 00 15`.** On/off: usa [SWITCH_VALUES]. */
    val BOOST_SOLO_ENABLED = Address(0x60, 0x00, 0x00, 0x15)

    /** **Solo Level, `60 00 00 16`.** Escala directa `00/64` = 0..100: [PANEL_LEVEL_SCALE]. */
    val BOOST_SOLO_LEVEL = Address(0x60, 0x00, 0x00, 0x16)

    /** **Effect Level, `60 00 00 17`.** Escala directa `00/64` = 0..100: [PANEL_LEVEL_SCALE]. */
    val BOOST_EFFECT_LEVEL = Address(0x60, 0x00, 0x00, 0x17)

    /** **Direct Mix, `60 00 00 18`.** Escala directa `00/64` = 0..100: [PANEL_LEVEL_SCALE]. */
    val BOOST_DIRECT_MIX = Address(0x60, 0x00, 0x00, 0x18)

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
     * ⚠️ Alternativas "bajas" de Delay, **que nunca hicieron falta probar**: `60 00 05 06`
     * (`de_effect_lvl`) y `60 00 05 04` (`de_feedback_lvl`, que `Adresses.txt:127` marca con
     * `?`). Se conservan por si Delay diera problemas más adelante.
     *
     * ⚠️ **Con los parámetros internos de Delay ya extraídos (ver más abajo), estas dos
     * direcciones dejaron de ser "alternativas sin usar": son exactamente [DELAY_EFFECT_LEVEL]
     * y [DELAY_FEEDBACK], los parámetros internos reales de Delay 1**, no una alternativa a la
     * perilla del panel. Se conserva esta lista para no romper lo que ya la usaba, pero el
     * KDoc queda desactualizado a propósito de "nunca hizo falta probar": si algún día se
     * prueban, es como parámetros internos, no como sustitutos de [DELAY_LEVEL].
     */
    val DELAY_LEVEL_LOW = listOf(
        Address(0x60, 0x00, 0x05, 0x06),
        Address(0x60, 0x00, 0x05, 0x04),
    )

    // --- Parámetros internos fijos de Delay 1 y Reverb (CLAUDE.md §5.2) --------------------
    //
    // ⚠️ Implementados, pendientes de confirmar con audio. Delay y Reverb son "DSP simple"
    // igual que Booster: un único bloque fijo de direcciones, el mismo para cualquier tipo
    // activo — no dependen del tipo activo como Mod/FX. **Fuente única**:
    // `reference/FxFloorboard/midi.xml:42413-42488` (Delay 1, bloque `desc="DD1:"`) y
    // `:42860-42935` (Reverb, bloque `desc="REV:"`/`"REVERB:"`). Ninguno de estos parámetros
    // aparece en `reference/TuxKatana/params/delay.yaml` ni `reverb.yaml` más allá de lo ya
    // implementado (On/Off, Type, color, nivel de panel) ni en `Adresses.txt`.

    /**
     * **Delay Time, `60 00 05 02`–`03` (2 bytes).** Escala directa, milisegundos 1:1:
     * [DELAY_TIME_SCALE].
     *
     * ⚠️ Implementado, **sin confirmar con audio**. `midi.xml:42413-42461` documenta el valor
     * como 16 posiciones del primer byte (`00`..`0F`, el MSB) cada una con un sub-rango del
     * segundo (el LSB) — el mismo esquema MSB×128+LSB que ya usa [ACTIVE_CHANNEL], y que aquí
     * cubre limpiamente `1`..`1999` ms en 15 de los 16 tramos.
     *
     * ⚠️ **Un tramo de la fuente no cuadra, y no bloquea la implementación.** El tramo
     * `MSB=0x0E` documenta crudo `00`..`4F` (80 valores) mostrado como `1792`..`1919` ms (128
     * valores) — un ancho que no coincide, cosa que no le pasa a ningún otro tramo ni al de al
     * lado (`MSB=0x0F`: crudo `00`..`4F` → `1920`..`1999`, ese ancho sí encaja con el crudo).
     * El propio [REVERB_PRE_DELAY], con la misma estructura de 4 tramos, es limpio en los
     * cuatro. La lectura más probable es un error de copia en esa única fila de `midi.xml`
     * (el ancho que falta, 48, es justo lo que sobra si el tope real fuera `7F` en vez de
     * `4F`) y no un cambio real de resolución — pero **es solo una lectura, no una prueba**.
     * Se cablea igual, con [LevelScale.direct] sobre el rango entero `1..2000`, porque el
     * `MidiBytes` de 14 bits ya decodifica cualquier valor del rango sin necesitar una escala
     * nueva; lo que queda pendiente es confirmar con audio específicamente el tramo
     * `1792`-`1999` ms, por si el amplificador de verdad usara ahí una resolución más gruesa.
     */
    val DELAY_TIME = Address(0x60, 0x00, 0x05, 0x02)

    /** Rango de [DELAY_TIME]: `1..2000` ms, directo — ver su KDoc para la salvedad. */
    val DELAY_TIME_SCALE: LevelScale = LevelScale.direct(1..2000)

    /**
     * **Feedback de Delay 1, `60 00 05 04`.** Escala directa `00/64` = 0..100:
     * [PANEL_LEVEL_SCALE].
     *
     * ⚠️ Implementado, sin confirmar. `midi.xml:42465` la nombra `DD1: Feedback`.
     */
    val DELAY_FEEDBACK = Address(0x60, 0x00, 0x05, 0x04)

    /**
     * **High Cut de Delay 1, `60 00 05 05`.** Selector de 15 frecuencias: usa
     * [DelayHighCutFrequency.VALUES].
     *
     * ⚠️ Implementado, sin confirmar. `midi.xml:42468-42482` la nombra `DD1: High Cut`. No
     * confundir con [REVERB_HIGH_CUT]: comparten estructura y casi todos los valores, pero no
     * son el mismo catálogo — ver el KDoc de [ReverbHighCutFrequency].
     */
    val DELAY_HIGH_CUT = Address(0x60, 0x00, 0x05, 0x05)

    /**
     * **Effect Level de Delay 1, `60 00 05 06`.** Escala directa `00/78` = 0..120:
     * [DELAY_EFFECT_SCALE].
     *
     * ⚠️ Implementado, sin confirmar. `midi.xml:42485` la nombra `DD1: Effect`.
     */
    val DELAY_EFFECT_LEVEL = Address(0x60, 0x00, 0x05, 0x06)

    /** Rango de [DELAY_EFFECT_LEVEL]: `00/78/0/120`, directo — misma forma que [BOOST_DRIVE_SCALE]. */
    val DELAY_EFFECT_SCALE: LevelScale = LevelScale.direct(0..120)

    /**
     * **Direct Mix de Delay 1, `60 00 05 07`.** Escala directa `00/64` = 0..100:
     * [PANEL_LEVEL_SCALE].
     *
     * ⚠️ Implementado, sin confirmar. `midi.xml:42488` la nombra `DD1: Direct`.
     */
    val DELAY_DIRECT_MIX = Address(0x60, 0x00, 0x05, 0x07)

    /**
     * **Reverb Time, `60 00 05 42`.** Escala fraccionaria, paso de 0.1 s: [REVERB_TIME_SCALE].
     *
     * ⚠️ Implementado, **sin confirmar con audio** (2026-09-05) — desbloqueado al extender
     * [FractionalLevelScale] (ver su KDoc). `midi.xml:42873` la nombra `REV: Reverb Time` con
     * `range 00/63/0.1/10.0 sec`: crudo `0x00`..`0x63` (0..99) mostrado como `0.1`..`10.0`
     * **segundos**, es decir `mostrado = 0.1 + crudo × 0.1` — el mismo `(crudo + 1) / 10` de
     * siempre, solo que expresado como lo pide `FractionalLevelScale`: el `+1` no es un campo
     * aparte, sale de que `displayRange` empieza en `0.1`, no en `0.0`.
     *
     * Se quedó sin cablear una temporada porque [LevelScale] solo sabe sumar un desplazamiento
     * entero, no dividir, y no podía representar "un paso de 0.1" — la misma anomalía que el
     * Pre Delay de 2x2 Chorus en Mod (CLAUDE.md §5.2, ver [MOD_CHORUS_PRE_DELAY_LOW]).
     */
    val REVERB_TIME = Address(0x60, 0x00, 0x05, 0x42)

    /** Rango de [REVERB_TIME]: crudo `0..99` (`0x00..0x63`), mostrado `0.1..10.0` s, paso `0.1`. */
    val REVERB_TIME_SCALE: FractionalLevelScale =
        FractionalLevelScale(rawRange = 0..0x63, displayRange = 0.1..10.0, step = 0.1)

    /**
     * **Pre Delay de Reverb, `60 00 05 43`–`44` (2 bytes).** Escala directa, milisegundos 1:1:
     * [REVERB_PRE_DELAY_SCALE].
     *
     * ⚠️ Implementado, **sin confirmar con audio**. `midi.xml:42876-42891` documenta el mismo
     * esquema MSB×128+LSB que [DELAY_TIME], pero **sin la irregularidad de aquel**: los 4
     * tramos (`00`..`03`) son limpios de punta a punta —`00`..`7F`→`0`-`127`, ..., y el último
     * `00`..`73`→`384`-`499` con el valor especial `74`→`500` encajando exacto (`384+116=500`).
     * Rango entero `0..500` ms, [LevelScale.direct].
     */
    val REVERB_PRE_DELAY = Address(0x60, 0x00, 0x05, 0x43)

    /** Rango de [REVERB_PRE_DELAY]: `0..500` ms, directo. */
    val REVERB_PRE_DELAY_SCALE: LevelScale = LevelScale.direct(0..500)

    /**
     * **Low Cut de Reverb, `60 00 05 45`.** Selector de 18 frecuencias: usa
     * [ReverbLowCutFrequency.VALUES].
     *
     * ⚠️ Implementado, sin confirmar. `midi.xml:42892-42906` la nombra `REV: Low Cut`.
     */
    val REVERB_LOW_CUT = Address(0x60, 0x00, 0x05, 0x45)

    /**
     * **High Cut de Reverb, `60 00 05 46`.** Selector de 15 frecuencias: usa
     * [ReverbHighCutFrequency.VALUES].
     *
     * ⚠️ Implementado, sin confirmar. `midi.xml:42912-42922` la nombra `REV: High Cut`. Ver el
     * KDoc de [ReverbHighCutFrequency] para la discrepancia con [DELAY_HIGH_CUT] en `0x0A`.
     */
    val REVERB_HIGH_CUT = Address(0x60, 0x00, 0x05, 0x46)

    /**
     * **Density de Reverb, `60 00 05 47`.** Escala directa `00/0A` = 0..10 —**no** 0..100,
     * a diferencia de casi todo lo demás de este bloque: [REVERB_DENSITY_SCALE].
     *
     * ⚠️ Implementado, sin confirmar. `midi.xml:42925` la nombra `REV: Density`.
     */
    val REVERB_DENSITY = Address(0x60, 0x00, 0x05, 0x47)

    /** Rango de [REVERB_DENSITY]: `00/0A/0/10`, directo. */
    val REVERB_DENSITY_SCALE: LevelScale = LevelScale.direct(0..10)

    /**
     * **Direct Mix de Reverb, `60 00 05 49`.** Escala directa `00/64` = 0..100:
     * [PANEL_LEVEL_SCALE].
     *
     * ⚠️ Implementado, sin confirmar. `midi.xml:42931` la nombra `REV: Direct Mix`.
     */
    val REVERB_DIRECT_MIX = Address(0x60, 0x00, 0x05, 0x49)

    // --- Primer parámetro interno de Mod cableado: Pre Delay de 2x2 Chorus (CLAUDE.md §5.2) --
    //
    // ⚠️ Implementado, pendiente de confirmar con audio. A diferencia de Booster/Delay/Reverb
    // ("DSP simple", un bloque fijo para cualquier tipo), Mod es "DSP complejo": cada tipo
    // tiene su propio bloque de direcciones, y estas dos **solo significan "Pre Delay" cuando
    // el tipo activo de Mod es 2x2 Chorus** (`ModFxType.CHORUS`, `0x1D`). Con cualquier otro
    // tipo activo, `60 00 02 3A`/`3E` caen dentro del bloque de ESE otro tipo y significan otra
    // cosa (o nada, si el tipo usa menos direcciones) — la UI condiciona el control a que el
    // tipo activo sea 2x2 Chorus, no lo muestra siempre como Booster/Delay/Reverb.

    /**
     * **Pre Delay de 2x2 Chorus, banda Low, `60 00 02 3A`.** Escala fraccionaria, paso de
     * 0.5 ms: [MOD_CHORUS_PRE_DELAY_SCALE].
     *
     * ⚠️ Implementado, **sin confirmar con audio** (2026-09-05) — desbloqueado al extender
     * [FractionalLevelScale] (ver su KDoc). `midi.xml` documenta `range 00/50/0.0/40.0` para
     * el bloque `MOD 2CE:` de 2x2 Chorus (CLAUDE.md §5.2, tabla de "2x2 Chorus"): crudo
     * `0x00`..`0x50` (0..80) mostrado como `0.0`..`40.0` ms, paso de 0.5 ms.
     *
     * ⚠️ **`midi.xml` repite los nombres `Rate`/`Depth`/`Pre Delay` para las dos bandas sin
     * distinguirlas** — lo único que dice cuál es cuál es la posición respecto a los niveles
     * `3B` Low y `3F` High que cierran cada grupo (CLAUDE.md §5.2). El "banda Low"/"banda High"
     * de este KDoc y el de [MOD_CHORUS_PRE_DELAY_HIGH] es interpretación razonada, no literal
     * de la fuente.
     */
    val MOD_CHORUS_PRE_DELAY_LOW = Address(0x60, 0x00, 0x02, 0x3A)

    /** **Pre Delay de 2x2 Chorus, banda High, `60 00 02 3E`.** Ver [MOD_CHORUS_PRE_DELAY_LOW]. */
    val MOD_CHORUS_PRE_DELAY_HIGH = Address(0x60, 0x00, 0x02, 0x3E)

    /**
     * Rango de [MOD_CHORUS_PRE_DELAY_LOW]/[MOD_CHORUS_PRE_DELAY_HIGH]: crudo `0..80`
     * (`0x00..0x50`), mostrado `0.0..40.0` ms, paso `0.5`.
     */
    val MOD_CHORUS_PRE_DELAY_SCALE: FractionalLevelScale =
        FractionalLevelScale(rawRange = 0..0x50, displayRange = 0.0..40.0, step = 0.5)

    // --- Canal/preset activo ---------------------------------------------------------------
    //
    // ⚠️ Investigado (CLAUDE.md §5.1), **sin confirmar contra el amplificador** (2026-09-03).

    /**
     * **Canal/preset activo, `00 01 00 00`.** Lectura y escritura.
     *
     * ⚠️ **Sin confirmar contra el amplificador.** La dirección y los valores son la
     * conclusión de la investigación documental de CLAUDE.md §5.1, con tres fuentes de Mk2
     * independientes de acuerdo entre sí:
     *  - `reference/TuxKatana/widgets/switcher.py:75-88` indexa un único `ch_num` 1..8 sobre
     *    los dos bancos, prueba de que es un solo campo y no banco+canal separados.
     *  - `reference/TuxKatana/params/config.yaml:8-17` da los 8 valores con su checksum.
     *  - `reference/katana-midi-bridge/globals.py:14-15` fija `CURRENT_PRESET_ADDR` y
     *    `CURRENT_PRESET_LEN = 0x02`.
     *
     * **Es la única dirección de este fichero cuyo dato son 2 bytes, no 1** — de ahí que
     * [dev.alonx3.ktnacontrol.device.KatanaRepository] la registre con `byteWidth = 2`. El
     * byte extra es siempre `00` en las fuentes (el valor entero cabe en el segundo byte), así
     * que no se sabe si un SET de 1 byte también funcionaría; eso solo lo dirá el hardware.
     *
     * Se puede leer por dos vías, sin que hiciera falta elegir una para implementar esto: un
     * GET explícito (`reference/katana-midi-bridge/doc/katana_sysex.txt:180-198`, que exige
     * edit mode, igual que los demás GET de este proyecto) o el reporte espontáneo que ya
     * llega solo con edit mode activo (`reference/TuxKatana/lib/controller.py:95-98`).
     */
    val ACTIVE_CHANNEL = Address(0x00, 0x01, 0x00, 0x00)

    /**
     * Valores de [ACTIVE_CHANNEL]: `0` Panel, `1`..`4` Banco A canales 1-4, `5`..`8` Banco B
     * canales 1-4. Las dos numeraciones física y de Program Change **no coinciden** —el panel
     * es `4` en Program Change, no `0`— pero esto es SysEx, así que no aplica: ver CLAUDE.md
     * §5.1.
     */
    val ACTIVE_CHANNEL_VALUES: List<Int> = (0..8).toList()

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
     * **Nombre del preset en uso, `60 00 00 00`, 16 bytes ASCII.** Paso 1 del guardado
     * (CLAUDE.md §5, "Guardado de presets"); ver [dev.alonx3.ktnacontrol.protocol.PresetSave].
     *
     * Es **la misma dirección que [MEMORY_DUMP]**, y eso no es un descuido: los 16 primeros
     * bytes del bloque efectivo son el nombre, y el dump empieza justo ahí. Tiene nombre propio
     * porque `MEMORY_DUMP` se lee como "el principio del volcado" y aquí se usa como "dónde va
     * el nombre" — pedirle a alguien que escriba un nombre en `MEMORY_DUMP` invita a pensar que
     * se está pisando el dump entero.
     *
     * Tres fuentes coinciden en que el nombre va aquí: `katana_sysex.txt:145-147`
     * ("Send name of amp first"), `renameWidget.cpp:68` (FxFloorboard, que lo escribe como un
     * parámetro más) y `presets_addrs.yaml:1-3` (`UserPatch%PatchName`, `size 16`).
     */
    val CURRENT_PRESET_NAME = MEMORY_DUMP

    /**
     * **Commit del guardado, `7F 00 01 04`.** Paso 2: copia el estado editado al canal cuyo
     * número va en el dato. Ver [dev.alonx3.ktnacontrol.protocol.PresetSave].
     *
     * ✅ Confirmado para el **Mk2** en código, no por analogía con el MK1:
     * `reference/FxFloorboard/patchWriteDialog.cpp:346` manda
     * `F0 41 00 00 00 00 33 12 7F 00 01 04 00 <canal> <checksum> F7`.
     *
     * ⚠️ **Destructivo e irreversible**, y **fire-and-forget**: no hay respuesta que esperar.
     */
    val PRESET_SAVE = Address(0x7F, 0x00, 0x01, 0x04)

    /**
     * El dato de [PRESET_SAVE] son **2 bytes** (`00 xx`), como el canal activo de §5.1.
     *
     * ⚠️ La tabla de §5 lo describió un tiempo como `xx = 01..04`, que es el rango del **MK1**
     * copiado tal cual de `katana_sysex.txt:150-155`, cuando el amplificador tenía cuatro
     * canales. En el Mk2 llega hasta `08`.
     */
    const val PRESET_SAVE_SIZE = 2

    // --- Controles sin perilla física (CLAUDE.md §5) ---------------------------------------
    //
    // ⚠️ Implementados el 2026-09-06, **pendientes de confirmar con audio**. Todos salen de
    // `reference/FxFloorboard/midi.xml`, la única fuente de Mk2 que los cubre: ni
    // `Adresses.txt` ni los `*.yaml` de TuxKatana los mencionan, y `katana-midi-bridge` es MK1
    // (corrobora estructura, nunca direcciones).
    //
    // Su rasgo común es que no tienen perilla en el panel, así que no se pueden descubrir
    // girando algo y mirando qué reporta el amplificador — que es como se confirmaron los once
    // niveles. La prueba tiene que ser al revés: escribir y escuchar.

    /**
     * **Noise Gate On/Off, `60 00 05 66`.** On/off: usa [SWITCH_VALUES].
     *
     * `midi.xml:43022`, bloque `desc="NS:"` — Boss lo llama *Noise Suppressor*; "Noise Gate" es
     * el nombre que usa la spec del MK1.
     *
     * ✅ **La estructura la corrobora el MK1 por partida doble, con otra dirección**:
     * `katana-midi-bridge/parameters/amplifier.json:106-127` define `noiseGate` con
     * `baseAddr [96,0,6,99]` (= `60 00 06 63`), `length: 3` y exactamente estos tres campos.
     * Es el caso de libro de CLAUDE.md §5.2: la estructura transfiere del MK1, la dirección no
     * (`06 63` → `05 66`).
     */
    val NOISE_GATE_ENABLED = Address(0x60, 0x00, 0x05, 0x66)

    /** **Noise Gate Threshold, `60 00 05 67`.** `00/64` = 0..100: [PANEL_LEVEL_SCALE]. */
    val NOISE_GATE_THRESHOLD = Address(0x60, 0x00, 0x05, 0x67)

    /** **Noise Gate Release, `60 00 05 68`.** `00/64` = 0..100: [PANEL_LEVEL_SCALE]. */
    val NOISE_GATE_RELEASE = Address(0x60, 0x00, 0x05, 0x68)

    /** **Contour On/Off, `60 00 06 16`.** On/off: usa [SWITCH_VALUES]. `midi.xml:43521`. */
    val CONTOUR_ENABLED = Address(0x60, 0x00, 0x06, 0x16)

    /**
     * **Contour Select, `60 00 06 17`.** Cuál de los tres slots está activo:
     * [CONTOUR_SELECT_VALUES]. `midi.xml:43525`.
     */
    val CONTOUR_SELECT = Address(0x60, 0x00, 0x06, 0x17)

    /** Los tres valores de [CONTOUR_SELECT]: `00` Contour 1, `01` Contour 2, `02` Contour 3. */
    val CONTOUR_SELECT_VALUES: List<Int> = listOf(0x00, 0x01, 0x02)

    /**
     * **Contour Freq Shift del slot activo, `60 00 06 1A`.** Escala centrada `-50..+50`
     * ([CONTOUR_FREQ_SHIFT_SCALE]). `midi.xml:43544`, `range 00/64/-50/+50`.
     *
     * Es el mismo parámetro que el `Freq Shift` de cada slot ([contourSlot]), visto desde el
     * slot que esté seleccionado — no una dirección aparte con otro significado.
     */
    val CONTOUR_FREQ_SHIFT = Address(0x60, 0x00, 0x06, 0x1A)

    /** Escala de los Freq Shift de Contour: `00/64/-50/+50`, centrada. */
    val CONTOUR_FREQ_SHIFT_SCALE: LevelScale = LevelScale.centered(50)

    /** Los cuatro valores de un `Contour Shape`: `00`..`03`. `midi.xml:50135`. */
    val CONTOUR_SHAPE_VALUES: List<Int> = listOf(0x00, 0x01, 0x02, 0x03)

    /** Cuántos slots de Contour hay. */
    const val CONTOUR_SLOT_COUNT = 3

    /**
     * Las dos direcciones del slot de Contour [slot] (0-based): Shape y Freq Shift.
     *
     * Los tres slots van de 8 en 8 desde `60 00 0F 30` — `midi.xml:50135` (`0F 30`/`31`),
     * `:50150` (`0F 38`/`39`) y `:50165` (`0F 40`/`41`).
     *
     * ⚠️ **Estas seis direcciones caen FUERA del dump** y son las primeras del proyecto que lo
     * hacen. El dump pide `60 00 00 00` + 1920 bytes, o sea hasta `60 00 0E 7F`, y el
     * amplificador real devolvió menos todavía (hasta `60 00 0E 43`, CLAUDE.md §4.4); estas
     * están en el offset 1968-1985. Se poblarán por el **GET individual de respaldo** que
     * `KatanaRepository.loadFromDump` ya hace para lo que el dump no cubre — no es código
     * nuevo, pero sí la primera vez que ese camino es el único que puede funcionar para un
     * control, así que es lo primero a comprobar al probarlos.
     *
     * ⚠️ **Discrepancia de fuentes sin resolver**: la aritmética de offsets de FxFloorboard
     * (`sysxWriter.cpp`) da `0F 2E`/`36`/`3E`, dos menos, para estos tres bloques. Se
     * documentan las de `midi.xml` porque son una afirmación directa (`<DATA value="30" …
     * desc="Contour 1:">`) frente a una aritmética, y porque esa misma aritmética ya falla en
     * otro sitio (`GafcExp1AsgnMinMax`, CLAUDE.md §5 "Formato `.tsl`"). **TBD.**
     */
    fun contourSlot(slot: Int): Pair<Address, Address> {
        require(slot in 0 until CONTOUR_SLOT_COUNT) {
            "el slot de contour debe estar entre 0 y ${CONTOUR_SLOT_COUNT - 1}, era $slot"
        }
        val shape = Address(0x60, 0x00, 0x0F, 0x30 + slot * 8)
        return shape to Address(0x60, 0x00, 0x0F, 0x31 + slot * 8)
    }

    /**
     * **Posición de EQ1 en la cadena, `60 00 06 22`.** Dos valores: [EQ1_POSITION_VALUES].
     *
     * `midi.xml:43559` (`desc="EQ" customdesc="Postion"`, `00` Amp In / `01` Amp Out), y la
     * tabla de destinos de asignación en `:3967` lo nombra literalmente
     * `"Signal chain position: EQ1"`.
     *
     * ⚠️ **Son dos valores, no tres.** Es la diferencia con casi todos los demás selectores del
     * proyecto; el que sí tiene cuatro posiciones (Input / Output / Line Out Only / Speaker Out
     * Only) es el EQ **global**, que vive en otro espacio de direcciones y no es por preset.
     */
    val EQ1_POSITION = Address(0x60, 0x00, 0x06, 0x22)

    /** `00` Amp In, `01` Amp Out. */
    val EQ1_POSITION_VALUES: List<Int> = listOf(0x00, 0x01)

    /**
     * **Posición de EQ2 en la cadena, `60 00 06 19`.** Dos valores: [EQ2_POSITION_VALUES].
     *
     * `midi.xml:43540` (`desc="EQ2:" customdesc="Postion"`), asignación en `:3968`.
     *
     * ⚠️ Las etiquetas dicen lo mismo que las de EQ1 con otras palabras — "PreAmp In" / "Pre
     * Amp Out" contra "Amp In" / "Amp Out" —, y la fuente escribe "Postion" en los dos sitios.
     * Es cosmético: el rango es `00`/`01` en ambos y la tabla de asignación los llama a los dos
     * "Signal chain position".
     */
    val EQ2_POSITION = Address(0x60, 0x00, 0x06, 0x19)

    /** `00` PreAmp In, `01` Pre Amp Out. */
    val EQ2_POSITION_VALUES: List<Int> = listOf(0x00, 0x01)

    /**
     * **Tipo de cadena, `60 00 06 20`.** Siete cadenas predefinidas: [CHAIN_TYPE_VALUES].
     *
     * `midi.xml:43552`, `range 00/06/00/06`. La fuente lo declara como rango y no como lista de
     * opciones con nombre, pero lo que selecciona es una de siete configuraciones fijas, no un
     * nivel continuo — de ahí que se registre como selector (CLAUDE.md §4.3: lo que muestra es
     * una elección, no un número en una escala).
     *
     * Convive con [chainSlot]: este es el control grueso (elegir entre siete cadenas hechas) y
     * el array de veinte es el fino (poner cada bloque donde se quiera).
     */
    val CHAIN_TYPE = Address(0x60, 0x00, 0x06, 0x20)

    /** Las siete cadenas predefinidas de [CHAIN_TYPE]: `00`..`06`. */
    val CHAIN_TYPE_VALUES: List<Int> = (0x00..0x06).toList()

    /**
     * **Posición del loop de send/return, `60 00 06 21`.** `00` Post Amp, `01` Post Reverb.
     * `midi.xml:43555`, asignación en `:3969`.
     */
    val LOOP_POSITION = Address(0x60, 0x00, 0x06, 0x21)

    /** `00` Post Amp, `01` Post Reverb. */
    val LOOP_POSITION_VALUES: List<Int> = listOf(0x00, 0x01)

    /**
     * **Posición del Pedal/FX, `60 00 06 23`.** `00` Input, `01` Post Amp. `midi.xml:43563`.
     */
    val PEDAL_FX_POSITION = Address(0x60, 0x00, 0x06, 0x23)

    /** `00` Input, `01` Post Amp. */
    val PEDAL_FX_POSITION_VALUES: List<Int> = listOf(0x00, 0x01)

    /**
     * La posición [slot] (0-based) de la cadena de efectos, `60 00 06 00`–`06 13`.
     *
     * Cada una es un selector de los 20 identificadores de [ChainBlock], y el conjunto es una
     * **permutación**: el orden de los valores es el orden de la cadena. Ver [ChainBlock] para
     * la verificación de que las 20 posiciones ofrecen el mismo catálogo.
     */
    fun chainSlot(slot: Int): Address {
        require(slot in 0 until ChainBlock.SLOT_COUNT) {
            "la posición de la cadena debe estar entre 0 y ${ChainBlock.SLOT_COUNT - 1}, era $slot"
        }
        return Address(0x60, 0x00, 0x06, 0x00 + slot)
    }

    /**
     * The three colour slots of one effect, starting at [greenByte] in the `60 00 06 xx`
     * block: verde, rojo, amarillo, en el orden de [EffectColor].
     */
    private fun colorSlots(greenByte: Int): List<Address> =
        List(3) { colorValue -> Address(0x60, 0x00, 0x06, greenByte + colorValue) }

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
