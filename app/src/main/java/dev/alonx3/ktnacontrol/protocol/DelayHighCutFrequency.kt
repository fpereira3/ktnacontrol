package dev.alonx3.ktnacontrol.protocol

/**
 * The 15 High Cut corner frequencies of Delay 1's internal DSP (CLAUDE.md §5.2).
 *
 * **Only one Mk2 source documents this parameter**:
 * `reference/FxFloorboard/midi.xml:42468-42482`, the `<DATA value="05" desc="DD1:"
 * customdesc="High Cut">` block — neither `reference/TuxKatana/params/delay.yaml` nor
 * `reference/TuxKatana/doc/Adresses.txt` list it. ⚠️ **Nothing here is confirmed against the
 * amplifier yet.**
 *
 * No gaps: runs `0x00`..`0x0E` solid, `FLAT` being the last (no cut at all), same shape as
 * [ReverbHighCutFrequency] — which looks identical but is **not** the same catalogue: see its
 * KDoc for the one entry where the two sources disagree.
 */
enum class DelayHighCutFrequency(val value: Int, val displayName: String) {
    HZ_630(0x00, "630Hz"),
    HZ_800(0x01, "800Hz"),
    KHZ_1_00(0x02, "1.00K"),
    KHZ_1_25(0x03, "1.25K"),
    KHZ_1_60(0x04, "1.60K"),
    KHZ_2_00(0x05, "2.00K"),
    KHZ_2_50(0x06, "2.50K"),
    KHZ_3_15(0x07, "3.15K"),
    KHZ_4_00(0x08, "4.00K"),
    KHZ_5_00(0x09, "5.00K"),
    KHZ_6_30(0x0A, "6.30K"),
    KHZ_8_00(0x0B, "8.00K"),
    KHZ_10_0(0x0C, "10.0K"),
    KHZ_12_5(0x0D, "12.5k"),
    FLAT(0x0E, "FLAT");

    companion object {
        /** Every legal raw value, in catalogue order. */
        val VALUES: List<Int> = entries.map { it.value }

        fun fromValue(value: Int): DelayHighCutFrequency? =
            entries.firstOrNull { it.value == value }
    }
}
