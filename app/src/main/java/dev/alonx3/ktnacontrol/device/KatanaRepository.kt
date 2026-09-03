package dev.alonx3.ktnacontrol.device

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.AmpCategory
import dev.alonx3.ktnacontrol.protocol.AmpType
import dev.alonx3.ktnacontrol.protocol.EffectColor
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.RolandMessage
import dev.alonx3.ktnacontrol.protocol.RolandSysEx
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
        parameter(KatanaAddresses.GAIN_LEVEL, KatanaAddresses.GAIN_LEVEL_RANGE)

    /** Volume, `0..100`. ✅ Confirmado por oído, incluida la perilla física. */
    val volumeLevel: KatanaParameter =
        parameter(KatanaAddresses.VOLUME_LEVEL, KatanaAddresses.VOLUME_LEVEL_RANGE)

    /** Bass, `0..100`. ✅ Confirmado por oído, incluida la perilla física. */
    val bassLevel: KatanaParameter =
        parameter(KatanaAddresses.BASS_LEVEL, KatanaAddresses.BASS_LEVEL_RANGE)

    /** Middle, `0..100`. ✅ Confirmado por oído, incluida la perilla física. */
    val middleLevel: KatanaParameter =
        parameter(KatanaAddresses.MIDDLE_LEVEL, KatanaAddresses.MIDDLE_LEVEL_RANGE)

    /** Treble, `0..100`. ✅ Confirmado por oído, incluida la perilla física. */
    val trebleLevel: KatanaParameter =
        parameter(KatanaAddresses.TREBLE_LEVEL, KatanaAddresses.TREBLE_LEVEL_RANGE)

    /**
     * Reverb level, `0..100`. ✅ The only one confirmed by ear so far: writing here changes
     * the sound, and the front-panel knob reports back on this same address.
     */
    val reverbLevel: KatanaParameter =
        parameter(KatanaAddresses.REVERB_LEVEL, KatanaAddresses.REVERB_LEVEL_RANGE)

    /**
     * Presence, `0..100`. ✅ Confirmado por oído: el slider cambia el brillo del sonido y la
     * perilla física reporta por la misma dirección. El **rango** sigue siendo una suposición
     * — ver [KatanaAddresses.PRESENCE_LEVEL_RANGE].
     */
    val presenceLevel: KatanaParameter =
        parameter(KatanaAddresses.PRESENCE_LEVEL, KatanaAddresses.PRESENCE_LEVEL_RANGE)

    /** Boost, `0..100`. ✅ Confirmado por oído, incluida la perilla física. */
    val boostLevel: KatanaParameter =
        parameter(KatanaAddresses.BOOST_LEVEL, KatanaAddresses.BOOST_LEVEL_RANGE)

    /** Mod, `0..100`. ✅ Confirmado por oído, incluida la perilla física. */
    val modLevel: KatanaParameter =
        parameter(KatanaAddresses.MOD_LEVEL, KatanaAddresses.MOD_LEVEL_RANGE)

    /** FX, `0..100`. ✅ Confirmado por oído, incluida la perilla física. */
    val fxLevel: KatanaParameter =
        parameter(KatanaAddresses.FX_LEVEL, KatanaAddresses.FX_LEVEL_RANGE)

    /** Delay, `0..100`. ✅ Confirmado por oído, incluida la perilla física. */
    val delayLevel: KatanaParameter =
        parameter(KatanaAddresses.DELAY_LEVEL, KatanaAddresses.DELAY_LEVEL_RANGE)

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
    private fun parameter(address: Address, range: IntRange): KatanaParameter =
        KatanaParameter(
            address = address,
            range = range,
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
    private fun selector(address: Address, options: List<Int>): KatanaEnumParameter =
        KatanaEnumParameter(
            address = address,
            options = options,
            link = link,
            scope = scope,
            onDiagnostic = onDiagnostic,
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
        val handled = controls.any { control -> control.applyIncoming(data) }
        if (!handled) {
            onDiagnostic("entrante ${data.address} sin control asociado")
        }
    }

    /** Stops listening and drops pending writes. Call when the connection goes away. */
    fun close() {
        listener.cancel()
        controls.forEach { control -> control.close() }
    }
}
