package dev.alonx3.ktnacontrol.protocol

/**
 * The 23 booster / overdrive / distortion models the Katana Mk2 offers (CLAUDE.md §5.2).
 *
 * **Two Mk2 sources agree byte for byte**: `reference/TuxKatana/params/booster.yaml:20-43`
 * (its `Types:` table) and `reference/FxFloorboard/midi.xml:43568-43590`, the
 * `<DATA value="24" ... desc="Booster" customdesc="GREEN">` block. The same table repeats in
 * `midi.xml` for the red and yellow slots and for the active-type address, which is itself
 * evidence that all four addresses share one catalogue.
 *
 * ⚠️ **`0x07` does not exist**, and it is missing from **both** sources — this is a real gap
 * in the amp's numbering, not a transcription slip. Same situation as `AmpType`'s `0x19`:
 * "the nearest legal value" means nothing here, which is exactly what
 * [dev.alonx3.ktnacontrol.device.KatanaEnumParameter] rejects instead of guessing.
 *
 * ⚠️ **Nothing here is confirmed against the amplifier yet**, and neither is *which address*
 * accepts a write — see [KatanaAddresses.BOOST_TYPE_ACTIVE].
 *
 * Declared in the amp's own numeric order, which is also the order both sources list.
 */
enum class BoostType(val value: Int, val displayName: String) {
    MID_BOOST(0x00, "Mid Boost"),
    CLEAN_BOOST(0x01, "Clean Boost"),
    TREBLE_BOOST(0x02, "Treble Boost"),
    CRUNCH_OD(0x03, "Crunch OD"),
    NATURAL_OD(0x04, "Natural OD"),
    WARM_OD(0x05, "Warm OD"),
    FAT_DS(0x06, "Fat DS"),

    // 0x07 no existe: el hueco está en las dos fuentes.

    METAL_DS(0x08, "Metal DS"),
    OCT_FUZZ(0x09, "Oct Fuzz"),
    BLUES_DRIVE(0x0A, "Blues Drive"),
    OVER_DRIVE(0x0B, "Over Drive"),
    TUBE_SCREAMER(0x0C, "TubeScreamer"),
    TURBO_OD(0x0D, "Turbo OD"),
    DISTORTION(0x0E, "Distortion"),
    RAT(0x0F, "Rat"),
    GUV_DS(0x10, "GuV DS"),
    DST_PLUS(0x11, "DST+"),
    METAL_ZONE(0x12, "Metal Zone"),
    SIXTIES_FUZZ(0x13, "'60s Fuzz"),
    MUFF_FUZZ(0x14, "Muff Fuzz"),
    HM_2(0x15, "HM-2"),
    METAL_CORE(0x16, "Metal Core"),
    CENTA_OD(0x17, "Centa OD");

    companion object {
        /** Every legal raw value, in catalogue order. Note the gap at `0x07`. */
        val VALUES: List<Int> = entries.map { it.value }

        fun fromValue(value: Int): BoostType? = entries.firstOrNull { it.value == value }
    }
}
