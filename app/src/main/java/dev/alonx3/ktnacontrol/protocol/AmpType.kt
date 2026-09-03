package dev.alonx3.ktnacontrol.protocol

/**
 * The amp models the Katana Mk2 exposes at [KatanaAddresses.AMP_TYPE_FULL] (`60 00 00 21`).
 *
 * **Source: `reference/FxFloorboard/midi.xml:37311-37341`**, the `<DATA value="21" ...
 * desc="PREAMP:" customdesc="Type">` block — the table that belongs to that exact address.
 * It is the most complete of the three sources and the only one that is unambiguously about
 * the Mk2: `reference/TuxKatana/params/amplifier.yaml:16-45` and
 * `reference/TuxKatana/doc/Adresses.txt:3-33` list the same values but **miss `BG Lead`
 * (`0x10`)**, so this table has 30 entries where those have 29.
 *
 * Declared in the order `midi.xml` lists them: the five base categories, then their
 * variations, then the individual models (what the community calls the "sneaky amps").
 *
 * ⚠️ **Nothing here is confirmed against the amplifier yet.** The values come from
 * documentation only; writing them has never been tried (CLAUDE.md §5).
 *
 * Not included: `0x19` "Custom", which appears only in a *different* block of the same file
 * (`midi.xml:51292`, "Preamp convert mk2") and **not** in the table for address `00 21`. If
 * a Custom amp turns out to be selectable, that is where to look first.
 */
enum class AmpType(val value: Int, val displayName: String) {
    ACOUSTIC(0x01, "Acoustic"),
    CLEAN(0x08, "Clean"),
    CRUNCH(0x0B, "Crunch"),
    LEAD(0x18, "Lead"),
    BROWN(0x17, "Brown"),
    ACOUSTIC_VARIATION(0x1C, "Var Acoustic"),
    CLEAN_VARIATION(0x1D, "Var Clean"),
    CRUNCH_VARIATION(0x1E, "Var Crunch"),
    LEAD_VARIATION(0x1F, "Var Lead"),
    BROWN_VARIATION(0x20, "Var Brown"),
    NATURAL_CLEAN(0x00, "Natural Clean"),
    CLEAN_TWIN(0x09, "Clean Twin"),
    COMBO_CRUNCH(0x02, "Combo Crunch"),
    PRO_CRUNCH(0x0A, "Pro Crunch"),
    DELUXE_CRUNCH(0x0C, "Deluxe Crunch"),
    STACK_CRUNCH(0x03, "Stack Crunch"),
    VO_DRIVE(0x0D, "VO Drive"),
    BG_DRIVE(0x11, "BG Drive"),
    MATCH_DRIVE(0x0F, "Match Drive"),
    POWER_DRIVE(0x05, "Power Drive"),
    VO_LEAD(0x0E, "VO Lead"),

    /**
     * ⚠️ Only `midi.xml` lists this one. Both TuxKatana sources skip `0x10` entirely, so if
     * one amp model in the list turns out not to exist on the Mk2, this is the first suspect.
     */
    BG_LEAD(0x10, "BG Lead"),
    EXTREME_LEAD(0x06, "Extreme Lead"),
    T_AMP_LEAD(0x16, "T-Amp Lead"),
    MS_1959_I(0x12, "MS-1959 I"),
    MS_1959_I_II(0x13, "MS-1959 I+II"),
    HIGAIN_STACK(0x04, "HiGain Stack"),
    R_FIER_VINTAGE(0x14, "R-Fier Vintage"),
    R_FIER_MODERN(0x15, "R-Fier Modern"),
    CORE_METAL(0x07, "Core Metal");

    companion object {
        /** The raw byte values, for [KatanaAddresses] and for building a control. */
        val VALUES: List<Int> = entries.map { it.value }

        /** The type with this raw value, or null if the amp reports something unlisted. */
        fun fromValue(value: Int): AmpType? = entries.firstOrNull { it.value == value }
    }
}

/**
 * The five positions of the physical AMP TYPE knob, at
 * [KatanaAddresses.AMP_TYPE_PANEL] (`60 00 06 50`).
 *
 * **This is a different value space from [AmpType]**, and confusing the two is the easiest
 * mistake to make here: the panel knob is `00`..`04` (five categories), while `60 00 00 21`
 * takes the full model ids (`0x00`..`0x20`, non-contiguous). `amplifier.yaml:3` names the
 * panel one `am_num` — a *number*, not a type.
 *
 * Source: `reference/FxFloorboard/midi.xml:44062-44068`, the `<DATA value="50" name="Panel:
 * 944" abbr="knob" desc="Amp" customdesc="Type">` block, corroborated by
 * `reference/TuxKatana/doc/Adresses.txt:35` (`60 00 06 50 -> [00|01|02|03|04]`) and by
 * `reference/katana-midi-bridge/parameters/amplifier.json:44-48`.
 *
 * ⚠️ Sin confirmar contra el amplificador.
 */
enum class AmpCategory(val value: Int, val displayName: String) {
    ACOUSTIC(0x00, "Acoustic"),
    CLEAN(0x01, "Clean"),
    CRUNCH(0x02, "Crunch"),
    LEAD(0x03, "Lead"),
    BROWN(0x04, "Brown");

    companion object {
        val VALUES: List<Int> = entries.map { it.value }
        fun fromValue(value: Int): AmpCategory? = entries.firstOrNull { it.value == value }
    }
}

/**
 * The green / red / yellow bank each effect keeps, selected by the button under its knob.
 *
 * `midi.xml` calls these addresses "GRY color select" — GRY spelling out the three colours —
 * which is as explicit as the reference material ever gets. Three Mk2 sources agree on both
 * the addresses and on `00|01|02`:
 *  - `reference/FxFloorboard/midi.xml:3959-3963` (`Booster/MOD/FX/Delay1/Rev-Delay2 GRY
 *    color select`),
 *  - `reference/TuxKatana/doc/Adresses.txt:70, 83, 98, 112, 123` (`-> [00|01|02]`, and line
 *    83 annotates it `# [green|red|yellow]`),
 *  - `reference/TuxKatana/params/*.yaml` (`bo_bank_sel`, `mo_bank_sel`, `fx_bank_sel`,
 *    `de_bank_sel`, `re_bank_sel`).
 *
 * `reference/katana-midi-bridge/parameters/color_assign.json:2-6` defines the same
 * `colorEnum` `[0, 1, 2] = green, red, yellow`, but for the **MK1** address family — see
 * [KatanaAddresses.EFFECT_COLOR_MK1].
 *
 * ⚠️ Sin confirmar contra el amplificador.
 */
enum class EffectColor(val value: Int) {
    GREEN(0x00),
    RED(0x01),
    YELLOW(0x02);

    companion object {
        val VALUES: List<Int> = entries.map { it.value }
        fun fromValue(value: Int): EffectColor? = entries.firstOrNull { it.value == value }
    }
}
