package dev.alonx3.ktnacontrol.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.alonx3.ktnacontrol.R
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.material3.Surface
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.rememberUpdatedState

/**
 * Los controles sueltos que comparten la pantalla de diagnóstico y la de Biblioteca.
 *
 * Están aquí, y son `internal` en vez de `private`, porque la Biblioteca enseña **los mismos
 * controles en modo deshabilitado** con los valores de un fichero en vez de los del
 * amplificador. Copiarlos habría sido garantizar que las dos copias se separaran a la primera
 * corrección.
 *
 * Cada uno acepta `enabled`, que es lo que hace posible ese modo de solo lectura sin que el
 * widget sepa nada de ficheros ni de amplificadores.
 */

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
    )
}

/**
 * The two halves of the diagnostics screen, reachable from the drawer.
 *
 * They are split because they compete for vertical space: with six sliders and eight buttons
 * on one screen the console was squeezed down to nothing.
 */
enum class DebugSection(
    @param:androidx.annotation.StringRes val titleRes: Int,
    /**
     * Si es uno de los destinos de la **barra inferior**.
     *
     * ⚠️ Separa las tres pantallas de dominio de [LOGS], que desde el 2026-09-09 es una **entrada
     * secundaria**: sigue estando entera y llegando por la barra superior, pero deja de competir
     * de igual a igual con las pantallas que se usan para tocar. Es la lista que recorre la barra
     * inferior, así que **añadir aquí una pantalla la pone en la barra** — y olvidarse la deja
     * inalcanzable, que es justo lo que fija un test.
     */
    val primary: Boolean = true,
) {
    /** ⚠️ Diagnóstico: entrada secundaria, no va en la barra inferior. */
    LOGS(R.string.debug_connection_section_logs, primary = false),

    /**
     * La pantalla de dominio del amplificador (BACKLOG.md, bloque 6): canal, modelo, niveles del
     * panel, EQ, Noise Gate, Contour, Solo y cadena. **Ni un control de efecto** — ver
     * [AmpDomain] para el reparto y CLAUDE.md §4.2 para por qué se extrajo así.
     *
     * Convivió con la antigua `SLIDERS` mientras el resto de pantallas de dominio no existían;
     * desde el 2026-09-09 esa entrada ya no está y `AmpSection` es el único sitio donde vive
     * este bloque de controles.
     */
    AMP(R.string.debug_connection_section_amp),

    /**
     * La pantalla de dominio de los efectos (BACKLOG.md, bloque 6): las cinco tarjetas
     * (Booster/Mod/FX/Delay/Reverb) y nada del amplificador. Mismo patrón que [AMP] — ver
     * [AmpDomain] y CLAUDE.md §4.2.
     */
    EFFECTS(R.string.debug_connection_section_effects),
    /**
     * La pantalla de presets (BACKLOG.md, bloque 6): guardar el estado en un canal, exportar a
     * `.tsl` y la Biblioteca entera.
     *
     * **Sustituye a las antiguas `SLIDERS` y `LIBRARY`**: la primera se quedó sin contenido
     * propio cuando [AMP] y [EFFECTS] absorbieron lo suyo, y la segunda es la mitad de abajo de
     * esta. Dentro, lo que actúa sobre el amplificador en vivo y lo que son ficheros están
     * separados a propósito — ver CLAUDE.md §4.2.
     */
    PRESETS(R.string.debug_connection_section_presets),
    ;

    companion object {
        /** Los destinos de la barra inferior, en su orden. */
        val PRIMARY: List<DebugSection> get() = entries.filter { it.primary }

        /** Los que se llegan por otro sitio. Hoy solo [LOGS]. */
        val SECONDARY: List<DebugSection> get() = entries.filterNot { it.primary }
    }
}

/**
 * Diagnostics screen: USB actions plus a live console on one side, the amp's continuous
 * controls on the other.
 */

