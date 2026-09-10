package dev.alonx3.ktnacontrol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alonx3.ktnacontrol.R
import dev.alonx3.ktnacontrol.protocol.AmpCategory
import dev.alonx3.ktnacontrol.protocol.AmpType
import dev.alonx3.ktnacontrol.protocol.AmpVariationUi
import dev.alonx3.ktnacontrol.ui.theme.KTNAControlTheme
import dev.alonx3.ktnacontrol.ui.theme.Spacing
import dev.alonx3.ktnacontrol.usb.UsbConnectionState
import kotlin.math.roundToInt

/**
 * **El bloque de controles del amplificador**, sin el canal y sin los "sin perilla física".
 *
 * Es el composable que hace que [AmpScreen] y `SlidersPane` pinten lo mismo sin copiarlo
 * (CLAUDE.md §4.2): la pantalla en vivo lo usa como su cuerpo, y `SlidersPane` —que sirve
 * también al editor offline de la Biblioteca— lo llama en el sitio exacto donde antes tenía
 * este código, así que **el orden de esa pantalla no cambia**.
 *
 * Qué controles son "del amplificador" no se decide aquí a ojo: está en [AmpDomain], que es
 * Kotlin puro y tiene tests. Los niveles salen de [AmpDomain.LEVELS], que es una resta —todo lo
 * que ningún efecto reclama— para que añadir un nivel nuevo no pueda dejarlo invisible.
 *
 * @param canEdit el contrato de Edit Mode, decidido por quien llama (§4.2). Esta capa no sabe
 *   qué es el edit mode; solo apaga lo que le digan.
 */
@Composable
internal fun AmpSection(
    levels: Map<LevelId, Int?>,
    selectors: Map<SelectorId, Int?>,
    variation: AmpVariationUi,
    canEdit: Boolean,
    onLevelChanged: (LevelId, Int) -> Unit,
    onSelectorChanged: (SelectorId, Int) -> Unit,
    onAmpVariationChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.section_amp))
        AmpModelSelector(
            model = selectors[SelectorId.AMP_TYPE],
            variation = variation,
            canEdit = canEdit,
            onModelSelected = { value -> onSelectorChanged(SelectorId.AMP_TYPE, value) },
            onAmpVariationChanged = onAmpVariationChanged,
        )

        // ❌ **Bright (`60 00 00 29`) y Gain SW (`60 00 00 2A`) ya no se ofrecen.** Probados
        // contra el amplificador real (2026-09-06) con la instrumentación de SET+GET: no
        // cambian el sonido **ni el estado interno**. Un control que acepta el gesto y no hace
        // nada es peor que ninguno, así que salen de la UI. Las direcciones se conservan
        // registradas y documentadas en `KatanaAddresses` por si algún día aparece evidencia
        // de que estaban mal identificadas — el precedente del reverb (CLAUDE.md §5).
        // Están marcados como `ControlDomain.RETIRED` en [AmpDomain] para que el test de
        // cobertura no los confunda con un olvido.
        //
        // ⚠️ **Solo ya no está aquí** (QA 2026-09-09, bloque C): pasó a ser una tarjeta más de
        // `EffectsSection`, después de Reverb. El porqué está en [AmpDomain.EFFECT_SELECTORS];
        // el reparto lo fija un test, así que esto no puede quedarse a medias.

        PagedVerticalParams(
            params = ampLevelParams(levels, onLevelChanged),
            canEdit = canEdit,
            columns = PANEL_COLUMNS,
            barHeight = PANEL_BAR_HEIGHT,
            barWidth = PANEL_BAR_WIDTH,
        )
    }
}

