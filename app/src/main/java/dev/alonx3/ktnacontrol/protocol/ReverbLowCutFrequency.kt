package dev.alonx3.ktnacontrol.protocol

/**
 * The 18 Low Cut corner frequencies of Reverb's internal DSP (CLAUDE.md §5.2).
 *
 * **Only one Mk2 source documents this parameter**:
 * `reference/FxFloorboard/midi.xml:42892-42906`, the `<DATA value="45" desc="REV:"
 * customdesc="Low Cut">` block — neither `reference/TuxKatana/params/reverb.yaml` nor
 * `reference/TuxKatana/doc/Adresses.txt` list it. ⚠️ **Nothing here is confirmed against the
 * amplifier yet.**
 *
 * No gaps: runs `0x00`..`0x11` solid, `FLAT` being the **first** entry here (no cut at all) —
 * unlike the two High Cut catalogues, which put `FLAT` last. That is what the source says, not
 * an inconsistency to fix: a low cut and a high cut sweep their filter in opposite directions.
 */
enum class ReverbLowCutFrequency(val value: Int, val displayName: String) {
    FLAT(0x00, "FLAT"),
    HZ_20_0(0x01, "20.0Hz"),
    HZ_25_0(0x02, "25.0Hz"),
    HZ_31_5(0x03, "31.5Hz"),
    HZ_40_0(0x04, "40.0Hz"),
    HZ_50_0(0x05, "50.0Hz"),
    HZ_63_0(0x06, "63.0Hz"),
    HZ_80_0(0x07, "80.0Hz"),
    HZ_100(0x08, "100Hz"),
    HZ_125(0x09, "125Hz"),
    HZ_160(0x0A, "160Hz"),
    HZ_200(0x0B, "200Hz"),
    HZ_250(0x0C, "250Hz"),
    HZ_315(0x0D, "315Hz"),
    HZ_400(0x0E, "400Hz"),
    HZ_500(0x0F, "500Hz"),
    HZ_630(0x10, "630Hz"),
    HZ_800(0x11, "800Hz");

    companion object {
        /** Every legal raw value, in catalogue order. */
        val VALUES: List<Int> = entries.map { it.value }

        fun fromValue(value: Int): ReverbLowCutFrequency? =
            entries.firstOrNull { it.value == value }
    }
}
