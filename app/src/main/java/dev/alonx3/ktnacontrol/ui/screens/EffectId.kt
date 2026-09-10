package dev.alonx3.ktnacontrol.ui.screens

import androidx.annotation.StringRes
import dev.alonx3.ktnacontrol.R

/**
 * The five effects that have all three controls: a level knob, a colour button and an on/off.
 *
 * Groups what the amp keeps in three unrelated address families so the screen can draw one
 * card per effect. The level is a [LevelId] because levels are shared with the amp/EQ knobs,
 * which have no colour and no switch.
 *
 * Order is the panel's, same as [LevelId].
 */
enum class EffectId(
    @param:StringRes val labelRes: Int,
    /** The continuous level this effect owns. */
    val level: LevelId,
    /** Plain name for the diagnostics log, which is written in Spanish. */
    val logName: String,
) {
    BOOST(R.string.effect_boost, LevelId.BOOST, "boost"),
    MOD(R.string.effect_mod, LevelId.MOD, "mod"),
    FX(R.string.effect_fx, LevelId.FX, "fx"),
    DELAY(R.string.effect_delay, LevelId.DELAY, "delay"),
    REVERB(R.string.effect_reverb, LevelId.REVERB, "reverb"),
}

/**
 * The selectors that are not levels, keyed the way [LevelId] keys the continuous ones.
 *
 * Confirmation status varies per entry now — see the KDoc of each address in
 * `KatanaAddresses` for the current word. [AMP_CATEGORY], [AMP_TYPE], [AMP_VARIATION] and
 * [ACTIVE_CHANNEL] are confirmed with audio; the three PREAMP additions below are not yet.
 */
enum class SelectorId(val logName: String) {
    /** ✅ Amp category, five positions of the physical knob (`60 00 06 50`). */
    AMP_CATEGORY("amp category"),

    /** ✅ Full amp model, 30 options (`60 00 00 21`). */
    AMP_TYPE("amp type"),

    /** ✅ Variation LED (`60 00 06 5C`), confirmed read-only — see `KatanaAddresses`. */
    AMP_VARIATION("amp variation"),

    /** ✅ Active channel/preset, 9 values (`00 01 00 00`). See CLAUDE.md §5.1. */
    ACTIVE_CHANNEL("active channel"),

    /** ⚠️ Bright on/off, part of the PREAMP block (`60 00 00 29`). Unconfirmed. */
    AMP_BRIGHT("amp bright"),

    /** ⚠️ Gain SW, three positions (`60 00 00 2A`): Low/Middle/High. Unconfirmed. */
    AMP_GAIN_SW("amp gain sw"),

    /** ⚠️ Solo on/off, part of the PREAMP block (`60 00 00 2B`). Unconfirmed. */
    AMP_SOLO("amp solo"),

    /** ⚠️ High Cut of Delay 1's internal block (`60 00 05 05`). Unconfirmed. */
    DELAY_HIGH_CUT("delay high cut"),

    /** ⚠️ Low Cut of Reverb's internal block (`60 00 05 45`). Unconfirmed. */
    REVERB_LOW_CUT("reverb low cut"),

    /** ⚠️ High Cut of Reverb's internal block (`60 00 05 46`). Unconfirmed. */
    REVERB_HIGH_CUT("reverb high cut"),

    // --- Controles sin perilla física (CLAUDE.md §5), 2026-09-06. Ninguno confirmado -------

    /** ⚠️ Noise Gate on/off (`60 00 05 66`). Unconfirmed. */
    NOISE_GATE("noise gate"),

    /** ⚠️ Contour on/off (`60 00 06 16`). Unconfirmed. */
    CONTOUR("contour"),

    /** ⚠️ Cuál de los tres slots de Contour está activo (`60 00 06 17`). Unconfirmed. */
    CONTOUR_SELECT("contour select"),

    /** ⚠️ Posición de EQ1 en la cadena (`60 00 06 22`): **dos** valores, no tres. Unconfirmed. */
    EQ1_POSITION("eq1 position"),

    /** ⚠️ Posición de EQ2 (`60 00 06 19`): dos valores. Unconfirmed. */
    EQ2_POSITION("eq2 position"),

    /** ⚠️ Cuál de las siete cadenas predefinidas está activa (`60 00 06 20`). Unconfirmed. */
    CHAIN_TYPE("chain type"),

    /** ⚠️ Posición del loop de send/return (`60 00 06 21`). Unconfirmed. */
    LOOP_POSITION("loop position"),

    /** ⚠️ Posición del Pedal/FX (`60 00 06 23`). Unconfirmed. */
    PEDAL_FX_POSITION("pedal fx position"),
}

/**
 * Booster's five internal continuous parameters, beyond its panel-knob level
 * ([LevelId.BOOST]) and its type ([SelectorId] has none of these — the type lives on
 * `boostTypeActive` directly). See CLAUDE.md §5.2.
 *
 * ⚠️ **Implemented but unconfirmed against the amplifier** (2026-09-04) — same status as the
 * type itself was before Booster's confirmation. [displayRange] is the same bound
 * `KatanaAddresses`' `LevelScale` gives each one; carrying it here is what lets the slider
 * size itself without the screen learning an address.
 *
 * Custom Type's own five parameters (`60 00 00 1A`–`1E`) are not modelled: that sub-mode is
 * out of scope for now.
 */