/**
 * Los seis niveles del panel como [VerticalParam] (QA 2026-09-09, bloque C — cierre del pase
 * visual).
 *
 * ⚠️ **Los sliders del panel eran lo único que se había quedado en horizontal.** El bloque C
 * convirtió las once tarjetas de efecto y las del panel sin perilla física (Noise Gate, Contour,
 * EQ1, EQ2) a tiras verticales paginadas, pero Gain/Volume/Bass/Middle/Treble/Presence seguían
 * siendo un `LevelRow` cada uno, apilados. O sea que la pantalla de amplificador enseñaba **dos
 * lenguajes de control distintos** —barras verticales abajo, sliders horizontales arriba— para
 * exactamente el mismo tipo de parámetro: un nivel continuo de 0 a 100.
 *
 * Se reutiliza tal cual la cadena que ya existía ([VerticalParam] → [PagedVerticalParams]), sin
 * pieza nueva.
 *
 * ⚠️ **El orden no es el de [AmpDomain.LEVELS] y las columnas no se derivan del ancho**, que son
 * las dos únicas cosas en las que esta tira se aparta del resto. Las dos salen del mismo motivo:
 * aquí cada página es un **grupo que se ajusta junto** —`Bass · Middle · Treble` primero,
 * `Gain · Volume · Presence` después— y no una fila de relleno. El porqué, con la tabla, está en
 * [AmpDomain.PANEL_LEVEL_ORDER] y en [ControlPaging.columnsFor].
 *
 * Se usan los nombres cortos ([LevelId.shortLabelRes]) y no los de siempre por lo mismo que en las
 * tarjetas de efecto: `labelRes` lleva el valor incrustado ("Gain: 42") y una celda de 80 dp solo
 * tiene sitio para el nombre — el valor ya se pinta encima de la barra.
 */
@Composable
private fun ampLevelParams(
    levels: Map<LevelId, Int?>,
    onLevelChanged: (LevelId, Int) -> Unit,
): List<VerticalParam> = AmpDomain.PANEL_LEVEL_ORDER.map { id ->
    val level = levels[id]
    VerticalParam(
        label = stringResource(id.shortLabelRes),
        value = level?.toDouble(),
        valueText = level?.toString(),
        range = 0.0..100.0,
        onValueChanged = { value -> onLevelChanged(id, value.roundToInt()) },
    )
}

/**
 * **La pantalla del amplificador en vivo**: el primer trozo de la "UI real de control"
 * (BACKLOG.md, bloque 6).
 *
 * Reúne todo lo cableado que es del amplificador y **ni un control de efecto**: canal, modelo y
 * variación, los seis niveles del panel, Solo, Noise Gate, Contour, EQ1 y EQ2, y la cadena con
 * su diagrama. El reparto sale de [AmpDomain] y está probado en JVM; lo que no se puede probar
 * sin dispositivo es que se vea bien y quepa — para eso está el `@Preview` de abajo y la prueba
 * a mano del BACKLOG.
 *
 * **Todo bajo `canEdit`, con una excepción de siempre**: el selector de canal, que sigue
 * habilitado con Edit Mode apagado porque cambiar de canal es un comando básico y no un ajuste
 * fino (CLAUDE.md §4.2). El gate se calcula aquí, como en `SlidersPane`; `device/` sigue sin
 * saber qué es el edit mode.
 *
 * ⚠️ **Lo que deliberadamente NO trae**, aunque `SlidersPane` sí lo tenga arriba: guardar un
 * preset en un canal y exportar a `.tsl`. Son operaciones sobre **el preset entero**, no sobre
 * el amplificador, y duplicarlas en cada pantalla de dominio sería tener el mismo botón
 * destructivo en cuatro sitios. Se quedan donde están hasta que exista `PresetsScreen`. Releer
 * sí está: es de solo lectura y es lo que repuebla justo lo que esta pantalla enseña.
 */
