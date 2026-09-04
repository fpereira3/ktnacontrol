package dev.alonx3.ktnacontrol.protocol

/**
 * The 31 effect models shared by **MOD and FX** (CLAUDE.md §5.2).
 *
 * One table for both on purpose, and not as a shortcut: the two catalogues are **byte for
 * byte the same list**. `reference/TuxKatana/params/mod.yaml:11-42` and
 * `fx.yaml:11-42` are identical, and in `reference/FxFloorboard/midi.xml` the block for MOD
 * green (`06 27`, lines 43642-43674) and the one for FX green (`06 2A`, lines 43741-43773)
 * diff clean. The two slots hold the same kind of thing; what differs is where it sits in the
 * chain, not what can go in it.
 *
 * ⚠️ **Ten gaps**: `05`, `08`, `0B`, `0D`, `11`, `18`, `1E`, `20`, `21`, `22` do not exist,
 * and all ten are missing from **both** Mk2 sources. Same situation as [BoostType]'s `0x07`
 * and [AmpType]'s `0x19` — a selector whose values are not contiguous, which is exactly what
 * [dev.alonx3.ktnacontrol.device.KatanaEnumParameter] rejects instead of guessing at.
 *
 * Names follow `midi.xml`, the source that belongs to these addresses; where the two sources
 * differ it is only cosmetic (`Graphic E.Q.` vs `Graphic EQ`, `2x2 Chorus` vs `Chorus`).
 */
enum class ModFxType(val value: Int, val displayName: String) {
    TOUCH_WAH(0x00, "Touch Wah"),
    AUTO_WAH(0x01, "Auto Wah"),
    PEDAL_WAH(0x02, "Pedal Wah"),
    COMPRESSOR(0x03, "Compressor"),
    LIMITER(0x04, "Limiter"),
    GRAPHIC_EQ(0x06, "Graphic EQ"),
    PARAMETRIC_EQ(0x07, "Parametric EQ"),
    GUITAR_SIM(0x09, "Guitar Sim"),
    SLOW_GEAR(0x0A, "Slow Gear"),
    WAVE_SYNTH(0x0C, "Wave Synth"),
    OCTAVE(0x0E, "Octave"),
    PITCH_SHIFTER(0x0F, "Pitch Shifter"),
    HARMONIST(0x10, "Harmonist"),
    ACU_PROCESSOR(0x12, "Acu Processor"),
    PHASER(0x13, "Phaser"),
    FLANGER(0x14, "Flanger"),
    TREMOLO(0x15, "Tremolo"),
    ROTARY(0x16, "Rotary"),
    UNI_V(0x17, "Uni-V"),
    SLICER(0x19, "Slicer"),
    VIBRATO(0x1A, "Vibrato"),
    RING_MODULATE(0x1B, "Ring Modulate"),
    HUMANIZER(0x1C, "Humanizer"),
    CHORUS(0x1D, "2x2 Chorus"),
    AC_GUITAR_SIM(0x1F, "AC Guitar Sim"),
    PHASER_90E(0x23, "Phaser 90E"),
    FLANGER_117E(0x24, "Flanger 117E"),
    WAH_95E(0x25, "Wah 95E"),
    DC30(0x26, "DC30"),
    HEAVY_OCTAVE(0x27, "Heavy Octave"),
    PEDAL_BEND(0x28, "Pedal Bend");

    companion object {
        /** Every legal raw value, in catalogue order. Note the ten gaps. */
        val VALUES: List<Int> = entries.map { it.value }

        fun fromValue(value: Int): ModFxType? = entries.firstOrNull { it.value == value }
    }
}
