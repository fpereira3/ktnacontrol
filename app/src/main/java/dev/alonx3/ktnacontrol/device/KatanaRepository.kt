package dev.alonx3.ktnacontrol.device

import dev.alonx3.ktnacontrol.device.model.AmpState
import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.AmpCategory
import dev.alonx3.ktnacontrol.protocol.AmpType
import dev.alonx3.ktnacontrol.protocol.BoostType
import dev.alonx3.ktnacontrol.protocol.ChainBlock
import dev.alonx3.ktnacontrol.protocol.DelayHighCutFrequency
import dev.alonx3.ktnacontrol.protocol.DelayType
import dev.alonx3.ktnacontrol.protocol.EffectColor
import dev.alonx3.ktnacontrol.protocol.EqParams
import dev.alonx3.ktnacontrol.protocol.FractionalLevelScale
import dev.alonx3.ktnacontrol.protocol.LevelScale
import dev.alonx3.ktnacontrol.protocol.MemoryDump
import dev.alonx3.ktnacontrol.protocol.MemoryImage
import dev.alonx3.ktnacontrol.protocol.ModFxInternalParams
import dev.alonx3.ktnacontrol.protocol.ParamKind
import dev.alonx3.ktnacontrol.protocol.ParamSpec
import dev.alonx3.ktnacontrol.protocol.PresetSave
import dev.alonx3.ktnacontrol.protocol.ModFxType
import dev.alonx3.ktnacontrol.protocol.ReverbHighCutFrequency
import dev.alonx3.ktnacontrol.protocol.ReverbLowCutFrequency
import dev.alonx3.ktnacontrol.protocol.ReverbType
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.RolandMessage
import dev.alonx3.ktnacontrol.protocol.RolandSysEx
import dev.alonx3.ktnacontrol.protocol.tsl.TslTransfer
import dev.alonx3.ktnacontrol.protocol.tsl.TslTransferPlan
import dev.alonx3.ktnacontrol.protocol.awaitRolandReply
import dev.alonx3.ktnacontrol.protocol.blockReplyIn
import dev.alonx3.ktnacontrol.protocol.sendAndCollectUntilQuiet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Local copy of the amp's state, and the single place that writes to it.
 *
 * Each controllable parameter is a [KatanaParameter], so adding one is a single line here
 * instead of another hand-written copy of StateFlow + GET + SET + subscription.
 *
 * The cache is fed from **two** directions, which is the whole point:
 *  - what the app writes, applied optimistically and coalesced;
 *  - what the amp reports on its own, which only happens with edit mode on.
 *
 * **No echo loop**: messages arriving from the amp update the cache and are never sent back.
 */
/**
 * Ventana de espera del dump: mucho más larga que un GET porque la respuesta son varios
 * mensajes repartidos en varias lecturas del endpoint.
 */
private const val DUMP_WINDOW_MS = 3_000L

/**
 * Margen entre los dos mensajes de un guardado, y antes de leer el resultado.
 *
 * ⚠️ **Criterio propio, no un dato de las fuentes**: ninguna documenta cuánto hay que esperar
 * entre el nombre y el commit (CLAUDE.md §5, "¿Cuánto hay que esperar entre guardados?" → TBD).
 * Se eligen 50 ms por analogía con el único margen que sí está documentado —el de alrededor del
 * edit mode, en `katana_sysex.txt:142-143` y `bankTreeList.cpp:337-340`—, que es una analogía
 * razonada y nada más.
 */
private const val SAVE_SETTLE_MS = 50L

/**
 * Margen entre dos SET seguidos al mandar un preset entero al amplificador.
 *
 * ⚠️ **Criterio propio, no un dato**: ninguna fuente documenta a qué ritmo acepta el
 * amplificador una ráfaga de escrituras (CLAUDE.md §5, "Formato `.tsl`" → TBD). La analogía es
 * la única cadencia que este proyecto ha **medido** contra el hardware: el hueco de ~30 ms
 * entre los mensajes con que el propio amplificador contesta un dump (§4.4). Si ese es el ritmo
 * al que él habla, es un ritmo razonable al que hablarle.
 *
 * No es gratis pero tampoco caro: un preset completo son ~20 mensajes, o sea ~0,6 s.
 */
private const val SEND_CHUNK_GAP_MS = 30L