@Composable
internal fun AmpScreen(
    levels: Map<LevelId, Int?>,
    selectors: Map<SelectorId, Int?>,
    variation: AmpVariationUi,
    state: UsbConnectionState,
    editMode: Boolean,
    onEditModeChanged: (Boolean) -> Unit,
    onLevelChanged: (LevelId, Int) -> Unit,
    onSelectorChanged: (SelectorId, Int) -> Unit,
    onAmpVariationChanged: (Boolean) -> Unit,
    noPanelParams: Map<NoPanelParamId, Int?>,
    contourSlotValues: List<DebugConnectionViewModel.ContourSlotValues>,
    eq1Raw: Map<String, Int?>,
    eq2Raw: Map<String, Int?>,
    chainSlotValues: List<Int?>,
    onNoPanelParamChanged: (NoPanelParamId, Int) -> Unit,
    onContourShapeChanged: (Int, Int) -> Unit,
    onContourFreqShiftChanged: (Int, Int) -> Unit,
    onEqParamChanged: (Boolean, String, Double) -> Unit,
    reloadInFlight: Boolean,
    onRefreshClicked: () -> Unit,
    onScanClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // La única definición de `canEdit` del proyecto, y con tests (CLAUDE.md §4.2).
    val availability = ShellState.availabilityOf(state, editMode)
    val canEdit = availability.canEdit

    Column(
        modifier = modifier
            .fillMaxSize()
            .knobAwareVerticalScroll()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        // ⚠️ **Sin amplificador no se pintan los controles en gris: se explica qué falta.** Un
        // panel apagado sin motivo se lee como un bug; con una frase es una regla. Y el botón
        // que lo arregla —buscar— va aquí, no escondido en Logs.
        if (availability is ControlAvailability.NoAmp) {
            NoAmpNotice(state = availability.state, onScan = onScanClicked)
            return@Column
        }

        // ⚠️ **El único GET de la pantalla** (2026-09-10): antes cada parámetro llevaba el suyo
        // dentro de la celda. Este relee el estado **entero** del amplificador de una vez —un
        // dump, no cincuenta peticiones sueltas— y por eso va arriba del todo: es lo primero que
        // se busca cuando lo que se ve en pantalla y lo que suena no coinciden. CLAUDE.md §4.10.
        //
        // Releer **no es destructivo** —solo lee— así que no cae bajo `canEdit`: aquí ya se sabe
        // que hay cable, porque sin él esta pantalla no llega.
        OutlinedButton(
            onClick = onRefreshClicked,
            enabled = !reloadInFlight,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.sm),
        ) {
            Text(
                if (reloadInFlight) stringResource(R.string.refresh_in_flight)
                else stringResource(R.string.refresh_state)
            )
        }

        // El toggle va en cada pantalla que edite: sin él, quien vea los controles apagados
        // tendría que cambiar de sección para entender por qué.
        EditModeToggle(
            editMode = editMode,
            enabled = true,
            onEditModeChanged = onEditModeChanged,
        )
        if (!editMode) EditModeNotice()

        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))

        // ✅ Confirmado en las dos direcciones (CLAUDE.md §5.1). **Único control que sigue
        // habilitado con Edit Mode apagado**, a propósito.
        SectionHeader(stringResource(R.string.section_channel))
        ChipSelector(
            label = stringResource(R.string.section_channel),
            options = channelOptions(),
            selected = selectors[SelectorId.ACTIVE_CHANNEL],
            // Sigue siendo la excepción del contrato de Edit Mode (§4.2): habilitado aunque
            // `canEdit` sea false. Aquí ya hay cable, así que la condición es simplemente true.
            enabled = true,
            onSelected = { value -> onSelectorChanged(SelectorId.ACTIVE_CHANNEL, value) },
        )

        AmpSection(
            levels = levels,
            selectors = selectors,
            variation = variation,
            canEdit = canEdit,
            onLevelChanged = onLevelChanged,
            onSelectorChanged = onSelectorChanged,
            onAmpVariationChanged = onAmpVariationChanged,
        )

        Spacer(modifier = Modifier.height(Spacing.sm))
        // Noise Gate, Contour, EQ1/EQ2 y la cadena. **Es el mismo composable que usa
        // `SlidersPane`**, no una copia: todo lo que hay dentro es dominio de amplificador, así
        // que la pantalla se lo lleva entero. La cadena incluida — ver CLAUDE.md §4.2 para el
        // criterio de por qué no espera a una pantalla propia.
        NoPanelPane(
            selectors = selectors,
            params = noPanelParams,
            contourSlots = contourSlotValues,
            eq1Raw = eq1Raw,
            eq2Raw = eq2Raw,
            chainSlots = chainSlotValues,
            canEdit = canEdit,
            onSelectorChanged = onSelectorChanged,
            onParamChanged = onNoPanelParamChanged,
            onContourShapeChanged = onContourShapeChanged,
            onContourFreqShiftChanged = onContourFreqShiftChanged,
            onEqParamChanged = onEqParamChanged,
        )
    }
}

