package dev.alonx3.ktnacontrol.protocol

import kotlin.math.roundToInt

/**
 * Cómo lee y escribe **un** parámetro: qué valores acepta y cómo se traduce su byte crudo a lo
 * que ve el usuario.
 *
 * Es el vocabulario que comparten los bloques que se describen con una **tabla** en vez de con
 * una propiedad por parámetro — [ModFxInternalParams] (los 31 tipos de Mod/FX, CLAUDE.md §5.2)
 * y [EqParams] (los dos bloques de EQ, CLAUDE.md §5 "Controles sin perilla física")—, porque
 * son demasiados y demasiado regulares para nombrarlos uno a uno.
 *
 * Las seis formas son las mismas que ya sabe construir `KatanaRepository`: cada una se traduce
 * a un [LevelScale] o [FractionalLevelScale] concreto al crear el control de verdad.
 */
sealed interface ParamKind {
    /** Crudo y mostrado son el mismo número — la mayoría de los niveles internos. */
    data class Direct(val range: IntRange) : ParamKind

    /** Mostrado centrado en cero: `raw = display + radius`. Booster Bottom/Tone son el mismo caso. */
    data class Centered(val radius: Int) : ParamKind

    /** `0` es Off y la escala empieza en `1` — el mismo patrón que las cinco perillas de efecto del panel. */
    data class OffThenOneBased(val range: IntRange) : ParamKind

    /** Como [Direct] pero el dato ocupa 2 bytes (`byteWidth = 2`): Pre Delay de Pitch Shifter/
     * Harmonist (`0..300` ms) y Repeat Rate de DC30 (`40..600` rpm, con mínimo crudo `0x28`). */
    data class TwoByteDirect(val range: IntRange) : ParamKind

    /** Paso no entero: el Pre Delay de 2x2 Chorus (0.5 ms). Ver [FractionalLevelScale]. */
    data class Fractional(
        val rawRange: IntRange,
        val displayRange: ClosedFloatingPointRange<Double>,
        val step: Double,
    ) : ParamKind

    /** Selector de opciones con nombre — todos confirmados contiguos (CLAUDE.md §5.2). */
    data class Enum(val values: List<Int>, val labels: List<String>) : ParamKind
}

/**
 * Un parámetro con su etiqueta, su dirección **en el bloque base**, y su [ParamKind].
 *
 * "Bloque base" porque los dos usuarios de esta tabla tienen un gemelo desplazado: Mod es la
 * base de FX (`+ ModFxInternalParams.FX_OFFSET`) y EQ1 la de EQ2 (`+ EqParams.EQ2_OFFSET`).
 * La dirección de aquí es siempre la del primero de los dos.
 */
data class ParamSpec(
    val label: String,
    val address: Address,
    val kind: ParamKind,
)

/**
 * Crudo → mostrado para un [ParamKind], **sin construir un control**: la UI genérica de
 * los 31 tipos solo tiene el raw byte que le llega de [dev.alonx3.ktnacontrol.device.KatanaControl.state]
 * y necesita la misma conversión que ya aplica `KatanaRepository.modFxControl` al construir el
 * [dev.alonx3.ktnacontrol.device.KatanaParameter]/[dev.alonx3.ktnacontrol.device.KatanaFractionalParameter]
 * real — así que estas dos funciones son un espejo deliberado de [LevelScale] y
 * [FractionalLevelScale], no una escala nueva.
 */
fun ParamKind.rawToDisplay(raw: Int): Double = when (this) {
    is ParamKind.Direct -> raw.coerceIn(range).toDouble()
    is ParamKind.TwoByteDirect -> raw.coerceIn(range).toDouble()
    is ParamKind.Centered -> (raw - radius).coerceIn(-radius..radius).toDouble()
    is ParamKind.OffThenOneBased ->
        if (raw == 0) range.first.toDouble() else (raw - 1).coerceIn(range).toDouble()
    is ParamKind.Fractional -> FractionalLevelScale(rawRange, displayRange, step).toDisplay(raw)
    is ParamKind.Enum -> raw.toDouble()
}

/** El crudo a mandar para lo que la UI muestra — la inversa de [rawToDisplay]. */
fun ParamKind.displayToRaw(display: Double): Int = when (this) {
    is ParamKind.Direct -> display.roundToInt().coerceIn(range)
    is ParamKind.TwoByteDirect -> display.roundToInt().coerceIn(range)
    is ParamKind.Centered -> display.roundToInt().coerceIn(-radius..radius) + radius
    is ParamKind.OffThenOneBased -> display.roundToInt().coerceIn(range) + 1
    is ParamKind.Fractional -> FractionalLevelScale(rawRange, displayRange, step).toRaw(display)
    is ParamKind.Enum -> display.roundToInt()
}

/** El rango que la UI debe mostrar para un [ParamKind], en `Double` para un [ClosedFloatingPointRange]. */
/**
 * Cuánto vale **un paso** de este parámetro en lo que se muestra.
 *
 * Lo necesita un control que avanza de uno en uno en vez de barrer un rango continuo — la
 * perilla del EQ (`KnobControl`). Sin esto, un gesto tendría que traducir píxeles a "una
 * fracción del rango", y con un rango de 40 dB y otro de 24 posiciones el mismo gesto se
 * sentiría distinto en cada parámetro.
 *
 * Casi todos valen 1: un byte crudo es una unidad mostrada. Las excepciones son las escalas
 * fraccionarias, que traen el suyo, y los selectores, donde un paso es **una posición de la
 * lista** y quien llama trabaja con el índice, no con el valor crudo.
 */
val ParamKind.displayStep: Double
    get() = when (this) {
        is ParamKind.Fractional -> step
        is ParamKind.Enum -> 1.0
        else -> 1.0
    }

val ParamKind.displayBounds: ClosedRange<Double>
    get() = when (this) {
        is ParamKind.Direct -> range.first.toDouble()..range.last.toDouble()
        is ParamKind.TwoByteDirect -> range.first.toDouble()..range.last.toDouble()
        is ParamKind.Centered -> (-radius).toDouble()..radius.toDouble()
        is ParamKind.OffThenOneBased -> range.first.toDouble()..range.last.toDouble()
        is ParamKind.Fractional -> displayRange
        is ParamKind.Enum -> 0.0..0.0
    }

