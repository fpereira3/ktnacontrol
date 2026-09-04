package dev.alonx3.ktnacontrol.protocol

/**
 * The 11 delay models the Katana Mk2 offers (CLAUDE.md §5.2).
 *
 * **Two Mk2 sources agree**: `reference/TuxKatana/params/delay.yaml:36-47` (its `Types:`
 * table) and `reference/FxFloorboard/midi.xml:43840-43851`, the
 * `<DATA value="2D" ... desc="Delay 1" customdesc="GREEN">` block. The only discrepancy is
 * cosmetic — `delay.yaml` names `0x08` "Tap Echo", `midi.xml` names it "Tape".
 *
 * ⚠️ **Nothing here is confirmed against the amplifier yet** — see
 * [KatanaAddresses.DELAY_TYPE_ACTIVE].
 *
 * No gaps: unlike [BoostType] and [ModFxType], this catalogue runs `0x00`..`0x0A` solid.
 */
enum class DelayType(val value: Int, val displayName: String) {
    DIGITAL(0x00, "Digital"),
    PAN(0x01, "Pan"),
    STEREO(0x02, "Stereo"),
    DUAL_SERIES(0x03, "Dual series"),
    DUAL_PARALLEL(0x04, "Dual parallel"),
    DUAL_LR(0x05, "Dual L/R"),
    REVERSE(0x06, "Reverse"),
    ANALOG(0x07, "Analog"),
    TAPE(0x08, "Tap Echo"),
    MODULATE(0x09, "Modulate"),
    SDE_3000(0x0A, "SDE-3000");

    companion object {
        /** Every legal raw value, in catalogue order. */
        val VALUES: List<Int> = entries.map { it.value }

        fun fromValue(value: Int): DelayType? = entries.firstOrNull { it.value == value }
    }
}