/**
 * ⚠️ **Esto es lo único que sustituye a un test de renderizado, y no lo sustituye del todo.**
 *
 * Un `@Preview` deja ver la composición sin amplificador y sin dispositivo, pero no comprueba
 * nada por sí solo: que quepa, que se lea y que los controles estén donde el ojo los espera hay
 * que mirarlo. La prueba a mano está en BACKLOG.md, "Pendiente por probar".
 */
@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun AmpScreenPreview() {
    KTNAControlTheme {
        AmpScreen(
            levels = AmpDomain.LEVELS.associateWith { 50 },
            selectors = mapOf(
                SelectorId.ACTIVE_CHANNEL to 1,
                SelectorId.AMP_CATEGORY to AmpCategory.CLEAN.value,
                SelectorId.AMP_TYPE to AmpType.CLEAN.value,
                SelectorId.AMP_SOLO to SWITCH_OFF_VALUE,
                SelectorId.NOISE_GATE to SWITCH_ON_VALUE,
                SelectorId.CONTOUR to SWITCH_OFF_VALUE,
                SelectorId.CONTOUR_SELECT to 0,
                SelectorId.EQ1_POSITION to 0,
                SelectorId.EQ2_POSITION to 0,
                SelectorId.CHAIN_TYPE to 0,
                SelectorId.LOOP_POSITION to 0,
                SelectorId.PEDAL_FX_POSITION to 0,
            ),
            variation = AmpVariationUi(applies = true, on = false),
            state = UsbConnectionState.Connected("KATANA"),
            editMode = true,
            onEditModeChanged = {},
            onLevelChanged = { _, _ -> },
            onSelectorChanged = { _, _ -> },
            onAmpVariationChanged = {},
            noPanelParams = NoPanelParamId.entries.associateWith { 0 },
            contourSlotValues = emptyList(),
            eq1Raw = emptyMap(),
            eq2Raw = emptyMap(),
            chainSlotValues = List(20) { it },
            onNoPanelParamChanged = { _, _ -> },
            onContourShapeChanged = { _, _ -> },
            onContourFreqShiftChanged = { _, _ -> },
            onEqParamChanged = { _, _, _ -> },
            reloadInFlight = false,
            onRefreshClicked = {},
            onScanClicked = {},
        )
    }
}

