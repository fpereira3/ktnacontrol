package dev.alonx3.ktnacontrol.device.model

import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.AmpCategory
import dev.alonx3.ktnacontrol.protocol.AmpType
import dev.alonx3.ktnacontrol.protocol.EffectColor
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.LevelScale
import dev.alonx3.ktnacontrol.protocol.MemoryDump

/**
 * A readable snapshot of everything the app can control, as extracted from one memory dump.
 *
 * **Every field is nullable and null means "the dump did not cover it"**, never "error" and
 * never "zero". The amp skips the ranges where no parameters live, so a parameter simply not
 * being in the answer is a normal outcome — the caller falls back to a single GET for those.
 *
 * Levels are already in **what the UI shows**: the offset of the five effect knobs is applied
 * here, so nothing downstream has to remember it. See [LevelScale].
 *
 * This is the domain view, meant to be read; the mechanical work of pushing the values into
 * the live controls is `KatanaRepository.applyDump`, which walks its own controls instead of
 * this type's fields so that adding a parameter cannot leave it behind.
 */
data class AmpState(
    val gain: Int?,
    val volume: Int?,
    val bass: Int?,
    val middle: Int?,
    val treble: Int?,
    val presence: Int?,
    val ampCategory: AmpCategory?,
    val ampType: AmpType?,
    /** Whether the VARIATION LED is on. Read-only on the amp — see `KatanaAddresses`. */
    val variationOn: Boolean?,
    val boost: EffectState,
    val mod: EffectState,
    val fx: EffectState,
    val delay: EffectState,
    val reverb: EffectState,
) {

    /** Los cinco efectos en el orden del panel, para recorrerlos sin repetir el listado. */
    val effects: List<Pair<String, EffectState>>
        get() = listOf(
            "boost" to boost, "mod" to mod, "fx" to fx, "delay" to delay, "reverb" to reverb,
        )

    /** Cuántos de los 24 parámetros venían en el dump. */
    val knownCount: Int
        get() = listOf(gain, volume, bass, middle, treble, presence).count { it != null } +
            listOf(ampCategory, ampType, variationOn).count { it != null } +
            effects.sumOf { (_, effect) -> effect.knownCount }

    /** Una línea legible para el log de diagnóstico. */
    fun summary(): String = buildString {
        append("amp ")
        append(ampType?.displayName ?: ampCategory?.displayName ?: "?")
        if (variationOn == true) append(" (var)")
        append(" · gain ").append(gain.orUnknown())
        append(" vol ").append(volume.orUnknown())
        append(" · EQ ").append(bass.orUnknown()).append('/').append(middle.orUnknown())
            .append('/').append(treble.orUnknown()).append('/').append(presence.orUnknown())
        effects.forEach { (name, effect) ->
            append(" · ").append(name).append(' ').append(effect.summary())
        }
    }

    private fun Int?.orUnknown(): String = this?.toString() ?: "?"

    companion object {
        /**
         * Reads every address the app knows about out of [dump].
         *
         * Anything the dump does not cover stays null — the amp not sending a range is a fact
         * about the amp's memory layout, not a failure of the parse.
         */
        fun from(dump: MemoryDump): AmpState {
            fun level(address: Address, scale: LevelScale): Int? =
                dump.byteAt(address)?.let(scale::toDisplay)

            fun panel(address: Address) = level(address, KatanaAddresses.PANEL_LEVEL_SCALE)
            fun effectLevel(address: Address) = level(address, KatanaAddresses.EFFECT_LEVEL_SCALE)

            fun effect(level: Address, enabled: Address, color: Address) = EffectState(
                level = effectLevel(level),
                enabled = dump.byteAt(enabled)?.let { it == KatanaAddresses.SWITCH_ON },
                color = dump.byteAt(color)?.let(EffectColor::fromValue),
            )

            return AmpState(
                gain = panel(KatanaAddresses.GAIN_LEVEL),
                volume = panel(KatanaAddresses.VOLUME_LEVEL),
                bass = panel(KatanaAddresses.BASS_LEVEL),
                middle = panel(KatanaAddresses.MIDDLE_LEVEL),
                treble = panel(KatanaAddresses.TREBLE_LEVEL),
                presence = panel(KatanaAddresses.PRESENCE_LEVEL),
                ampCategory = dump.byteAt(KatanaAddresses.AMP_TYPE_PANEL)
                    ?.let(AmpCategory::fromValue),
                ampType = dump.byteAt(KatanaAddresses.AMP_TYPE_FULL)?.let(AmpType::fromValue),
                variationOn = dump.byteAt(KatanaAddresses.AMP_VARIATION)
                    ?.let { it == KatanaAddresses.SWITCH_ON },
                boost = effect(
                    KatanaAddresses.BOOST_LEVEL,
                    KatanaAddresses.BOOST_ENABLED,
                    KatanaAddresses.BOOST_COLOR,
                ),
                mod = effect(
                    KatanaAddresses.MOD_LEVEL,
                    KatanaAddresses.MOD_ENABLED,
                    KatanaAddresses.MOD_COLOR,
                ),
                fx = effect(
                    KatanaAddresses.FX_LEVEL,
                    KatanaAddresses.FX_ENABLED,
                    KatanaAddresses.FX_COLOR,
                ),
                delay = effect(
                    KatanaAddresses.DELAY_LEVEL,
                    KatanaAddresses.DELAY_ENABLED,
                    KatanaAddresses.DELAY_COLOR,
                ),
                reverb = effect(
                    KatanaAddresses.REVERB_LEVEL,
                    KatanaAddresses.REVERB_ENABLED,
                    KatanaAddresses.REVERB_COLOR,
                ),
            )
        }
    }
}

/** One effect's three controls. Null on any of them means the dump did not cover it. */
data class EffectState(
    /** `0..100` as shown, with the effect-knob offset already applied. */
    val level: Int?,
    val enabled: Boolean?,
    val color: EffectColor?,
) {
    val knownCount: Int
        get() = listOf(level, enabled, color).count { it != null }

    fun summary(): String = buildString {
        append(
            when (enabled) {
                true -> "on"
                false -> "off"
                null -> "?"
            }
        )
        append('/')
        append(color?.name?.lowercase() ?: "?")
        append('/')
        append(level?.toString() ?: "?")
    }
}
