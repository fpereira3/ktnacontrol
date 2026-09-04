package dev.alonx3.ktnacontrol.protocol

/**
 * The 7 reverb models the Katana Mk2 offers (CLAUDE.md §5.2).
 *
 * **Two Mk2 sources agree**: `reference/TuxKatana/params/reverb.yaml:24-31` (its `Types:`
 * table) and `reference/FxFloorboard/midi.xml:43879-43886`, the
 * `<DATA value="30" ... desc="Reverb" customdesc="GREEN">` block.
 *
 * ⚠️ **Nothing here is confirmed against the amplifier yet** — see
 * [KatanaAddresses.REVERB_TYPE_ACTIVE].
 *
 * No gaps: this catalogue runs `0x00`..`0x06` solid.
 */
enum class ReverbType(val value: Int, val displayName: String) {
    AMBIENCE(0x00, "Ambience"),
    ROOM(0x01, "Room"),
    HALL_1(0x02, "Hall 1"),
    HALL_2(0x03, "Hall 2"),
    PLATE(0x04, "Plate"),
    SPRING(0x05, "Spring"),
    MODULATE(0x06, "Modulate");

    companion object {
        /** Every legal raw value, in catalogue order. */
        val VALUES: List<Int> = entries.map { it.value }

        fun fromValue(value: Int): ReverbType? = entries.firstOrNull { it.value == value }
    }
}
