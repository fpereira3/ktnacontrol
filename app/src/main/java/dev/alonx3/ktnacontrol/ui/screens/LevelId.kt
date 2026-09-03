package dev.alonx3.ktnacontrol.ui.screens

import androidx.annotation.StringRes
import dev.alonx3.ktnacontrol.R

/**
 * The continuous levels the app controls, in the order they sit on the amp's front panel —
 * which is also the order of their addresses, `60 00 06 51` … `60 00 06 5B`.
 *
 * `60 00 06 50` (Amp Type) is deliberately absent: it is a selector, not a level, and has
 * its own control. See `KatanaAddresses.AMP_TYPE_PANEL` and `EffectId` for the rest of the
 * per-effect controls (colour and on/off).
 *
 * Exists so the screen and the ViewModel stop growing a `StateFlow` + two handlers + a slider
 * per parameter: they all key off this instead. Adding the seventh is one entry here plus one
 * line in `KatanaRepository`.
 *
 * The UI never learns the addresses — that stays in `protocol/` (CLAUDE.md §4.2).
 */
enum class LevelId(
    /** Label with the value substituted in, e.g. `Reverb: 42`. */
    @param:StringRes val labelRes: Int,
    /** Plain name for the diagnostics log, which is written in Spanish. */
    val logName: String,
) {
    GAIN(R.string.debug_connection_gain_level, "gain"),
    VOLUME(R.string.debug_connection_volume_level, "volume"),
    BASS(R.string.debug_connection_bass_level, "bass"),
    MIDDLE(R.string.debug_connection_middle_level, "middle"),
    TREBLE(R.string.debug_connection_treble_level, "treble"),
    PRESENCE(R.string.debug_connection_presence_level, "presence"),
    BOOST(R.string.debug_connection_boost_level, "boost"),
    MOD(R.string.debug_connection_mod_level, "mod"),
    FX(R.string.debug_connection_fx_level, "fx"),
    DELAY(R.string.debug_connection_delay_level, "delay"),
    REVERB(R.string.debug_connection_reverb_level, "reverb"),
}