/**
 * **El selector de modelo de amplificador, en dos páginas deslizables** (QA 2026-09-09, bloque C).
 *
 * Sustituye a lo que había —un `ChipSelector` de las cinco categorías **más** un desplegable con
 * los treinta modelos **más** el switch en su propia fila—, que tenía dos problemas: el
 * desplegable escondía veinte modelos detrás de un toque y un scroll, y los cinco canales
 * aparecían **dos veces**, como chip de categoría y como entrada del desplegable, sin que nada
 * dijera que eran lo mismo.
 *
 * Ahora son dos páginas con el mismo gesto que ya usa el resto de la app:
 *
 * | Página | Qué ofrece | Switch de variación |
 * | --- | --- | --- |
 * | `AMP TYPE` | los **cinco canales** de la perilla física | **sí** |
 * | `SNEAKY AMPS` | los **veinte** modelos individuales, en rejilla de 3 | **no existe** |
 *
 * ⚠️ **Qué modelo cae en qué página no se decide aquí**: sale de [AmpModelPage], que es Kotlin
 * puro y tiene tests. Un modelo que se quedara fuera de las dos páginas no daría error, solo
 * sería inelegible para siempre — exactamente la clase de fallo que hay que probar en JVM.
 *
 * ✅ **Se usa `HorizontalPager` de `androidx.compose.foundation.pager`**, y **no añade ninguna
 * dependencia**: `foundation` ya está en el classpath por Material 3, así que la regla de §6
 * ("mantener la lista corta") se respeta sin discusión. La alternativa era un `Row` con
 * `horizontalScroll` y `snapFlingBehavior` a mano, que es reimplementar el mismo componente peor:
 * `HorizontalPager` ya trae el enganche por página, el arrastre y `settledPage`, que es lo que
 * hace falta para saber si toca pintar el switch.
 *
 * ⚠️ **`beyondViewportPageCount = 1` es deliberado**: con las dos páginas compuestas, cambiar de
 * una a otra no reconstruye veinte chips a mitad del gesto, que es lo que hacía que el arrastre
 * se sintiera pegajoso en la primera pasada.
 */
