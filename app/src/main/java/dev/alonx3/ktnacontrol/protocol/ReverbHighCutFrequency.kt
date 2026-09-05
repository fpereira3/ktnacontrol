package dev.alonx3.ktnacontrol.protocol

/**
 * The 15 High Cut corner frequencies of Reverb's internal DSP (CLAUDE.md §5.2).
 *
 * **Only one Mk2 source documents this parameter**:
 * `reference/FxFloorboard/midi.xml:42912-42922`, the `<DATA value="46" desc="REV:"
 * customdesc="High Cut">` block. ⚠️ **Nothing here is confirmed against the amplifier yet.**
 *
 * ⚠️ **Looks like [DelayHighCutFrequency] but is not: `0x0A` differs.** Delay 1's own High Cut
 * list (`midi.xml:42468-42482`) names every other entry identically, but its `0x0A` is
 * `"6.30K"` while this one's is `"6.00k"` — one row where the two blocks of the same source
 * disagree with each other, not a transcription choice made here. Kept as two separate
 * catalogues on purpose rather than asserting they are the same list: see CLAUDE.md §5, "el
 * ruido de las fuentes no predice el resultado" — reusing one enum for both would have hidden
 * this the moment either turned out wrong.
 */
enum class ReverbHighCutFrequency(val value: Int, val displayName: String) {
    HZ_630(0x00, "630Hz"),
    HZ_800(0x01, "800Hz"),
    KHZ_1_00(0x02, "1.00k"),
    KHZ_1_25(0x03, "1.25k"),
    KHZ_1_60(0x04, "1.60k"),
    KHZ_2_00(0x05, "2.00k"),
    KHZ_2_50(0x06, "2.50k"),
    KHZ_3_15(0x07, "3.15k"),
    KHZ_4_00(0x08, "4.00k"),
    KHZ_5_00(0x09, "5.00k"),
    KHZ_6_00(0x0A, "6.00k"),
    KHZ_8_00(0x0B, "8.00k"),
    KHZ_10_0(0x0C, "10.0k"),
    KHZ_12_5(0x0D, "12.5k"),
    FLAT(0x0E, "FLAT");

    companion object {
        /** Every legal raw value, in catalogue order. */
        val VALUES: List<Int> = entries.map { it.value }

        fun fromValue(value: Int): ReverbHighCutFrequency? =
            entries.firstOrNull { it.value == value }
    }
}