class KatanaRepository(
    private val link: KatanaLink,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_WRITE_DEBOUNCE_MS,
    /**
     * Diagnostics hook: reports what goes out and which addresses come back unrecognised.
     *
     * Kept on purpose after the reverb investigation. Finding the right address for a
     * parameter took three candidates and only audio told them apart, and the five effects
     * below still have to go through the same thing — see CLAUDE.md §5.
     */
    private val onDiagnostic: (String) -> Unit = {},
) {

    private val controls = mutableListOf<KatanaControl>()

    /**
     * Gain, `0..100`. ✅ Confirmado por oído, incluida la perilla física — pese a que
     * `Adresses.txt` afirmaba lo contrario sobre qué dirección era la de escritura. Ver el
     * KDoc de [KatanaAddresses.GAIN_LEVEL].
     */
    val gainLevel: KatanaParameter =
        parameter(KatanaAddresses.GAIN_LEVEL, KatanaAddresses.PANEL_LEVEL_SCALE)

    /** Volume, `0..100`. ✅ Confirmado por oído, incluida la perilla física. */
    val volumeLevel: KatanaParameter =
        parameter(KatanaAddresses.VOLUME_LEVEL, KatanaAddresses.PANEL_LEVEL_SCALE)

    /** Bass, `0..100`. ✅ Confirmado por oído, incluida la perilla física. */
    val bassLevel: KatanaParameter =
        parameter(KatanaAddresses.BASS_LEVEL, KatanaAddresses.PANEL_LEVEL_SCALE)

    /** Middle, `0..100`. ✅ Confirmado por oído, incluida la perilla física. */
    val middleLevel: KatanaParameter =
        parameter(KatanaAddresses.MIDDLE_LEVEL, KatanaAddresses.PANEL_LEVEL_SCALE)

    /** Treble, `0..100`. ✅ Confirmado por oído, incluida la perilla física. */
    val trebleLevel: KatanaParameter =
        parameter(KatanaAddresses.TREBLE_LEVEL, KatanaAddresses.PANEL_LEVEL_SCALE)

    /**
     * Reverb level, `0..100`. ✅ The only one confirmed by ear so far: writing here changes
     * the sound, and the front-panel knob reports back on this same address.
     */
    val reverbLevel: KatanaParameter =
        parameter(KatanaAddresses.REVERB_LEVEL, KatanaAddresses.EFFECT_LEVEL_SCALE)

    /**
     * Presence, `0..100`. ✅ Confirmado por oído: el slider cambia el brillo del sonido y la
     * perilla física reporta por la misma dirección. El **rango** sigue siendo una suposición
     * — ver [KatanaAddresses.PANEL_LEVEL_SCALE].
     */
    val presenceLevel: KatanaParameter =
        parameter(KatanaAddresses.PRESENCE_LEVEL, KatanaAddresses.PANEL_LEVEL_SCALE)

    /** Boost, `0..100`. ✅ Confirmado por oído, incluida la perilla física. */
    val boostLevel: KatanaParameter =
        parameter(KatanaAddresses.BOOST_LEVEL, KatanaAddresses.EFFECT_LEVEL_SCALE)

    /** Mod, `0..100`. ✅ Confirmado por oído, incluida la perilla física. */
    val modLevel: KatanaParameter =
        parameter(KatanaAddresses.MOD_LEVEL, KatanaAddresses.EFFECT_LEVEL_SCALE)

    /** FX, `0..100`. ✅ Confirmado por oído, incluida la perilla física. */
    val fxLevel: KatanaParameter =
        parameter(KatanaAddresses.FX_LEVEL, KatanaAddresses.EFFECT_LEVEL_SCALE)

    /** Delay, `0..100`. ✅ Confirmado por oído, incluida la perilla física. */
    val delayLevel: KatanaParameter =
        parameter(KatanaAddresses.DELAY_LEVEL, KatanaAddresses.EFFECT_LEVEL_SCALE)

    // --- Selectores. ⚠️ Ninguno confirmado contra el amplificador (2026-09-03) ------------

    /**
     * ⚠️ Tipo de amplificador por **posición de perilla**, 5 opciones. **Sin confirmar.**
     *
     * Ver [KatanaAddresses.AMP_TYPE_PANEL]: es el bloque alto, que acertó once de once veces,
     * así que es la mejor apuesta — pero solo da las cinco categorías.
     */
    val ampCategory: KatanaEnumParameter =
        selector(KatanaAddresses.AMP_TYPE_PANEL, AmpCategory.VALUES)

    /**
     * ⚠️ Tipo de amplificador **completo**, 30 modelos. **Sin confirmar.**
     *
     * Ver [KatanaAddresses.AMP_TYPE_FULL]. Es una dirección "baja" y ninguna baja está
     * confirmada en este proyecto, así que tiene menos probabilidades que [ampCategory] — se
     * cablean las dos para poder distinguirlo en una sola sesión de pruebas.
     */
    val ampType: KatanaEnumParameter =
        selector(KatanaAddresses.AMP_TYPE_FULL, AmpType.VALUES)

    /** ⚠️ LED de variación del amplificador. **Sin confirmar.** */
    val ampVariation: KatanaEnumParameter = switch(KatanaAddresses.AMP_VARIATION)

    /** ⚠️ Color (verde/rojo/amarillo) de Boost. **Sin confirmar.** */
    val boostColor: KatanaEnumParameter =
        selector(KatanaAddresses.BOOST_COLOR, EffectColor.VALUES)

    /** ⚠️ Color de Mod. **Sin confirmar.** */
    val modColor: KatanaEnumParameter =
        selector(KatanaAddresses.MOD_COLOR, EffectColor.VALUES)

    /** ⚠️ Color de FX. **Sin confirmar.** */
    val fxColor: KatanaEnumParameter =
        selector(KatanaAddresses.FX_COLOR, EffectColor.VALUES)

    /** ⚠️ Color de Delay. **Sin confirmar.** */
    val delayColor: KatanaEnumParameter =
        selector(KatanaAddresses.DELAY_COLOR, EffectColor.VALUES)

    /** ⚠️ Color de Reverb. **Sin confirmar.** */
    val reverbColor: KatanaEnumParameter =
        selector(KatanaAddresses.REVERB_COLOR, EffectColor.VALUES)

    /** ⚠️ On/off de Boost. **Sin confirmar**, ni la dirección ni el sentido de los valores. */
    val boostEnabled: KatanaEnumParameter = switch(KatanaAddresses.BOOST_ENABLED)

    /** ⚠️ On/off de Mod. **Sin confirmar.** */
    val modEnabled: KatanaEnumParameter = switch(KatanaAddresses.MOD_ENABLED)

    /** ⚠️ On/off de FX. **Sin confirmar.** */
    val fxEnabled: KatanaEnumParameter = switch(KatanaAddresses.FX_ENABLED)

    /** ⚠️ On/off de Delay. **Sin confirmar.** */
    val delayEnabled: KatanaEnumParameter = switch(KatanaAddresses.DELAY_ENABLED)

    /** ⚠️ On/off de Reverb. **Sin confirmar.** */
    val reverbEnabled: KatanaEnumParameter = switch(KatanaAddresses.REVERB_ENABLED)

    /**
     * ✅ Tipo de **Booster** activo, `60 00 00 11`, 23 opciones ([BoostType]). **Confirmado**
     * con el amplificador: escribir aquí cambia el sonido y se corresponde con el color
     * encendido en el panel, en las dos direcciones (CLAUDE.md §5.2).
     */
    val boostTypeActive: KatanaEnumParameter =
        selector(KatanaAddresses.BOOST_TYPE_ACTIVE, BoostType.VALUES)

    /**
     * ⚠️ Tipo de **MOD** activo, `60 00 01 01`, 31 opciones ([ModFxType]). **Sin confirmar**,
     * pero es la gemela exacta de [boostTypeActive], que sí lo está.
     */
    val modTypeActive: KatanaEnumParameter =
        selector(KatanaAddresses.MOD_TYPE_ACTIVE, ModFxType.VALUES)

    /** ⚠️ Tipo de **FX** activo, `60 00 03 01`. Mismo catálogo que [modTypeActive]. */
    val fxTypeActive: KatanaEnumParameter =
        selector(KatanaAddresses.FX_TYPE_ACTIVE, ModFxType.VALUES)

    /**
     * ⚠️ Tipo de **Delay 1** activo, `60 00 05 01`, 11 opciones ([DelayType]). **Sin
     * confirmar**, pero es la gemela exacta de [boostTypeActive], que sí lo está.
     */
    val delayTypeActive: KatanaEnumParameter =
        selector(KatanaAddresses.DELAY_TYPE_ACTIVE, DelayType.VALUES)

    /**
     * ⚠️ Tipo de **Reverb** activo, `60 00 05 41`, 7 opciones ([ReverbType]). **Sin
     * confirmar**, misma estructura que [delayTypeActive].
     */
    val reverbTypeActive: KatanaEnumParameter =
        selector(KatanaAddresses.REVERB_TYPE_ACTIVE, ReverbType.VALUES)

    /**
     * Tipo asignado a cada **slot de color** de Booster, indexado por [EffectColor].
     *
     * No se usan para escribir —eso va por [boostTypeActive]— pero se registran igual: vienen
     * en el dump y reportan, así que dan gratis el contenido de los tres slots, que es lo que
     * hará falta para editar presets.
     */
    val boostTypeByColor: List<KatanaEnumParameter> =
        KatanaAddresses.BOOST_TYPE_BY_COLOR.map { address ->
            selector(address, BoostType.VALUES)
        }

    // --- Parámetros internos de Booster (CLAUDE.md §5.2) -----------------------------------
    //
    // ✅ Confirmados con audio (2026-09-04). Custom Type y sus cinco parámetros
    // (`60 00 00 19`–`1E`) quedan fuera a propósito: es el modo "pedal custom", menos
    // prioritario, con su propio sub-catálogo — ver KatanaAddresses.

    /** ✅ Drive de Booster, `60 00 00 12`, `0..120`. Confirmado con audio. */
    val boostDrive: KatanaParameter =
        parameter(KatanaAddresses.BOOST_DRIVE, KatanaAddresses.BOOST_DRIVE_SCALE)

    /** ✅ Bottom de Booster, `60 00 00 13`, `-50..+50`. Confirmado con audio. */
    val boostBottom: KatanaParameter =
        parameter(KatanaAddresses.BOOST_BOTTOM, KatanaAddresses.CENTERED_TRIM_SCALE)

    /** ✅ Tone de Booster, `60 00 00 14`, `-50..+50`. Confirmado con audio. */
    val boostTone: KatanaParameter =
        parameter(KatanaAddresses.BOOST_TONE, KatanaAddresses.CENTERED_TRIM_SCALE)

    /** ✅ Solo Sw de Booster, `60 00 00 15`. Confirmado con audio. */
    val boostSoloEnabled: KatanaEnumParameter = switch(KatanaAddresses.BOOST_SOLO_ENABLED)

    /** ✅ Solo Level de Booster, `60 00 00 16`, `0..100`. Confirmado con audio. */
    val boostSoloLevel: KatanaParameter =
        parameter(KatanaAddresses.BOOST_SOLO_LEVEL, KatanaAddresses.PANEL_LEVEL_SCALE)

    /** ✅ Effect Level de Booster, `60 00 00 17`, `0..100`. Confirmado con audio. */
    val boostEffectLevel: KatanaParameter =
        parameter(KatanaAddresses.BOOST_EFFECT_LEVEL, KatanaAddresses.PANEL_LEVEL_SCALE)

    /** ✅ Direct Mix de Booster, `60 00 00 18`, `0..100`. Confirmado con audio. */
    val boostDirectMix: KatanaParameter =
        parameter(KatanaAddresses.BOOST_DIRECT_MIX, KatanaAddresses.PANEL_LEVEL_SCALE)

    // --- Controles restantes del bloque PREAMP (60 00 00 21-2C, CLAUDE.md / BACKLOG.md
    // "Cambiar el tipo de amplificador no recarga nada") -------------------------------------
    //
    // ⚠️ Implementados, pendientes de confirmar con audio. Ya vienen en el dump —el rango
    // `60 00 00 2x` cae dentro de `60 00 00 00` + 1920 B— así que esto es solo cablear el
    // control, no pedir más memoria. Gain/Bass/Middle/Treble/Presence/Volume del preamp NO
    // se duplican aquí: ya están cubiertos por sus direcciones "altas" (`60 00 06 51`–`56`),
    // que son la fuente de verdad confirmada con audio; las bajas siguen documentadas como
    // alias sin usar en KatanaAddresses.

    /** ⚠️ Bright, `60 00 00 29`. **Sin confirmar.** */
    val ampBright: KatanaEnumParameter = switch(KatanaAddresses.AMP_BRIGHT)

    /** ⚠️ Gain SW, `60 00 00 2A`, tres posiciones. **Sin confirmar.** */
    val ampGainSw: KatanaEnumParameter =
        selector(KatanaAddresses.AMP_GAIN_SW, KatanaAddresses.GAIN_SW_VALUES)

    /** ⚠️ Solo Sw del amplificador, `60 00 00 2B`. **Sin confirmar.** */
    val ampSoloEnabled: KatanaEnumParameter = switch(KatanaAddresses.AMP_SOLO_ENABLED)

    /** ⚠️ Solo Level del amplificador, `60 00 00 2C`, `0..100`. **Sin confirmar.** */
    val ampSoloLevel: KatanaParameter =
        parameter(KatanaAddresses.AMP_SOLO_LEVEL, KatanaAddresses.PANEL_LEVEL_SCALE)

    // --- Solo del amplificador: SEGUNDA CANDIDATA, solo diagnóstico ------------------------
    //
    // ⚠️ Estos dos NO sustituyen a `ampSoloEnabled` / `ampSoloLevel`: conviven con ellos a
    // propósito, para poder probar las dos direcciones sin desmontar la que ya está cableada.
    // La del bloque PREAMP (`00 2B`/`2C`) se probó con el amplificador y no hizo nada; esta,
    // del bloque `panel` (`06 14`/`06 15`), es la otra que la investigación dejó sin probar
    // (CLAUDE.md §5, "Controles sin perilla física").
    //
    // Se registran como controles normales —y no como un envío suelto— para que hereden
    // gratis todo lo que hace falta en una prueba de este tipo: el GET, la escritura, la
    // población desde el dump (`06 14`/`06 15` caen dentro) y, sobre todo, **la aplicación de
    // los reportes espontáneos**, que es lo que delata una dirección de solo lectura — el
    // valor que "rebota solo" de `60 00 06 5C` (§5).

    /** ⚠️ Solo Sw, segunda candidata `60 00 06 14`. **Diagnóstico, sin confirmar.** */
    val ampSoloEnabledPanel: KatanaEnumParameter =
        switch(KatanaAddresses.AMP_SOLO_ENABLED_PANEL)

    /** ⚠️ Solo Level, segunda candidata `60 00 06 15`, `0..100`. **Diagnóstico, sin confirmar.** */
    val ampSoloLevelPanel: KatanaParameter =
        parameter(KatanaAddresses.AMP_SOLO_LEVEL_PANEL, KatanaAddresses.PANEL_LEVEL_SCALE)

    // --- Parámetros internos fijos de Delay 1 y Reverb (CLAUDE.md §5.2) --------------------
    //
    // ⚠️ Implementados, pendientes de confirmar con audio. Reverb Time (`60 00 05 42`) y
    // Reverb Effect Level (`60 00 05 48`, la misma dirección que [KatanaAddresses.REVERB_LEVEL_DERIVED],
    // ya probada como no funcional) quedan **fuera a propósito** — ver sus respectivos KDoc en
    // KatanaAddresses.

    /** ⚠️ Time de Delay 1, `60 00 05 02`–`03` (2 bytes), `1..2000` ms. Sin confirmar. */
    val delayTime: KatanaParameter =
        parameter(KatanaAddresses.DELAY_TIME, KatanaAddresses.DELAY_TIME_SCALE, byteWidth = 2)

    /** ⚠️ Feedback de Delay 1, `60 00 05 04`, `0..100`. Sin confirmar. */
    val delayFeedback: KatanaParameter =
        parameter(KatanaAddresses.DELAY_FEEDBACK, KatanaAddresses.PANEL_LEVEL_SCALE)

    /** ⚠️ High Cut de Delay 1, `60 00 05 05`, 15 frecuencias. Sin confirmar. */
    val delayHighCut: KatanaEnumParameter =
        selector(KatanaAddresses.DELAY_HIGH_CUT, DelayHighCutFrequency.VALUES)

    /** ⚠️ Effect Level de Delay 1, `60 00 05 06`, `0..120`. Sin confirmar. */
    val delayEffectLevel: KatanaParameter =
        parameter(KatanaAddresses.DELAY_EFFECT_LEVEL, KatanaAddresses.DELAY_EFFECT_SCALE)

    /** ⚠️ Direct Mix de Delay 1, `60 00 05 07`, `0..100`. Sin confirmar. */
    val delayDirectMix: KatanaParameter =
        parameter(KatanaAddresses.DELAY_DIRECT_MIX, KatanaAddresses.PANEL_LEVEL_SCALE)

    /** ⚠️ Pre Delay de Reverb, `60 00 05 43`–`44` (2 bytes), `0..500` ms. Sin confirmar. */
    val reverbPreDelay: KatanaParameter =
        parameter(
            KatanaAddresses.REVERB_PRE_DELAY,
            KatanaAddresses.REVERB_PRE_DELAY_SCALE,
            byteWidth = 2,
        )

    /** ⚠️ Low Cut de Reverb, `60 00 05 45`, 18 frecuencias. Sin confirmar. */
    val reverbLowCut: KatanaEnumParameter =
        selector(KatanaAddresses.REVERB_LOW_CUT, ReverbLowCutFrequency.VALUES)

    /** ⚠️ High Cut de Reverb, `60 00 05 46`, 15 frecuencias. Sin confirmar. */
    val reverbHighCut: KatanaEnumParameter =
        selector(KatanaAddresses.REVERB_HIGH_CUT, ReverbHighCutFrequency.VALUES)

    /** ⚠️ Density de Reverb, `60 00 05 47`, `0..10`. Sin confirmar. */
    val reverbDensity: KatanaParameter =
        parameter(KatanaAddresses.REVERB_DENSITY, KatanaAddresses.REVERB_DENSITY_SCALE)

    /** ⚠️ Direct Mix de Reverb, `60 00 05 49`, `0..100`. Sin confirmar. */
    val reverbDirectMix: KatanaParameter =
        parameter(KatanaAddresses.REVERB_DIRECT_MIX, KatanaAddresses.PANEL_LEVEL_SCALE)

    /**
     * ⚠️ Time de Reverb, `60 00 05 42`, `0.1..10.0` s con paso de 0.1. Sin confirmar.
     *
     * Es el primer control de este repositorio con [KatanaFractionalParameter]: su valor
     * mostrado no es un `Int` limpio, así que `displayValue`/`setLevel` trabajan en `Double`.
     * Ver [FractionalLevelScale][dev.alonx3.ktnacontrol.protocol.FractionalLevelScale].
     */
    val reverbTime: KatanaFractionalParameter =
        fractionalParameter(KatanaAddresses.REVERB_TIME, KatanaAddresses.REVERB_TIME_SCALE)

    /**
     * ⚠️ Pre Delay de 2x2 Chorus (banda Low), `60 00 02 3A`, `0.0..40.0` ms con paso de 0.5.
     * Sin confirmar.
     *
     * **Solo significa "Pre Delay" cuando el tipo activo de Mod es 2x2 Chorus**
     * (`ModFxType.CHORUS`) — ver el KDoc de
     * [KatanaAddresses.MOD_CHORUS_PRE_DELAY_LOW]. El repositorio registra el control de
     * todos modos, igual que el resto: quién puede editarlo con sentido lo decide la UI.
     */
    val modChorusPreDelayLow: KatanaFractionalParameter = fractionalParameter(
        KatanaAddresses.MOD_CHORUS_PRE_DELAY_LOW,
        KatanaAddresses.MOD_CHORUS_PRE_DELAY_SCALE,
    )

    /** ⚠️ Pre Delay de 2x2 Chorus (banda High), `60 00 02 3E`. Ver [modChorusPreDelayLow]. */
    val modChorusPreDelayHigh: KatanaFractionalParameter = fractionalParameter(
        KatanaAddresses.MOD_CHORUS_PRE_DELAY_HIGH,
        KatanaAddresses.MOD_CHORUS_PRE_DELAY_SCALE,
    )

    // --- Parámetros internos de los 31 tipos de Mod/FX (CLAUDE.md §5.2, ModFxInternalParams) --
    //
    // ⚠️ Implementados, pendientes de confirmar con audio. "DSP complejo": cada tipo activo
    // reutiliza el mismo espacio de direcciones, así que esto es una **tabla**, no 192
    // propiedades con nombre como el resto del fichero — con 31 tipos y ~6 parámetros de
    // media, nombrarlos uno a uno sería tanto código que el patrón dejaría de ayudar a nadie
    // a encontrar nada. `ModFxInternalParams` (protocol/, puro y con tests) es la fuente de
    // verdad de direcciones/rangos; aquí solo se registran como controles.
    //
    // El Pre Delay de 2x2 Chorus (`modChorusPreDelayLow`/`High`, arriba) queda fuera de la
    // tabla a propósito —ya está cableado como [KatanaFractionalParameter], no como los
    // [KatanaParameter]/[KatanaEnumParameter] que salen de aquí— y se mezcla al construir el
    // mapa de Chorus para que la UI no tenga que saber que hay dos mecanismos distintos.

    private fun controlFor(spec: ParamSpec, address: Address): KatanaControl =
        when (val kind = spec.kind) {
            is ParamKind.Direct -> parameter(address, LevelScale.direct(kind.range))
            is ParamKind.Centered -> parameter(address, LevelScale.centered(kind.radius))
            is ParamKind.OffThenOneBased ->
                parameter(address, LevelScale.offThenOneBased(kind.range))
            is ParamKind.TwoByteDirect ->
                parameter(address, LevelScale.direct(kind.range), byteWidth = 2)
            is ParamKind.Fractional -> fractionalParameter(
                address,
                FractionalLevelScale(kind.rawRange, kind.displayRange, kind.step),
            )
            is ParamKind.Enum -> selector(address, kind.values)
        }

    private fun modFxParamsFor(isFx: Boolean): Map<ModFxType, Map<String, KatanaControl>> =
        ModFxInternalParams.byType.mapValues { (type, specs) ->
            val offset = if (isFx) ModFxInternalParams.FX_OFFSET else 0
            val built = specs.associate { spec ->
                spec.label to controlFor(spec, spec.address + offset)
            }
            if (type != ModFxType.CHORUS) return@mapValues built
            // Se mezclan aquí, no se registran de nuevo: son las mismas instancias de arriba,
            // ya en `controls` — duplicarlas crearía dos KatanaControl compitiendo por la
            // misma dirección.
            val preDelayLow = if (isFx) fxChorusPreDelayLow else modChorusPreDelayLow
            val preDelayHigh = if (isFx) fxChorusPreDelayHigh else modChorusPreDelayHigh
            built + mapOf(
                "Pre Delay (banda baja)" to preDelayLow,
                "Pre Delay (banda alta)" to preDelayHigh,
            )
        }

    /** ⚠️ Pre Delay de 2x2 Chorus en **FX**, banda Low, `60 00 04 3A`. Ver [modChorusPreDelayLow]. */
    val fxChorusPreDelayLow: KatanaFractionalParameter = fractionalParameter(
        KatanaAddresses.MOD_CHORUS_PRE_DELAY_LOW + ModFxInternalParams.FX_OFFSET,
        KatanaAddresses.MOD_CHORUS_PRE_DELAY_SCALE,
    )

    /** ⚠️ Pre Delay de 2x2 Chorus en **FX**, banda High, `60 00 04 3E`. Ver [modChorusPreDelayLow]. */
    val fxChorusPreDelayHigh: KatanaFractionalParameter = fractionalParameter(
        KatanaAddresses.MOD_CHORUS_PRE_DELAY_HIGH + ModFxInternalParams.FX_OFFSET,
        KatanaAddresses.MOD_CHORUS_PRE_DELAY_SCALE,
    )

    /**
     * Parámetros internos de **Mod**, por tipo y por nombre. Solo tienen sentido cuando ese
     * tipo es el activo en el slot ([modTypeActive]) — CLAUDE.md §5.2.
     */
    val modInternalParams: Map<ModFxType, Map<String, KatanaControl>> = modFxParamsFor(isFx = false)

    /** Igual que [modInternalParams] pero para **FX** — mismas direcciones `+ 0x0200`. */
    val fxInternalParams: Map<ModFxType, Map<String, KatanaControl>> = modFxParamsFor(isFx = true)

    // --- Controles sin perilla física (CLAUDE.md §5) ---------------------------------------
    //
    // ⚠️ Implementados el 2026-09-06, pendientes de confirmar con audio. Noise Gate, Contour,
    // las posiciones de EQ1/EQ2 en la cadena, los dos bloques de EQ y la cadena de efectos.
    //
    // Los que son pocos y fijos van con nombre, como Booster/Delay/Reverb; los dos bloques de
    // EQ (24 parámetros cada uno) y las 20 posiciones de la cadena van por tabla, con la misma
    // maquinaria que Mod/FX de arriba. El **Solo del amplificador no está aquí**: sigue en
    // instrumentación de diagnóstico aparte, pendiente de decidir entre sus dos candidatas.

    /** ⚠️ Noise Gate On/Off, `60 00 05 66`. Sin confirmar. */
    val noiseGateEnabled: KatanaEnumParameter =
        selector(KatanaAddresses.NOISE_GATE_ENABLED, KatanaAddresses.SWITCH_VALUES)

    /** ⚠️ Noise Gate Threshold, `60 00 05 67`, `0..100`. Sin confirmar. */
    val noiseGateThreshold: KatanaParameter =
        parameter(KatanaAddresses.NOISE_GATE_THRESHOLD, KatanaAddresses.PANEL_LEVEL_SCALE)

    /** ⚠️ Noise Gate Release, `60 00 05 68`, `0..100`. Sin confirmar. */
    val noiseGateRelease: KatanaParameter =
        parameter(KatanaAddresses.NOISE_GATE_RELEASE, KatanaAddresses.PANEL_LEVEL_SCALE)

    /** ⚠️ Contour On/Off, `60 00 06 16`. Sin confirmar. */
    val contourEnabled: KatanaEnumParameter =
        selector(KatanaAddresses.CONTOUR_ENABLED, KatanaAddresses.SWITCH_VALUES)

    /** ⚠️ Cuál de los tres slots de Contour está activo, `60 00 06 17`. Sin confirmar. */
    val contourSelect: KatanaEnumParameter =
        selector(KatanaAddresses.CONTOUR_SELECT, KatanaAddresses.CONTOUR_SELECT_VALUES)

    /**
     * ⚠️ Freq Shift del Contour **activo**, `60 00 06 1A`, escala centrada `-50..+50`.
     *
     * Es el mismo parámetro que el `Freq Shift` del slot que diga [contourSelect], visto desde
     * el slot activo. Se registra igual porque vive en el dump y el de cada slot no
     * ([contourSlots]) — así que este es el que se puebla solo al conectar.
     */
    val contourFreqShift: KatanaParameter =
        parameter(KatanaAddresses.CONTOUR_FREQ_SHIFT, KatanaAddresses.CONTOUR_FREQ_SHIFT_SCALE)

    /** Los dos controles de un slot de Contour. Ver [contourSlots]. */
    class ContourSlot internal constructor(
        /** Forma del contorno, 4 posiciones. */
        val shape: KatanaEnumParameter,
        /** Desplazamiento de frecuencia, `-50..+50`. */
        val freqShift: KatanaParameter,
    )

    /**
     * ⚠️ Los tres slots de Contour, `60 00 0F 30`/`38`/`40`. Sin confirmar.
     *
     * ⚠️ **Los únicos controles del proyecto que caen fuera del dump** (offset 1968-1985, más
     * allá de lo que el amplificador devuelve): se pueblan por el GET individual de respaldo de
     * [loadFromDump], que ya existe y ya funciona para cualquier dirección que el dump no
     * cubra. Ver el KDoc de [KatanaAddresses.contourSlot].
     */
    val contourSlots: List<ContourSlot> = List(KatanaAddresses.CONTOUR_SLOT_COUNT) { slot ->
        val (shapeAddress, freqAddress) = KatanaAddresses.contourSlot(slot)
        ContourSlot(
            shape = selector(shapeAddress, KatanaAddresses.CONTOUR_SHAPE_VALUES),
            freqShift = parameter(freqAddress, KatanaAddresses.CONTOUR_FREQ_SHIFT_SCALE),
        )
    }

    /** ⚠️ Posición de EQ1 en la cadena, `60 00 06 22`: Amp In / Amp Out. Sin confirmar. */
    val eq1Position: KatanaEnumParameter =
        selector(KatanaAddresses.EQ1_POSITION, KatanaAddresses.EQ1_POSITION_VALUES)

    /** ⚠️ Posición de EQ2, `60 00 06 19`: PreAmp In / Pre Amp Out. Sin confirmar. */
    val eq2Position: KatanaEnumParameter =
        selector(KatanaAddresses.EQ2_POSITION, KatanaAddresses.EQ2_POSITION_VALUES)

    private fun eqParamsFor(isEq2: Boolean): Map<String, KatanaControl> {
        val offset = if (isEq2) EqParams.EQ2_OFFSET else 0
        return EqParams.SPECS.associate { spec ->
            spec.label to controlFor(spec, spec.address + offset)
        }
    }

    /**
     * ⚠️ Los 24 parámetros de **EQ1** (`60 00 00 40`–`00 57`), por nombre. Sin confirmar.
     *
     * Los 11 del paramétrico y los 11 del gráfico existen a la vez; cuál suena lo dice
     * `Selection` — ver [EqParams]. Como con Mod/FX, la tabla vive en `protocol/` y aquí solo
     * se registran los controles.
     */
    val eq1Params: Map<String, KatanaControl> = eqParamsFor(isEq2 = false)

    /** ⚠️ Igual que [eq1Params] para **EQ2** — mismas direcciones `+ 0x20`. Sin confirmar. */
    val eq2Params: Map<String, KatanaControl> = eqParamsFor(isEq2 = true)

    /** ⚠️ Cuál de las siete cadenas predefinidas está activa, `60 00 06 20`. Sin confirmar. */
    val chainType: KatanaEnumParameter =
        selector(KatanaAddresses.CHAIN_TYPE, KatanaAddresses.CHAIN_TYPE_VALUES)

    /** ⚠️ Posición del loop de send/return, `60 00 06 21`. Sin confirmar. */
    val loopPosition: KatanaEnumParameter =
        selector(KatanaAddresses.LOOP_POSITION, KatanaAddresses.LOOP_POSITION_VALUES)

    /** ⚠️ Posición del Pedal/FX, `60 00 06 23`. Sin confirmar. */
    val pedalFxPosition: KatanaEnumParameter =
        selector(KatanaAddresses.PEDAL_FX_POSITION, KatanaAddresses.PEDAL_FX_POSITION_VALUES)

    /**
     * ⚠️ Las 20 posiciones de la cadena de efectos, `60 00 06 00`–`06 13`, en orden. Sin
     * confirmar.
     *
     * Cada una es un selector de los 20 [ChainBlock]; el conjunto es una **permutación**, así
     * que escribir un bloque en una posición sin quitarlo de la otra deja la cadena con un
     * duplicado. La app no impone esa invariante en `device/` a propósito —eso sería decidir
     * por el amplificador— pero es lo primero a mirar si la cadena queda rara.
     */
    val chainSlots: List<KatanaEnumParameter> = List(ChainBlock.SLOT_COUNT) { slot ->
        selector(KatanaAddresses.chainSlot(slot), ChainBlock.VALUES)
    }

    /** Ver [boostTypeByColor]. Slots de color de MOD. */
    val modTypeByColor: List<KatanaEnumParameter> =
        KatanaAddresses.MOD_TYPE_BY_COLOR.map { address ->
            selector(address, ModFxType.VALUES)
        }

    /** Ver [boostTypeByColor]. Slots de color de FX. */
    val fxTypeByColor: List<KatanaEnumParameter> =
        KatanaAddresses.FX_TYPE_BY_COLOR.map { address ->
            selector(address, ModFxType.VALUES)
        }

    /** Ver [boostTypeByColor]. Slots de color de Delay 1. */
    val delayTypeByColor: List<KatanaEnumParameter> =
        KatanaAddresses.DELAY_TYPE_BY_COLOR.map { address ->
            selector(address, DelayType.VALUES)
        }

    /** Ver [boostTypeByColor]. Slots de color de Reverb. */
    val reverbTypeByColor: List<KatanaEnumParameter> =
        KatanaAddresses.REVERB_TYPE_BY_COLOR.map { address ->
            selector(address, ReverbType.VALUES)
        }

    /**
     * ⚠️ Canal/preset activo, `00 01 00 00`. **Sin confirmar contra el amplificador.**
     *
     * A diferencia de todo lo demás registrado aquí, su dato son **2 bytes**, no 1 — de ahí el
     * `byteWidth = 2` explícito. Ver el KDoc de [KatanaAddresses.ACTIVE_CHANNEL] y CLAUDE.md
     * §5.1 para la investigación completa. No vive en el dump de `60 00 00 00`, así que
     * siempre se puebla por el GET de respaldo de [loadFromDump], nunca directamente del dump.
     */
    val channel: KatanaEnumParameter = selector(
        address = KatanaAddresses.ACTIVE_CHANNEL,
        options = KatanaAddresses.ACTIVE_CHANNEL_VALUES,
        byteWidth = 2,
    )

    private val listener: Job = scope.launch {
        link.incoming.collect { message -> onIncoming(message) }
    }

    /**
     * Registers a parameter.
     *
     * A line here is only a way to *test* an address, never a claim that it works: the
     * structural analogy already proved wrong once (CLAUDE.md §5), so nothing counts as
     * confirmed until it has been heard.
     */
    private fun parameter(
        address: Address,
        scale: LevelScale,
        byteWidth: Int = 1,
    ): KatanaParameter =
        KatanaParameter(
            address = address,
            scale = scale,
            link = link,
            scope = scope,
            debounceMillis = debounceMillis,
            onDiagnostic = onDiagnostic,
            byteWidth = byteWidth,
        ).also { created -> controls += created }

    /**
     * Registers a fractional-scale parameter — see [KatanaFractionalParameter] and
     * [FractionalLevelScale]. Same debounce as [parameter]: it is still a dragged slider.
     */
    private fun fractionalParameter(
        address: Address,
        scale: FractionalLevelScale,
    ): KatanaFractionalParameter =
        KatanaFractionalParameter(
            address = address,
            scale = scale,
            link = link,
            scope = scope,
            debounceMillis = debounceMillis,
            onDiagnostic = onDiagnostic,
        ).also { created -> controls += created }

    /**
     * Registers a selector: a choice out of a fixed list of raw values.
     *
     * Same machinery as [parameter] — cache, GET, optimistic SET, anti-echo — but without
     * clamping or debounce, because the values have gaps and a tap is not a drag. See
     * [KatanaEnumParameter].
     */
    private fun selector(
        address: Address,
        options: List<Int>,
        byteWidth: Int = 1,
    ): KatanaEnumParameter =
        KatanaEnumParameter(
            address = address,
            options = options,
            link = link,
            scope = scope,
            onDiagnostic = onDiagnostic,
            byteWidth = byteWidth,
        ).also { created -> controls += created }

    /** A selector with just off and on. Its own name because it reads better at the call site. */
    private fun switch(address: Address): KatanaEnumParameter =
        selector(address, KatanaAddresses.SWITCH_VALUES)

    /**
     * Hands an incoming message to whichever parameter owns its address.
     *
     * Anything else — other addresses, replies to queries nobody made, malformed messages —
     * is ignored; the diagnostics screen still logs all of it.
     */
    private fun onIncoming(message: ByteArray) {
        val data = RolandSysEx.parse(message) as? RolandMessage.Data ?: return
        // Un control es como mucho 2 bytes: el canal activo es el único de 2 (CLAUDE.md §5.1),
        // todo lo demás es 1. Un mensaje más largo es un bloque —el dump, los nombres— y no es
        // la actualización de ningún control, así que no se avisa de que "no tiene control
        // asociado": lo normal es que no lo tenga.
        if (data.data.size !in 1..2) return
        val handled = controls.any { control -> control.applyIncoming(data) }
        if (!handled) {
            onDiagnostic("entrante ${data.address} sin control asociado")
        }
    }

    /**
     * Fills every control from **one** memory dump, falling back to a single GET only for
     * what the dump did not cover.
     *
     * Replaces the old opening sequence of one GET per control — 24 round trips in series,
     * each waiting up to 800 ms for its own answer. The amp answers a dump with several
     * messages in one go, and every address the app controls lives inside `60 00 00 00`, so
     * one request is enough for almost all of them.
     *
     * The fallback GETs stay **in series** for the same reason they always were: replies are
     * correlated by address over a shared stream, and firing them at once is a race.
     */
    suspend fun loadFromDump(windowMillis: Long = DUMP_WINDOW_MS): DumpLoad {
        val query = RolandSysEx.get(
            address = KatanaAddresses.MEMORY_DUMP,
            size = KatanaAddresses.MEMORY_DUMP_SIZE,
        )
        // El filtro es lo que impide que un reporte espontáneo en vuelo durante el dump
        // —un cambio de canal, una perilla, un color— se cuele como si fuera un trozo de
        // memoria. Sin él, `applyDumpValue` podía recibir el byte alto de un control de
        // 2 bytes y convertir el canal en Panel. Ver BACKLOG.md, "El estado se
        // desincroniza al cambiar de canal rápido" y "Propuesta de diseño".
        val result = sendAndCollectUntilQuiet(
            messages = link.incoming,
            accept = blockReplyIn(KatanaAddresses.MEMORY_DUMP, KatanaAddresses.MEMORY_DUMP_SIZE),
            timeoutMillis = windowMillis,
        ) {
            link.send(query)
        }
        val dump = MemoryDump.from(result.accepted)

        val missing = controls.filter { control -> !control.applyDumpValue(dump) }
        var recovered = 0
        missing.forEach { control ->
            if (control.read() != null) recovered++
        }

        return DumpLoad(
            state = AmpState.from(dump),
            messages = result.accepted.size + result.rejected.size + result.invalidCount,
            invalidMessages = result.invalidCount,
            dataBytes = dump.dataByteCount,
            fromDump = controls.size - missing.size,
            fromFallbackGet = recovered,
            stillUnknown = missing.size - recovered,
            rejectedDuringWindow = result.rejected.groupingBy { it }.eachCount(),
        )
    }

    /**
     * Puebla los controles desde una imagen en memoria, **sin ningún viaje de ida y vuelta**.
     *
     * Es el equivalente offline de [loadFromDump] (CLAUDE.md §4.5) y existe por una razón de
     * peso, no por eficiencia: [loadFromDump] recorre los controles que el dump no cubrió y les
     * hace un GET individual **en serie, cada uno esperando hasta
     * [dev.alonx3.ktnacontrol.protocol.DEFAULT_REPLY_TIMEOUT_MS]**. Contra el amplificador eso
     * es un respaldo razonable; contra una imagen en RAM es a la vez **inútil y carísimo**:
     *
     * - **Inútil**, porque el GET de respaldo se contesta desde la misma imagen que acaba de no
     *   tener el valor. Preguntar dos veces a la misma fuente no puede dar una respuesta nueva.
     * - **Carísimo**, porque el proyecto registra [controlCount] controles y un preset nuevo en
     *   blanco no cubre casi ninguno: serían cientos de timeouts de 800 ms **en serie**. Con un
     *   preset vacío eso es del orden de minutos, no de segundos — comprobado colgando un test.
     *
     * Así que aquí no hay respaldo que valga: lo que la imagen no trae se queda en null, que es
     * exactamente lo que significa ("no cubierto", nunca cero, §4.4) y lo que la UI ya sabe
     * enseñar como "—".
     *
     * @return cuántos controles se poblaron.
     */
    fun loadFromImage(image: MemoryImage): Int {
        val dump = image.toDump()
        return controls.count { control -> control.applyDumpValue(dump) }
    }

    /**
     * Lee la memoria del amplificador y la devuelve como imagen editable, para exportarla.
     *
     * Es el mismo dump que usa [loadFromDump] —una sola petición, varios mensajes de vuelta—
     * pero devolviendo **los bytes tal cual** en vez de repartirlos por los controles.
     *
     * ⚠️ **Los bytes, no `AmpState`.** `AmpState` entiende 24 valores y un `.tsl` son 1141
     * bytes: exportar a través del modelo de dominio perdería todo lo que no modela — los
     * internos de Mod/FX, el EQ, la cadena (CLAUDE.md §4.5). Lo que se exporta es la memoria.
     *
     * ⚠️ **Lo que el amplificador no devuelva no estará en la imagen**, y eso es correcto y
     * visible: `TslWriter` omite la clave y lo dice, en vez de rellenar con ceros que se
     * leerían como un ajuste deliberado. El caso conocido son los tres Contour por slot, que
     * caen fuera del rango del dump (§5, "Controles sin perilla física").
     */
    suspend fun exportImage(windowMillis: Long = DUMP_WINDOW_MS): MemoryImage {
        val query = RolandSysEx.get(
            address = KatanaAddresses.MEMORY_DUMP,
            size = KatanaAddresses.MEMORY_DUMP_SIZE,
        )
        val result = sendAndCollectUntilQuiet(
            messages = link.incoming,
            accept = blockReplyIn(KatanaAddresses.MEMORY_DUMP, KatanaAddresses.MEMORY_DUMP_SIZE),
            timeoutMillis = windowMillis,
        ) {
            link.send(query)
        }
        return MemoryImage.from(MemoryDump.from(result.accepted))
    }

    /**
     * ⚠️ **Escribe un preset entero —el de un `.tsl` importado o el de una edición offline— en
     * el búfer de edición del amplificador. Destructivo y sin confirmación.**
     *
     * Es el camino que faltaba para cerrar el `.tsl`: importar, ver y editar no tocan el
     * amplificador; esto sí. Son los SET que construye [TslTransfer], mandados en orden con un
     * margen entre medio ([SEND_CHUNK_GAP_MS], criterio propio).
     *
     * ⚠️ **Qué sobrescribe, dicho con precisión**: el estado *en edición*, no un canal. Lo que
     * el usuario tuviera sin guardar se pierde, y lo que suene después es el preset del fichero
     * **mezclado** con lo que hubiera antes en las direcciones de [TslTransferPlan.skipped] —
     * por eso esa lista se devuelve, no se registra por lo bajo. Para dejarlo fijo en un canal
     * hace falta además un [savePreset], que es otra operación destructiva y aparte.
     *
     * **No toca el edit mode**, igual que [savePreset] y por lo mismo (§4.2): es un ajuste
     * explícito del usuario y encenderlo por detrás sería moverle un interruptor por la espalda.
     * Sin él el amplificador no reporta, así que quien llame a esto sin edit mode no podrá
     * comprobar nada de lo que escriba — la UI es quien lo exige, no esta capa.
     *
     * **No hay confirmación y no se finge que la haya.** Un SET es fire-and-forget (semántica
     * DT1), así que lo único que se sabe es si los mensajes salieron por el cable. La caché de
     * los controles **no se actualiza**: dar por buenos los valores del fichero sería enseñar lo
     * que se pidió, no lo que el amplificador aceptó. Quien quiera saber qué entró de verdad
     * tiene que releer con [loadFromDump] y comparar.
     *
     * ⚠️ **Nada de esto está probado contra el amplificador**, empezando por si acepta un SET de
     * 128 bytes de datos.
     *
     * @param image el preset a escribir, con sus bytes ya colocados por dirección.
     * @return qué se mandó y qué se quedó fuera. Ver [PresetSendResult.verdict].
     */
    suspend fun sendPreset(
        image: MemoryImage,
        gapMillis: Long = SEND_CHUNK_GAP_MS,
        chunkSize: Int = TslTransfer.CHUNK_SIZE,
    ): PresetSendResult {
        val plan = TslTransfer.plan(image, chunkSize)
        var sent = 0
        var bytes = 0
        var failedAt: String? = null

        outer@ for (write in plan.writes) {
            for ((index, message) in write.messages.withIndex()) {
                // El primer margen también cuenta: no hay razón para que el primer mensaje
                // salga pegado a lo último que la app mandara antes.
                if (sent > 0) delay(gapMillis)
                if (!link.send(message)) {
                    // Se corta en seco. Seguir mandando por un cable que acaba de fallar solo
                    // añade escrituras a medias, y el preset ya ha quedado incompleto igual.
                    failedAt = "${write.key} (trozo ${index + 1} de ${write.messages.size})"
                    break@outer
                }
                sent++
                bytes += RolandSysEx.payloadSizeOf(message)
            }
        }

        return PresetSendResult(
            plan = plan,
            messagesSent = sent,
            dataBytesSent = bytes,
            failedAt = failedAt,
        )
    }

    /**
     * Lo que se pudo averiguar de un [sendPreset]. **Ningún campo prueba que el amplificador
     * haya aceptado nada**: solo que los mensajes salieron.
     */
    data class PresetSendResult(
        val plan: TslTransferPlan,
        val messagesSent: Int,
        val dataBytesSent: Int,
        /** El bloque y trozo donde falló el envío, o null si salieron todos. */
        val failedAt: String?,
    ) {
        /** Cuántos bloques se trocearon en más de un SET. `Fx(1)` y `Fx(2)`, en la práctica. */
        val chunkedBlocks: List<String> get() = plan.writes.filter { it.chunked }.map { it.key }

        val complete: Boolean get() = failedAt == null && messagesSent == plan.messageCount

        /** Cómo leerlo en una línea, para el log. Sin afirmar éxito, que nadie puede afirmar. */
        val verdict: String get() = buildString {
            if (!complete) {
                append("envío incompleto: salieron $messagesSent de ${plan.messageCount} ")
                append("mensajes")
                failedAt?.let { append(", falló en $it") }
                append(". El amplificador queda con el preset a medias.")
            } else {
                append("salieron los $messagesSent mensajes ($dataBytesSent bytes) por el cable; ")
                append("el amplificador no confirma un SET, así que esto no prueba que los ")
                append("aceptara.")
            }
            if (plan.skipped.isNotEmpty()) {
                append(" No se escribieron ${plan.skipped.size} bloque(s), que se quedan como ")
                append("estaban: ")
                append(plan.skipped.joinToString("; ") { it.key })
                append(".")
            }
        }
    }

    /** Cuántos controles hay registrados. Para diagnóstico y para los tests. */
    val controlCount: Int get() = controls.size

    /**
     * ⚠️ **Guarda el estado editado en el canal [channel] (`01`..`08`). Destructivo,
     * irreversible y sin confirmación del amplificador** (CLAUDE.md §5, "Guardado de presets").
     *
     * Son los dos mensajes que documenta [PresetSave] —el nombre y después el commit—, con una
     * pausa entre medio: el commit copia lo que haya en el búfer, así que el nombre tiene que
     * haber llegado antes. ⚠️ **Ese margen es criterio propio, no un dato**: ninguna fuente
     * documenta cuánto hay que esperar entre los dos pasos (ni entre dos guardados seguidos).
     * Los ~50 ms que sí están documentados son alrededor del **edit mode**, que es otra cosa.
     *
     * **No toca el edit mode**, aunque la secuencia del MK1 lo ponga como paso 1. Es un ajuste
     * explícito y visible del usuario (§4.2), y encenderlo como efecto secundario de guardar
     * sería moverle un interruptor por la espalda; quien pueda pulsar Guardar ya lo tiene
     * encendido, porque la UI lo exige. Y **tampoco hay aquí ningún gate de edit mode**: esta
     * capa no sabe qué es, igual que con cualquier otro SET.
     *
     * **La verificación es leer el nombre del canal destino**, `10 0N 00 00`, y compararlo con
     * lo que se pidió guardar. Es lo más parecido a una confirmación que hay: si el commit
     * funcionó, la tabla de nombres de preset debería reflejar el nuevo. Un desacuerdo **no
     * demuestra** que el guardado fallara —puede que el amp tarde en actualizar esa tabla, algo
     * que ninguna fuente aclara— pero una coincidencia sí es una señal fuerte de que entró.
     *
     * @param name el nombre a escribir; se recorta, rellena y sanea en [PresetSave.encodeName].
     * @param channel el canal destino, `01`..`08`. Rechaza cualquier otro valor.
     */
    suspend fun savePreset(
        name: String,
        channel: Int,
        settleMillis: Long = SAVE_SETTLE_MS,
    ): PresetSaveResult {
        require(channel in PresetSave.CHANNELS) {
            "el canal destino debe estar entre ${PresetSave.CHANNELS.first()} y " +
                "${PresetSave.CHANNELS.last()}, era $channel"
        }
        val requested = PresetSave.decodeName(PresetSave.encodeName(name))

        val nameSent = link.send(PresetSave.nameMessage(name))
        delay(settleMillis)
        val commitSent = link.send(PresetSave.commitMessage(channel))

        // Sin esto la lectura de vuelta podría adelantarse al propio guardado del amplificador,
        // y un "no coincide" no distinguiría "falló" de "todavía no terminó".
        delay(settleMillis)
        val readBack = awaitRolandReply(
            messages = link.incoming,
            address = KatanaAddresses.presetName(channel),
        ) {
            link.send(
                RolandSysEx.get(
                    KatanaAddresses.presetName(channel),
                    KatanaAddresses.PRESET_NAME_SIZE,
                )
            )
        }

        return PresetSaveResult(
            channel = channel,
            requestedName = requested,
            nameSent = nameSent,
            commitSent = commitSent,
            storedName = readBack?.data?.let(PresetSave::decodeName),
        )
    }

    /**
     * Lo que se pudo averiguar de un [savePreset]. **Ninguno de estos campos prueba que el
     * sonido se haya guardado**: el commit no se confirma, y lo único observable es el nombre.
     */
    data class PresetSaveResult(
        val channel: Int,
        /** El nombre ya saneado, tal y como se mandó. */
        val requestedName: String,
        val nameSent: Boolean,
        val commitSent: Boolean,
        /** Lo que el amp dice tener en `10 0N 00 00` después, o null si no contestó. */
        val storedName: String?,
    ) {
        /** El canal como lo llama el panel: `A1`..`B4`. */
        val channelLabel: String get() = PresetSave.channelLabel(channel)

        /** El amplificador contestó y el nombre coincide: la mejor señal disponible. */
        val nameMatches: Boolean get() = storedName == requestedName

        /** Cómo leerlo en una línea, para el log. */
        val verdict: String get() = when {
            !nameSent || !commitSent -> "algún mensaje no salió por el cable: el guardado no se completó"
            storedName == null ->
                "el amp no contestó al GET del nombre de $channelLabel: sin forma de confirmar nada"
            nameMatches ->
                "$channelLabel dice llamarse \"$storedName\": el nombre entró, así que el commit llegó"
            else ->
                "$channelLabel sigue diciendo \"$storedName\" y se pidió \"$requestedName\": " +
                    "o el guardado no entró, o el amp no actualizó todavía esa tabla"
        }
    }

    /** What one [loadFromDump] achieved, for the diagnostics log. */
    data class DumpLoad(
        val state: AmpState,
        val messages: Int,
        val invalidMessages: Int,
        val dataBytes: Int,
        /** Controls filled straight from the dump — the round trips saved. */
        val fromDump: Int,
        /** Controls the dump missed and a single GET recovered. */
        val fromFallbackGet: Int,
        /** Controls still unknown: neither in the dump nor answered by their GET. */
        val stillUnknown: Int,
        /**
         * Addresses of well-formed messages [blockReplyIn] rejected during the collection
         * window, with how many times each showed up — spontaneous reports that happened to
         * land while the dump was in flight. Empty in the common case.
         */
        val rejectedDuringWindow: Map<Address, Int> = emptyMap(),
    )

    /** Stops listening and drops pending writes. Call when the connection goes away. */
    fun close() {
        listener.cancel()
        controls.forEach { control -> control.close() }
    }
}