enum class BoosterParamId(
    @param:StringRes val labelRes: Int,
    /** The range the slider shows, matching the control's `LevelScale.displayRange`. */
    val displayRange: IntRange,
    val logName: String,
    /** Solo el nombre, para la tira vertical. Ver [LevelId.shortLabelRes]. */
    @param:StringRes val shortLabelRes: Int,
) {
    DRIVE(R.string.booster_drive_level, 0..120, "drive", R.string.short_drive),
    BOTTOM(R.string.booster_bottom_level, -50..50, "bottom", R.string.short_bottom),
    TONE(R.string.booster_tone_level, -50..50, "tone", R.string.short_tone),
    SOLO_LEVEL(R.string.booster_solo_level, 0..100, "solo level", R.string.short_solo_level),
    EFFECT_LEVEL(R.string.booster_effect_level, 0..100, "effect level", R.string.short_effect_level),
    DIRECT_MIX(R.string.booster_direct_mix_level, 0..100, "direct mix", R.string.short_direct_mix),
}

/**
 * Delay 1's four internal continuous parameters, beyond its panel-knob level ([LevelId.DELAY])
 * and its type. Its High Cut is a frequency selector, not a level, so it is not here — see
 * [SelectorId.DELAY_HIGH_CUT]. See CLAUDE.md §5.2.
 *
 * ⚠️ **Implemented but unconfirmed against the amplifier.** Its Time is `1..2000` display units
 * but the raw byte occupies **2 bytes**, same shape as the active channel; the UI does not
 * need to know that, it just gets a wider range than every other level.
 */
enum class DelayParamId(
    @param:StringRes val labelRes: Int,
    val displayRange: IntRange,
    val logName: String,
    /** Solo el nombre, para la tira vertical. Ver [LevelId.shortLabelRes]. */
    @param:StringRes val shortLabelRes: Int,
) {
    TIME(R.string.delay_time, 1..2000, "time", R.string.short_time),
    FEEDBACK(R.string.delay_feedback, 0..100, "feedback", R.string.short_feedback),
    EFFECT_LEVEL(R.string.delay_effect_level, 0..120, "effect level", R.string.short_effect_level),
    DIRECT_MIX(R.string.delay_direct_mix, 0..100, "direct mix", R.string.short_direct_mix),
}

/**
 * Reverb's three internal continuous parameters, beyond its panel-knob level
 * ([LevelId.REVERB]) and its type. Its Low Cut and High Cut are frequency selectors, not
 * levels — see [SelectorId.REVERB_LOW_CUT] / [SelectorId.REVERB_HIGH_CUT]. See CLAUDE.md §5.2.
 *
 * Reverb Time (`60 00 05 42`) and Effect Level (`60 00 05 48`) are **not** here on purpose:
 * Time has a non-standard 0.1s-per-step scale [LevelScale][dev.alonx3.ktnacontrol.protocol.LevelScale]
 * cannot express yet, and Effect Level is the address already proven dead as
 * `REVERB_LEVEL_DERIVED` — see both KDocs in `KatanaAddresses`.
 *
 * ⚠️ **Implemented but unconfirmed against the amplifier.** Pre Delay's raw byte occupies 2
 * bytes, same as [DelayParamId.TIME].
 */
enum class ReverbParamId(
    @param:StringRes val labelRes: Int,
    val displayRange: IntRange,
    val logName: String,
    /** Solo el nombre, para la tira vertical. Ver [LevelId.shortLabelRes]. */
    @param:StringRes val shortLabelRes: Int,
) {
    PRE_DELAY(R.string.reverb_pre_delay, 0..500, "pre delay", R.string.short_pre_delay),
    DENSITY(R.string.reverb_density, 0..10, "density", R.string.short_density),
    DIRECT_MIX(R.string.reverb_direct_mix, 0..100, "direct mix", R.string.short_direct_mix),
}

/**
 * Los tres niveles continuos de los controles **sin perilla física** (CLAUDE.md §5): los dos
 * del Noise Gate y el Freq Shift del Contour activo.
 *
 * Van juntos en un enum, y no dispersos por las tarjetas de efecto, porque no pertenecen a
 * ningún efecto: son del amplificador, y en el panel no tienen dónde vivir. Sus on/off y
 * selectores son [SelectorId]; sus slots de Contour tienen su propio camino ([contourSlots] en
 * el repositorio), porque son tres pares repetidos y no tres parámetros distintos.
 *
 * ⚠️ **Implementados el 2026-09-06, sin confirmar con audio.** Fuente única `midi.xml`.
 */
enum class NoPanelParamId(
    @param:StringRes val labelRes: Int,
    /** El rango que muestra el slider, igual que el `displayRange` de su `LevelScale`. */
    val displayRange: IntRange,
    val logName: String,
    /** Solo el nombre, para la tira vertical. Ver [LevelId.shortLabelRes]. */
    @param:StringRes val shortLabelRes: Int,
) {
    NOISE_GATE_THRESHOLD(R.string.noise_gate_threshold, 0..100, "noise gate threshold", R.string.short_threshold),
    NOISE_GATE_RELEASE(R.string.noise_gate_release, 0..100, "noise gate release", R.string.short_release),

    /**
     * ⚠️ El Freq Shift del Contour **activo** (`60 00 06 1A`), centrado en cero — no confundir
     * con el de cada slot, que vive fuera del dump y va por [contourSlots].
     */
    CONTOUR_FREQ_SHIFT(R.string.contour_freq_shift, -50..50, "contour freq shift", R.string.short_freq_shift),
}