/**
 * A short list of options as chips.
 *
 * Nothing is selected until the amp says so: with an unconfirmed address the honest state is
 * "unknown", and pre-selecting the first option would look like a value the amp confirmed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipSelector(
    label: String,
    options: List<Pair<Int, String>>,
    selected: Int?,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, name) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    label = { Text(name) },
                    enabled = enabled,
                )
            }
        }
    }
}

/** A long list of options behind a button, for the 30 amp models. */
@Composable
internal fun DropdownSelector(
    label: String,
    options: List<Pair<Int, String>>,
    selected: Int?,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentName = options.firstOrNull { it.first == selected }?.second
        ?: stringResource(R.string.debug_connection_unknown_value)

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Box {
            TextButton(onClick = { expanded = true }, enabled = enabled) {
                Text(currentName)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            expanded = false
                            onSelected(value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/**
 * One 0..100 level.
 *
 * Deliberately a bare [Slider] — the point is to validate the read / write / cache path
 * against the amp, not the visual design. The GET button sits next to the name so that adding
 * a parameter is one more block and never a row of buttons growing until it overflows.
 *
 * Disabled until the level is known: a slider that reads 0 because nothing was read yet would
 * send a 0 on first touch and silently change the amp.
 */
/**
 * Si los botones de "leer" (el GET individual) se enseñan.
 *
 * Existe por un motivo concreto: los mismos controles sirven para el amplificador en vivo y
 * para editar un preset de fichero (CLAUDE.md §4.5), y **offline un botón de "leer del
 * amplificador" no significa nada** — leería de la misma imagen en memoria que ya se está
 * enseñando. Sería un botón que no hace nada, que es peor que no tenerlo.
 *
 * Se resuelve con un `CompositionLocal` y no con un parámetro porque los sitios donde se pasa
 * un `onRead` son medio centenar: un parámetro obligaría a tocarlos todos para expresar una
 * decisión que en realidad es de la pantalla entera, no de cada slider.
 */
internal val LocalReadButtonsVisible = androidx.compose.runtime.compositionLocalOf { true }

@Composable
internal fun LevelControl(
    label: String,
    level: Int?,
    enabled: Boolean,
    onLevelChanged: (Int) -> Unit,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Every panel/EQ/effect level is `0..100`, so that stays the default. Booster's internal
     * parameters are not — Drive goes to 120, Bottom/Tone are centered on zero — so this is a
     * parameter rather than a hardcoded `0f..100f`, without changing any existing caller.
     */
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (LocalReadButtonsVisible.current) TextButton(onClick = onRead, enabled = enabled) {
                Text(stringResource(R.string.debug_connection_read_level))
            }
        }
        Slider(
            value = (level ?: 0).toFloat(),
            onValueChange = { value -> onLevelChanged(value.roundToInt()) },
            valueRange = valueRange,
            enabled = enabled && level != null,
        )
    }
}
/**
 * Same as [LevelControl] but for a [KatanaFractionalParameter][dev.alonx3.ktnacontrol.device.KatanaFractionalParameter]:
 * the value shown and dragged is a `Double`, not an `Int` — CLAUDE.md §5.2, Reverb Time and
 * Mod's 2x2 Chorus Pre Delay, the two parameters whose step needed
 * [FractionalLevelScale][dev.alonx3.ktnacontrol.protocol.FractionalLevelScale].
 *
 * The slider itself still drags continuously in `Float`; quantising to the amp's actual 0.5/0.1
 * step happens once, in `scale.toRaw`, same as every other control — the UI never rounds on its
 * own.
 */
@Composable
internal fun FractionalLevelControl(
    label: String,
    level: Double?,
    enabled: Boolean,
    onLevelChanged: (Double) -> Unit,
    onRead: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (LocalReadButtonsVisible.current) TextButton(onClick = onRead, enabled = enabled) {
                Text(stringResource(R.string.debug_connection_read_level))
            }
        }
        Slider(
            value = (level ?: 0.0).toFloat(),
            onValueChange = { value -> onLevelChanged(value.toDouble()) },
            valueRange = valueRange,
            enabled = enabled && level != null,
        )
    }
}

/**
 * Una barra vertical por banda de frecuencia, al estilo del EQ gráfico de Boss Tone Studio.
 *
 * Sustituye a la lista de sliders horizontales sueltos que había antes: un ecualizador gráfico
 * **se lee de un vistazo por la forma de la curva**, y esa forma solo existe si las bandas están
 * una al lado de otra en vertical. Con diez sliders horizontales apilados no hay curva que ver,
 * solo diez números.
 *
 * ⚠️ **Interfaz mínima y deliberadamente sin pulir**: el objetivo es que sea usable para probar
 * contra el amplificador, no que se parezca a BTS. El rediseño visual va aparte.
 *
 * El valor es absoluto —se toca o se arrastra donde se quiere— porque en una barra corta eso es
 * más rápido que acumular desplazamiento, y porque el punto de partida (`null`, sin leer) no
 * tiene desde dónde acumular.
 */