@Composable
private fun AmpModelSelector(
    model: Int?,
    variation: AmpVariationUi,
    canEdit: Boolean,
    onModelSelected: (Int) -> Unit,
    onAmpVariationChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = AmpModelPage.entries
    val pagerState = rememberPagerState(
        // Arranca en la página del modelo activo: con un sneaky puesto, empezar en `AMP TYPE`
        // dejaría la selección fuera de la vista sin decirlo.
        initialPage = pages.indexOf(AmpModelPage.pageFor(model)),
        pageCount = { pages.size },
    )

    // ⚠️ Al cambiar el canal cambia el modelo, y con él la página que le corresponde. Seguirlo es
    // lo que evita que tras un cambio de canal el selector enseñe una página donde no hay nada
    // marcado. `settledPage` y no `currentPage`: durante el arrastre no se reposiciona solo.
    val targetPage = pages.indexOf(AmpModelPage.pageFor(model))
    LaunchedEffect(targetPage) {
        if (pagerState.settledPage != targetPage) pagerState.animateScrollToPage(targetPage)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        val visible = pages[pagerState.currentPage]

        // El título de la página y, **en la misma fila**, el botón de variación: el encargo pedía
        // explícitamente que el switch no ocupara una fila propia de layout.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(visible.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
            PageDots(count = pages.size, current = pagerState.currentPage)
            // ⚠️ En `SNEAKY AMPS` **no se pinta**, ni apagado: ninguno de esos veinte tiene
            // gemelo `Var [...]`, y un interruptor muerto para siempre solo genera preguntas.
            if (AmpModelPage.showsVariationSwitch(visible)) {
                Spacer(modifier = Modifier.width(Spacing.sm))
                VariationButton(
                    on = variation.on,
                    enabled = canEdit && variation.applies,
                    onToggle = { onAmpVariationChanged(!variation.on) },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        ) { index ->
            when (pages[index]) {
                // ⚠️ Los cinco canales, **no los diez**: el `Var` es el botón de arriba, no un
                // chip aparte. Y con `Var [Crunch]` activo el chip marcado es Crunch — eso lo
                // resuelve `selectedBaseModel`, no este composable.
                AmpModelPage.AMP_TYPE -> ChipSelector(
                    label = "",
                    options = AmpModelPage.optionsOf(AmpModelPage.AMP_TYPE),
                    selected = AmpModelPage.selectedBaseModel(model),
                    enabled = canEdit,
                    // Elegir un canal respeta la variación que ya estuviera puesta: cambiar de
                    // Crunch a Lead con el Var puesto lleva a `Var [Lead]`, como el panel.
                    onSelected = { base ->
                        val target = AmpType.fromValue(base)?.category
                            ?.typeValue(variation.on) ?: base
                        onModelSelected(target)
                    },
                )

                AmpModelPage.SNEAKY_AMPS -> ModelGrid(
                    options = AmpModelPage.optionsOf(AmpModelPage.SNEAKY_AMPS),
                    selected = model,
                    enabled = canEdit,
                    onSelected = onModelSelected,
                )
            }
        }
    }
}

/** El nombre de cada página del selector, en `strings.xml` como manda §6. */
private val AmpModelPage.titleRes: Int
    get() = when (this) {
        AmpModelPage.AMP_TYPE -> R.string.amp_page_type
        AmpModelPage.SNEAKY_AMPS -> R.string.amp_page_sneaky
    }

/**
 * Los veinte modelos individuales en **rejilla de tres columnas**, no en un desplegable.
 *
 * ⚠️ **A mano con `Row`s en vez de `LazyVerticalGrid`, y no por gusto**: esta rejilla vive dentro
 * de una columna que ya hace scroll vertical, y anidar dos scrolls en el mismo eje es un error de
 * medición en Compose, no un detalle estético. Con veinte elementos fijos, trocear en filas de
 * tres cuesta una línea y no tiene ninguno de esos problemas.
 */
@Composable
private fun ModelGrid(
    options: List<Pair<Int, String>>,
    selected: Int?,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        options.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                row.forEach { (value, label) ->
                    FilterChip(
                        selected = value == selected,
                        onClick = { onSelected(value) },
                        enabled = enabled,
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Rellena la última fila para que sus chips midan lo mismo que los de arriba,
                // en vez de estirarse a repartirse el ancho entero.
                repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * El switch de variación como **botón redondo pequeño**: rojo encendido, gris de chasis apagado.
 *
 * ⚠️ **El color no es la única señal**, por lo de siempre en este proyecto (CLAUDE.md §4.6): lleva
 * `contentDescription` con su estado, así que TalkBack lo lee aunque el color no llegue. El rojo
 * es el `WarningRed` del tema, que solo se usa para texto de aviso — aquí es el único sitio donde
 * tiñe una forma, y no compite con los tres colores de slot porque no está en una tarjeta de
 * efecto.
 *
 * Va en la fila del título a propósito: el encargo pedía que **no ocupara una fila propia**.
 */
@Composable
private fun VariationButton(
    on: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(
        if (on) R.string.amp_variation_on else R.string.amp_variation_off
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.amp_variation),
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.outline
            },
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Box(
            modifier = modifier
                // El mínimo táctil se pone a mano porque esto no es un componente de Material:
                // el disco visible mide 20 dp, la zona que responde 48.
                .size(MIN_TOUCH_TARGET)
                .clickable(enabled = enabled, onClick = onToggle)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(VARIATION_DOT)
                    .clip(CircleShape)
                    .background(
                        when {
                            !enabled -> MaterialTheme.colorScheme.outline
                            on -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
    }
}

/** Dos puntitos que dicen en qué página del selector está uno. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(PAGE_DOT)
                    .clip(CircleShape)
                    .background(
                        if (index == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    ),
            )
        }
    }
}

/**
 * **Tres por página, fijos**: cada página del panel es una tríada que se toca junta. Ver
 * [AmpDomain.PANEL_LEVEL_ORDER].
 */
private const val PANEL_COLUMNS = 3

/**
 * La barra del panel es **más alta y más gruesa que la de las tarjetas de efecto** (2026-09-10),
 * y puede serlo justamente porque son tres por página: Gain, Bass y compañía son las que más se
 * ajustan al oído y las que más ganan con un recorrido largo, mientras que una tarjeta con 11
 * bandas de EQ no tendría sitio para esto. El estilo es el mismo —misma pista, mismo degradado,
 * misma tapa—; lo único que cambia son las dos medidas.
 */
private val PANEL_BAR_HEIGHT = 176.dp
private val PANEL_BAR_WIDTH = 34.dp

private val VARIATION_DOT = 20.dp
private val PAGE_DOT = 6.dp

/** El mínimo táctil de Material, a mano porque este control no es un componente de fábrica. */
private val MIN_TOUCH_TARGET = 48.dp
