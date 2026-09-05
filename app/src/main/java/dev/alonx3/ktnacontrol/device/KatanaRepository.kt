package dev.alonx3.ktnacontrol.device

import dev.alonx3.ktnacontrol.device.model.AmpState
import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.AmpCategory
import dev.alonx3.ktnacontrol.protocol.AmpType
import dev.alonx3.ktnacontrol.protocol.BoostType
import dev.alonx3.ktnacontrol.protocol.DelayHighCutFrequency
import dev.alonx3.ktnacontrol.protocol.DelayType
import dev.alonx3.ktnacontrol.protocol.EffectColor
import dev.alonx3.ktnacontrol.protocol.FractionalLevelScale
import dev.alonx3.ktnacontrol.protocol.LevelScale
import dev.alonx3.ktnacontrol.protocol.MemoryDump
import dev.alonx3.ktnacontrol.protocol.ModFxType
import dev.alonx3.ktnacontrol.protocol.ReverbHighCutFrequency
import dev.alonx3.ktnacontrol.protocol.ReverbLowCutFrequency
import dev.alonx3.ktnacontrol.protocol.ReverbType
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.RolandMessage
import dev.alonx3.ktnacontrol.protocol.RolandSysEx
import dev.alonx3.ktnacontrol.protocol.blockReplyIn
import dev.alonx3.ktnacontrol.protocol.sendAndCollectUntilQuiet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