@Composable
internal fun VerticalBarControl(
    label: String,
    value: Double?,
    range: ClosedRange<Double>,
    enabled: Boolean,
    onValueChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
    barHeight: Dp = 96.dp,
) {
    val span = (range.endInclusive - range.start).takeIf { it > 0.0 } ?: 1.0
    var heightPx by remember { mutableFloatStateOf(0f) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill =
        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    fun report(y: Float) {
        if (!enabled || heightPx <= 0f) return
        // Arriba es el máximo: se invierte la Y de la pantalla.
        val ratio = (1f - (y / heightPx)).coerceIn(0f, 1f)
        onValueChanged(range.start + ratio * span)
    }

    Column(
        modifier = modifier.width(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value?.let { "%.1f".format(it) } ?: "—",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .width(20.dp)
                .height(barHeight)
                .background(track)
                .onSizeChanged { heightPx = it.height.toFloat() }
                .pointerInput(enabled) {
                    detectTapGestures { offset -> report(offset.y) }
                }
                .pointerInput(enabled) {
                    detectVerticalDragGestures { change, _ -> report(change.position.y) }
                },
        ) {
            val ratio = value?.let { ((it - range.start) / span).toFloat().coerceIn(0f, 1f) }
            if (ratio != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(ratio)
                        .align(Alignment.BottomCenter)
                        .background(fill),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

/**
 * Quién está ajustando una perilla ahora mismo, para que los contenedores con scroll se
 * queden quietos mientras dure el gesto.
 *
 * ⚠️ **Hace falta aunque el gesto ya reclame el puntero.** `detectDragGesturesAfterLongPress`
 * impide que el scroll *robe* el arrastre, pero el dedo sigue apoyado sobre un contenedor
 * desplazable y basta un movimiento vertical —que en este gesto no significa nada— para que la
 * pantalla se vaya sola por debajo mientras se ajusta. Congelar los dos ejes es lo que hace que
 * el arrastre mueva **solo** el valor.
 *
 * Es un objeto compartido y no un `Boolean` por perilla: quien tiene que enterarse es el
 * **contenedor**, que no sabe cuántas perillas hay dentro.
 *
 * ⚠️ **Nadie lo provee, así que la instancia por defecto se comparte en toda la app.** Es
 * correcto aquí porque las pantallas con perillas son excluyentes —solo hay una a la vez— y
 * simplifica: no hay que acordarse de envolver cada pantalla nueva. El riesgo que eso abre es
 * que una bandera se quede encendida y **el scroll no vuelva nunca**, y contra eso está el
 * `DisposableEffect` de [KnobControl], que la suelta si la perilla desaparece a media
 * pulsación.
 */
@Stable
internal class KnobInteraction {

    /** Hay una perilla en ajuste. Mientras sea true, los scrolls de la pantalla no responden. */
    var adjusting: Boolean by mutableStateOf(false)
        private set

    internal fun begin() {
        adjusting = true
    }

    internal fun end() {
        adjusting = false
    }
}

/**
 * El [KnobInteraction] de la pantalla actual.
 *
 * `staticCompositionLocalOf` y no `compositionLocalOf` porque el valor **provisto** nunca
 * cambia —lo que cambia es el campo de dentro, que sí es estado observable—, así que no hace
 * falta que Compose siga la pista de quién lo lee.
 */
internal val LocalKnobInteraction = staticCompositionLocalOf { KnobInteraction() }

/**
 * `verticalScroll` que se congela mientras se ajusta una perilla.
 *
 * Existe como helper y no como llamada suelta en cada pantalla para que sea difícil olvidarlo:
 * un contenedor que se desplace durante el gesto arruina el ajuste, y el síntoma —"el valor
 * salta raro"— no señala al scroll.
 */
@Composable
internal fun Modifier.knobAwareVerticalScroll(
    state: ScrollState = rememberScrollState(),
): Modifier = verticalScroll(state, enabled = !LocalKnobInteraction.current.adjusting)

/** El gemelo horizontal de [knobAwareVerticalScroll]. */
@Composable
internal fun Modifier.knobAwareHorizontalScroll(
    state: ScrollState = rememberScrollState(),
): Modifier = horizontalScroll(state, enabled = !LocalKnobInteraction.current.adjusting)

/**
 * Una perilla con gesto **mantener + arrastrar en horizontal**. El componente de perilla del
 * proyecto: lo que se decida aquí vale para todas.
 *
 * ⚠️ **El gesto anterior (arrastre vertical directo) era inutilizable, y por una razón
 * estructural, no de sensibilidad: estas perillas viven dentro de contenedores con scroll.**
 * Cualquier arrastre que empiece sin más se lo lleva el contenedor. Exigir una **pulsación
 * mantenida** es lo que hace que el gesto gane —`detectDragGesturesAfterLongPress` reclama el
 * puntero— y de paso da el momento natural para agrandar la perilla.
 *
 * Cómo se comporta:
 *  - **Mantener** el dedo: la perilla se agranda y se proyecta **muy por encima del dedo**, con
 *    margen para que ni el dedo ni la mano la tapen. El valor se enseña **arriba del todo**,
 *    separado del conjunto, que es donde se lee sin que la mano estorbe.
 *  - **Arrastrar**: a la derecha sube, a la izquierda baja. Es **relativo** (delta acumulado),
 *    no absoluto, así que se puede seguir ajustando aunque el dedo se salga del control.
 *  - **Mientras dura**: los scrolls de la pantalla se congelan en los dos ejes.
 *  - **Soltar**: vuelve a su tamaño y su sitio, y el scroll se restaura de inmediato.
 *
 * ✅ **Avanza de paso en paso y no se salta ninguno.** El valor se recalcula siempre desde el
 * que había al empezar el gesto más un número **entero** de pasos — ver [knobValueAt].
 *
 * [valueText] va aparte del número a propósito: un parámetro de frecuencia es un **selector**
 * cuyo índice mueve la perilla pero cuyo texto es `1.60k`. Así la misma perilla sirve para una
 * ganancia continua y para un enum sin que la UI sepa cuál es cuál.
 *
 * ⚠️ Interfaz mínima, sin pulir — igual que [VerticalBarControl].
 */
@Composable
internal fun KnobControl(
    label: String,
    value: Double?,
    valueText: String,
    range: ClosedRange<Double>,
    /** Cuánto vale un paso; ver `ParamKind.displayStep`. Para un selector, `1.0` (un índice). */
    step: Double,
    enabled: Boolean,
    onValueChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    // El valor al empezar el gesto. Recalcular desde aquí —y no desde el valor actual— es lo
    // que impide que el redondeo a pasos enteros se vaya acumulando durante el arrastre.
    var anchor by remember { mutableDoubleStateOf(0.0) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val interaction = LocalKnobInteraction.current
    val liftPx = with(LocalDensity.current) { -KNOB_LIFT.roundToPx() }

    // ⚠️ **`rememberUpdatedState` y no una key de `pointerInput`.** El propio gesto llama a
    // `onValueChanged`, que hace que `value` cambie en cada evento de arrastre. Si `value`
    // fuera key de `pointerInput`, Compose reiniciaría la corrutina del detector **a mitad de
    // gesto** cada vez que el valor se moviera — lo que cancela el arrastre en curso
    // (`onDragCancel`) casi de inmediato después del primer movimiento. Es justo el síntoma que
    // esto corrige: "mueve un poco y la perilla grande desaparece". `rememberUpdatedState` deja
    // leer el valor más reciente **sin** que su cambio reinicie nada.
    val latestValue by rememberUpdatedState(value)
    val latestRange by rememberUpdatedState(range)
    val latestStep by rememberUpdatedState(step)

    // Si la perilla desaparece de la composición a media pulsación (un cambio de tipo de
    // efecto, por ejemplo), el scroll se quedaría congelado para siempre. Esto lo suelta.
    DisposableEffect(Unit) {
        onDispose { if (dragging) interaction.end() }
    }

    Box(modifier = modifier.width(KNOB_SLOT_WIDTH)) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KnobDial(
                value = value,
                range = range,
                enabled = enabled,
                size = KNOB_SIZE,
                // Solo `enabled` como key: es lo único que de verdad debe reiniciar el detector
                // (para atar o soltar el gesto). `value`/`range`/`step` se leen a través de los
                // `rememberUpdatedState` de arriba, siempre con su valor más reciente.
                modifier = Modifier.pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            anchor = latestValue ?: latestRange.start
                            dragX = 0f
                            dragging = true
                            interaction.begin()
                        },
                        onDragEnd = {
                            dragging = false
                            interaction.end()
                        },
                        onDragCancel = {
                            dragging = false
                            interaction.end()
                        },
                        onDrag = { _, dragAmount ->
                            dragX += dragAmount.x
                            onValueChanged(knobValueAt(anchor, dragX, latestStep, latestRange))
                        },
                    )
                },
            )
            Text(text = valueText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }

        if (dragging) {
            // Un `Popup` y no un simple desplazamiento: las perillas viven en contenedores con
            // scroll que recortan a sus límites, así que una perilla agrandada dibujada dentro
            // quedaría cortada — y las de los bordes, a medias.
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, liftPx),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // El valor, **arriba del todo y fuera de la perilla**: es lo que hay que
                    // leer mientras se ajusta, y es la parte del conjunto que más lejos queda
                    // de la mano.
                    Surface(
                        shadowElevation = 8.dp,
                        tonalElevation = 8.dp,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = valueText,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shadowElevation = 8.dp,
                        tonalElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            KnobDial(
                                value = value,
                                range = range,
                                enabled = true,
                                size = KNOB_SIZE_DRAGGING,
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A qué valor lleva un arrastre de [dragPx] píxeles desde [anchor].
 *
 * Está fuera del composable **para poder probarlo**: es la parte del gesto que puede estar mal
 * de una forma que no se ve mirando la pantalla —saltarse una posición de una lista de 28, o
 * ir acumulando error— y es lo único de todo esto que no exige un dedo para comprobarse.
 *
 * ⚠️ **Siempre desde [anchor], nunca desde el valor actual.** Recalcular desde el último valor
 * emitido acumularía el redondeo a pasos enteros en cada evento de arrastre, y un gesto largo
 * acabaría lejos de donde el dedo dice. Partiendo del ancla, ir y volver con el dedo devuelve
 * exactamente al valor de partida.
 */
internal fun knobValueAt(
    anchor: Double,
    dragPx: Float,
    step: Double,
    range: ClosedRange<Double>,
): Double {
    val steps = (dragPx / KNOB_PIXELS_PER_STEP).roundToInt()
    return (anchor + steps * step).coerceIn(range.start, range.endInclusive)
}

/** El disco de una perilla, sin texto ni gesto: lo comparten el tamaño normal y el agrandado. */
@Composable
private fun KnobDial(
    value: Double?,
    range: ClosedRange<Double>,
    enabled: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val span = (range.endInclusive - range.start).takeIf { it > 0.0 } ?: 1.0
    val indicator =
        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val dial = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        drawCircle(color = dial, radius = radius)
        val ratio = value?.let { ((it - range.start) / span).coerceIn(0.0, 1.0) } ?: 0.0
        // 270° de recorrido, empezando abajo a la izquierda — la convención de cualquier
        // perilla física.
        val angle = Math.toRadians(KNOB_START_DEGREES + ratio * KNOB_SWEEP_DEGREES)
        drawLine(
            color = indicator,
            start = center,
            end = androidx.compose.ui.geometry.Offset(
                x = center.x + (radius * 0.8f) * kotlin.math.cos(angle).toFloat(),
                y = center.y + (radius * 0.8f) * kotlin.math.sin(angle).toFloat(),
            ),
            strokeWidth = 4f,
        )
    }
}

/**
 * Píxeles de arrastre horizontal que avanzan **un paso**.
 *
 * ⚠️ **Es el número a tocar si el gesto se siente mal, y no hay ninguna referencia que diga
 * cuál es el bueno** — solo el dedo. Bajado de 32 a 24 el 2026-09-06 tras probarlo: el gesto ya
 * funcionaba bien (arreglado el bug de `pointerInput` que lo cancelaba a mitad de arrastre) pero
 * se sentía lento. Con 28 frecuencias siguen siendo ~670 px para recorrer la lista entera —más
 * de lo que mide la pantalla, así que cada posición se sigue apuntando sin pelearse—, solo que
 * gira algo más rápido. Si resulta demasiado rápido, subirlo; bajarlo más lo hace más ágil
 * todavía.
 */
private const val KNOB_PIXELS_PER_STEP = 24f

/** Ancho de la celda de una perilla en la fila. */
private val KNOB_SLOT_WIDTH = 76.dp

private val KNOB_SIZE = 44.dp

/** El tamaño mientras se arrastra: suficientemente más grande para leerlo de un vistazo. */
private val KNOB_SIZE_DRAGGING = 96.dp

/**
 * Cuánto se sube el conjunto proyectado respecto de la perilla original.
 *
 * ⚠️ **En `dp` y no en píxeles, a propósito.** La primera versión usaba píxeles crudos, que en
 * una pantalla densa se traducen en menos distancia física — justo la magnitud que aquí importa,
 * porque lo que tiene que caber debajo es un dedo y una mano, que miden lo que miden en
 * milímetros y no en píxeles.
 *
 * El conjunto (etiqueta de valor + perilla de 96 dp + nombre) mide unos 190 dp, así que con 240
 * el borde de abajo queda ~50 dp por encima del punto donde está el dedo. **Si aun así la mano
 * lo tapa, subir esto es lo único que hace falta.**
 */
private val KNOB_LIFT = 240.dp

/** Dónde empieza el recorrido de la aguja: abajo a la izquierda (135°). */
private const val KNOB_START_DEGREES = 135.0

/** Cuánto barre: 270°, dejando el hueco de abajo como en una perilla real. */
private const val KNOB_SWEEP_DEGREES = 270.0
