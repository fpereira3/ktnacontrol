package dev.alonx3.ktnacontrol.protocol

/**
 * How a continuous level's **raw byte** relates to the number a person sees.
 *
 * The two are not always the same on the Katana Mk2, and the difference is a single step:
 *
 * | Perillas | Crudo | Mostrado |
 * | --- | --- | --- |
 * | Gain, Volume, Bass, Middle, Treble, Presence (`06 51`–`06 56`) | `0x00`..`0x64` | 0..100 |
 * | Boost, Mod, FX, Delay, Reverb (`06 57`–`06 5B`) | `0x00` = Off, luego `0x01`..`0x65` | 0..100 |
 *
 * Source: [reference/FxFloorboard/midi.xml:44062-44112](reference/FxFloorboard/midi.xml), the
 * `<DATA value="5x" abbr="knob">` blocks — `range 00/64/00/100` for the first six and
 * `00 name="Off"` plus `range 01/65/00/100` for the five effects. **Corroborado
 * independientemente** (2026-09-03) por Boss Tone Studio, cuya UI de PC muestra esos cinco
 * como "Off" y luego 0..100.
 *
 * ✅ **Verificado contra el amplificador (2026-09-03).** Con el mapeo directo, el máximo que
 * la app mandaba (crudo 100) era en realidad el 99 del amplificador; al corregirlo, el hueco
 * que quedaba entre el slider al 100 y el tope físico **se hizo más pequeño**, que es
 * exactamente lo que predice un desfase de un paso.
 *
 * El hueco no desapareció del todo, y eso también cuadra: es **holgura mecánica**, no otro
 * desfase. La prueba está en Presence — `60 00 06 56`, escala directa, sin desplazamiento
 * nunca — que mostraba el mismo hueco desde el principio. O sea que había dos cosas sumadas,
 * una de software y una del plástico, y solo la primera era arreglable.
 *
 * ✅ **Confirmado por lo que reporta el propio amplificador** (2026-09-03, lectura inicial al
 * conectar):
 *  - **El tope existe y es `0x65`.** Reverb reportó `crudo 101` → mostrado 100. O sea que el
 *    `1..101` no es solo lo que dice `midi.xml`: es lo que el amp emite. Esta es justamente
 *    la prueba que se había apuntado como pendiente —"girar la perilla al tope y leer lo que
 *    reporta"— resuelta sin tener que girar nada.
 *  - **El `0` es Off de verdad.** En la misma lectura, los tres efectos cuyo nivel reportó
 *    crudo `0` (Boost, Mod, FX) reportaron también su on/off en `0`, y los dos con nivel
 *    distinto de cero (Delay 78, Reverb 101) lo reportaron en `1`. Cinco pares de direcciones
 *    independientes, correlación perfecta. Es una sola muestra, así que corrobora fuerte pero
 *    no demuestra: un nivel al mínimo con el efecto encendido reportaría crudo `1`, no `0`, y
 *    eso todavía no se ha visto.
 *
 * ⚠️ **Una ambigüedad que queda, a propósito**: en una escala con Off, el crudo `0` y el
 * crudo `1` se muestran los dos como `0`. No se puede hacer mejor con un slider de 0..100, y
 * no hace falta: lo que distingue "apagado" de "al mínimo" es el switch on/off del efecto,
 * que tiene su propia dirección y está confirmado.
 */
data class LevelScale(
    /** What the UI shows and accepts. */
    val displayRange: IntRange,
    /** `raw = display + rawOffset`. */
    val rawOffset: Int,
    /** The raw value that means "off", outside [rawRange], or null when there is none. */
    val offRawValue: Int?,
) {
    /** The bytes the amp actually takes, excluding [offRawValue]. */
    val rawRange: IntRange =
        (displayRange.first + rawOffset)..(displayRange.last + rawOffset)

    /** The byte to send for a value the user picked. */
    fun toRaw(display: Int): Int = display.coerceIn(displayRange) + rawOffset

    /** The number to show for a byte the amp reported. Off shows as the bottom of the range. */
    fun toDisplay(raw: Int): Int =
        if (raw == offRawValue) displayRange.first
        else (raw - rawOffset).coerceIn(displayRange)

    /** Whether the amp could legitimately hold this raw byte. */
    fun accepts(raw: Int): Boolean = raw == offRawValue || raw in rawRange

    companion object {
        /** Raw and shown are the same number: the six amp/EQ knobs. */
        fun direct(displayRange: IntRange): LevelScale =
            LevelScale(displayRange, rawOffset = 0, offRawValue = null)

        /** `0` is Off and the scale starts at `1`: the five effect knobs. */
        fun offThenOneBased(displayRange: IntRange): LevelScale =
            LevelScale(displayRange, rawOffset = 1, offRawValue = 0)

        /**
         * Shown centered on zero, raw centered on the middle of its range: `raw = display +
         * radius`. This is already what [rawOffset] does — a centered scale needed no new
         * field, only this factory — for parameters `midi.xml` documents as
         * `range aa/bb/-radius/+radius`, such as Booster's Bottom and Tone
         * (`00/64/-50/+50`, CLAUDE.md §5.2).
         */
        fun centered(radius: Int): LevelScale =
            LevelScale((-radius)..radius, rawOffset = radius, offRawValue = null)
    }
}
