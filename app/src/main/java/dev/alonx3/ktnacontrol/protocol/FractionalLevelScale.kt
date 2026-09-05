package dev.alonx3.ktnacontrol.protocol

import kotlin.math.roundToInt

/**
 * Like [LevelScale] but for a raw byte whose displayed value uses a **fractional step** — 0.5,
 * 0.1, anything that is not "one raw unit = one displayed unit" — which an integer
 * [LevelScale.rawOffset] cannot express. Two parameters documented in CLAUDE.md §5.2 needed
 * exactly this and stayed unwired until now: Mod's 2x2 Chorus Pre Delay (`range 00/50/0.0/40.0`,
 * 0.5 ms/step) and Reverb Time (`range 00/63/0.1/10.0 sec`, 0.1 s/step, with `mostrado =
 * (crudo + 1) / 10`).
 *
 * **Kept as a separate type instead of widening [LevelScale] to `Double`.** Every other
 * parameter in this project shows a plain `Int` and the ~230 existing tests assume that; this
 * is the minority case, not the rule, so it gets its own small type rather than forcing a
 * floating-point contract onto controls that never needed one.
 *
 * The offset that [LevelScale.rawOffset] would otherwise carry falls out of the same formula
 * without a separate field: `display = displayRange.start + (raw - rawRange.first) * step`.
 * For Reverb Time, `rawRange = 0..99` and `displayRange = 0.1..10.0` already encode the "+1"
 * — `raw=0` lands on `0.1`, not `0.0`, because `displayRange.start` is `0.1`, not `0.0`.
 */
data class FractionalLevelScale(
    /** The bytes the amp actually takes. */
    val rawRange: IntRange,
    /** What the UI shows and accepts. */
    val displayRange: ClosedFloatingPointRange<Double>,
    /** How much [displayRange] moves per raw unit. */
    val step: Double,
) {
    init {
        require(step > 0.0) { "step debe ser positivo, era $step" }
    }

    /** The number to show for a byte the amp reported. */
    fun toDisplay(raw: Int): Double =
        displayRange.start + (raw.coerceIn(rawRange) - rawRange.first) * step

    /**
     * The byte to send for a value the user picked, rounded to the nearest raw step.
     *
     * Rounds once, from the clamped display value straight to an integer raw byte — never by
     * accumulating a running total — so repeated round trips (raw → display → raw → …) cannot
     * drift: each conversion starts fresh from an integer, and floating-point noise from
     * multiplying by [step] stays many orders of magnitude below the `0.5` needed to flip a
     * rounding boundary.
     */
    fun toRaw(display: Double): Int {
        val clamped = display.coerceIn(displayRange)
        val steps = ((clamped - displayRange.start) / step).roundToInt()
        return (rawRange.first + steps).coerceIn(rawRange)
    }

    /** Whether the amp could legitimately hold this raw byte. */
    fun accepts(raw: Int): Boolean = raw in rawRange
}
