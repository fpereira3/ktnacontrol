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
    /**
     * Solo el nombre, sin el valor: lo que cabe bajo una barra vertical de 80 dp
     * (QA 2026-09-09, bloque C).
     *
     * ⚠️ **Va aparte de [labelRes] y no se deriva de él.** `labelRes` es un formato con el valor
     * dentro ("Drive: 42"); quitarle el valor a mano dejaría restos ("Time:  s" en los que
     * llevan unidad). Dos recursos distintos porque son dos textos distintos, no dos formas del
     * mismo.
     */
    @param:StringRes val shortLabelRes: Int,
) {
    GAIN(R.string.debug_connection_gain_level, "gain", R.string.short_gain),
    VOLUME(R.string.debug_connection_volume_level, "volume", R.string.short_volume),
    BASS(R.string.debug_connection_bass_level, "bass", R.string.short_bass),
    MIDDLE(R.string.debug_connection_middle_level, "middle", R.string.short_middle),
    TREBLE(R.string.debug_connection_treble_level, "treble", R.string.short_treble),
    PRESENCE(R.string.debug_connection_presence_level, "presence", R.string.short_presence),
    BOOST(R.string.debug_connection_boost_level, "boost", R.string.short_boost),
    MOD(R.string.debug_connection_mod_level, "mod", R.string.short_mod),
    FX(R.string.debug_connection_fx_level, "fx", R.string.short_fx),
    DELAY(R.string.debug_connection_delay_level, "delay", R.string.short_delay),
    REVERB(R.string.debug_connection_reverb_level, "reverb", R.string.short_reverb),
}
