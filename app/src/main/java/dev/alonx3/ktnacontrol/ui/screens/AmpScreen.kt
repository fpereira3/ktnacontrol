package dev.alonx3.ktnacontrol.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alonx3.ktnacontrol.R
import dev.alonx3.ktnacontrol.protocol.AmpCategory
import dev.alonx3.ktnacontrol.protocol.AmpType
import dev.alonx3.ktnacontrol.ui.theme.KTNAControlTheme
import dev.alonx3.ktnacontrol.usb.UsbConnectionState

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
 * @param diagnostics la tarjeta de diagnóstico del Solo, o **null para no pintarla**: pregunta
 *   cosas que solo el hardware puede contestar, así que offline no tiene sentido.
 */
@Composable
internal fun AmpSection(
    levels: Map<LevelId, Int?>,
    selectors: Map<SelectorId, Int?>,
    variationApplies: Boolean,
    ampSoloLevel: Int?,
    canEdit: Boolean,
    onLevelChanged: (LevelId, Int) -> Unit,
    onReadLevelClicked: (LevelId) -> Unit,
    onSelectorChanged: (SelectorId, Int) -> Unit,
    onAmpVariationChanged: (Boolean) -> Unit,
    onAmpSoloLevelChanged: (Int) -> Unit,
    onReadAmpSoloLevelClicked: () -> Unit,
    diagnostics: SoloDiagnostics?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.section_amp))
        ChipSelector(
            label = stringResource(R.string.amp_category),
            options = AmpCategory.entries.map { it.value to it.displayName },
            selected = selectors[SelectorId.AMP_CATEGORY],
            enabled = canEdit,
            onSelected = { value -> onSelectorChanged(SelectorId.AMP_CATEGORY, value) },
        )
        DropdownSelector(
            label = stringResource(R.string.amp_type),
            options = AmpType.entries.map { it.value to it.displayName },
            selected = selectors[SelectorId.AMP_TYPE],
            enabled = canEdit,
            onSelected = { value -> onSelectorChanged(SelectorId.AMP_TYPE, value) },
        )
        // El switch **lee** de `06 5C` y **escribe** por el modelo (`00 21`): esa dirección
        // solo reporta. Se apaga cuando el modelo activo no es uno de los cinco canales base,
        // porque entonces "variación" no tiene a qué referirse. Ver el KDoc de AMP_VARIATION.
        SwitchRow(
            label = stringResource(R.string.amp_variation),
            checked = selectors[SelectorId.AMP_VARIATION] == SWITCH_ON_VALUE,
            enabled = canEdit && variationApplies,
            onCheckedChange = onAmpVariationChanged,
        )

        // ❌ **Bright (`60 00 00 29`) y Gain SW (`60 00 00 2A`) ya no se ofrecen.** Probados
        // contra el amplificador real (2026-09-06) con la instrumentación de SET+GET: no
        // cambian el sonido **ni el estado interno**. Un control que acepta el gesto y no hace
        // nada es peor que ninguno, así que salen de la UI. Las direcciones se conservan
        // registradas y documentadas en `KatanaAddresses` por si algún día aparece evidencia
        // de que estaban mal identificadas — el precedente del reverb (CLAUDE.md §5).
        // Están marcados como `ControlDomain.RETIRED` en [AmpDomain] para que el test de
        // cobertura no los confunda con un olvido.
        SwitchRow(
            label = stringResource(R.string.amp_solo),
            checked = selectors[SelectorId.AMP_SOLO] == SWITCH_ON_VALUE,
            enabled = canEdit,
            onCheckedChange = { on ->
                onSelectorChanged(
                    SelectorId.AMP_SOLO,
                    if (on) SWITCH_ON_VALUE else SWITCH_OFF_VALUE,
                )
            },
        )
        LevelControl(
            label = stringResource(
                R.string.amp_solo_level,
                ampSoloLevel?.toString() ?: stringResource(R.string.debug_connection_unknown_value),
            ),
            level = ampSoloLevel,
            enabled = canEdit,
            onLevelChanged = onAmpSoloLevelChanged,
            onRead = onReadAmpSoloLevelClicked,
        )

        diagnostics?.let { DiagnosticsCard(diagnostics = it, canEdit = canEdit) }

        AmpDomain.LEVELS.forEach { id ->
            LevelRow(id, levels[id], canEdit, onLevelChanged, onReadLevelClicked)
        }
    }
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
    variationApplies: Boolean,
    ampSoloLevel: Int?,
    state: UsbConnectionState,
    editMode: Boolean,
    onEditModeChanged: (Boolean) -> Unit,
    onLevelChanged: (LevelId, Int) -> Unit,
    onReadLevelClicked: (LevelId) -> Unit,
    onSelectorChanged: (SelectorId, Int) -> Unit,
    onAmpVariationChanged: (Boolean) -> Unit,
    onAmpSoloLevelChanged: (Int) -> Unit,
    onReadAmpSoloLevelClicked: () -> Unit,
    diagnostics: SoloDiagnostics,
    noPanelParams: Map<NoPanelParamId, Int?>,
    contourSlotValues: List<DebugConnectionViewModel.ContourSlotValues>,
    eq1Raw: Map<String, Int?>,
    eq2Raw: Map<String, Int?>,
    chainSlotValues: List<Int?>,
    onNoPanelParamChanged: (NoPanelParamId, Int) -> Unit,
    onReadNoPanelParamClicked: (NoPanelParamId) -> Unit,
    onContourShapeChanged: (Int, Int) -> Unit,
    onContourFreqShiftChanged: (Int, Int) -> Unit,
    onReadContourSlotClicked: (Int) -> Unit,
    onEqParamChanged: (Boolean, String, Double) -> Unit,
    onReadEqParamClicked: (Boolean, String) -> Unit,
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // ⚠️ **Sin amplificador no se pintan los controles en gris: se explica qué falta.** Un
        // panel apagado sin motivo se lee como un bug; con una frase es una regla. Y el botón
        // que lo arregla —buscar— va aquí, no escondido en Logs.
        if (availability is ControlAvailability.NoAmp) {
            NoAmpNotice(state = availability.state, onScan = onScanClicked)
            return@Column
        }

        // El toggle va en cada pantalla que edite: sin él, quien vea los controles apagados
        // tendría que cambiar de sección para entender por qué.
        EditModeToggle(
            editMode = editMode,
            enabled = true,
            onEditModeChanged = onEditModeChanged,
        )
        if (!editMode) EditModeNotice()

        // Releer **no es destructivo** —solo lee— así que no cae bajo `canEdit`: aquí ya se sabe
        // que hay cable, porque sin él esta pantalla no llega.
        OutlinedButton(
            onClick = onRefreshClicked,
            enabled = !reloadInFlight,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(
                if (reloadInFlight) stringResource(R.string.refresh_in_flight)
                else stringResource(R.string.refresh_state)
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
            variationApplies = variationApplies,
            ampSoloLevel = ampSoloLevel,
            canEdit = canEdit,
            onLevelChanged = onLevelChanged,
            onReadLevelClicked = onReadLevelClicked,
            onSelectorChanged = onSelectorChanged,
            onAmpVariationChanged = onAmpVariationChanged,
            onAmpSoloLevelChanged = onAmpSoloLevelChanged,
            onReadAmpSoloLevelClicked = onReadAmpSoloLevelClicked,
            diagnostics = diagnostics,
        )

        Spacer(modifier = Modifier.height(8.dp))
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
            onReadParam = onReadNoPanelParamClicked,
            onContourShapeChanged = onContourShapeChanged,
            onContourFreqShiftChanged = onContourFreqShiftChanged,
            onReadContourSlot = onReadContourSlotClicked,
            onEqParamChanged = onEqParamChanged,
            onReadEqParam = onReadEqParamClicked,
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
            variationApplies = true,
            ampSoloLevel = 60,
            state = UsbConnectionState.Connected("KATANA"),
            editMode = true,
            onEditModeChanged = {},
            onLevelChanged = { _, _ -> },
            onReadLevelClicked = {},
            onSelectorChanged = { _, _ -> },
            onAmpVariationChanged = {},
            onAmpSoloLevelChanged = {},
            onReadAmpSoloLevelClicked = {},
            diagnostics = SoloDiagnostics(
                panelEnabled = null,
                panelLevel = null,
                onPanelEnabledChanged = {},
                onPanelLevelChanged = {},
                onReadPanelClicked = {},
                onProbeSoloPreamp = {},
                onProbeSoloPanel = {},
            ),
            noPanelParams = NoPanelParamId.entries.associateWith { 0 },
            contourSlotValues = emptyList(),
            eq1Raw = emptyMap(),
            eq2Raw = emptyMap(),
            chainSlotValues = List(20) { it },
            onNoPanelParamChanged = { _, _ -> },
            onReadNoPanelParamClicked = {},
            onContourShapeChanged = { _, _ -> },
            onContourFreqShiftChanged = { _, _ -> },
            onReadContourSlotClicked = {},
            onEqParamChanged = { _, _, _ -> },
            onReadEqParamClicked = { _, _ -> },
            reloadInFlight = false,
            onRefreshClicked = {},
            onScanClicked = {},
        )
    }
}
