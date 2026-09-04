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
 * ⚠️ **Every one of these is unconfirmed against the amplifier** (2026-09-03). See the KDoc
 * of each address in `KatanaAddresses`.
 */
enum class SelectorId(val logName: String) {
    /** Amp category, five positions of the physical knob (`60 00 06 50`). */
    AMP_CATEGORY("amp category"),

    /** Full amp model, 30 options (`60 00 00 21`). */
    AMP_TYPE("amp type"),

    /** Variation LED (`60 00 06 5C`). */
    AMP_VARIATION("amp variation"),

    /** Active channel/preset, 9 values (`00 01 00 00`). See CLAUDE.md §5.1. */
    ACTIVE_CHANNEL("active channel"),
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
) {
    DRIVE(R.string.booster_drive_level, 0..120, "drive"),
    BOTTOM(R.string.booster_bottom_level, -50..50, "bottom"),
    TONE(R.string.booster_tone_level, -50..50, "tone"),
    SOLO_LEVEL(R.string.booster_solo_level, 0..100, "solo level"),
    EFFECT_LEVEL(R.string.booster_effect_level, 0..100, "effect level"),
    DIRECT_MIX(R.string.booster_direct_mix_level, 0..100, "direct mix"),
}
