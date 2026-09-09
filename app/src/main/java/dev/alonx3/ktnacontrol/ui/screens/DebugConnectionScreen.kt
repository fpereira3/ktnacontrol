package dev.alonx3.ktnacontrol.ui.screens

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.Saver
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alonx3.ktnacontrol.protocol.AmpCategory
import dev.alonx3.ktnacontrol.protocol.AmpType
import dev.alonx3.ktnacontrol.protocol.BoostType
import dev.alonx3.ktnacontrol.protocol.DelayHighCutFrequency
import dev.alonx3.ktnacontrol.protocol.ChainBlock
import dev.alonx3.ktnacontrol.protocol.DelayType
import dev.alonx3.ktnacontrol.protocol.EqParams
import dev.alonx3.ktnacontrol.protocol.EqSelection
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.ParamSpec
import dev.alonx3.ktnacontrol.protocol.PresetSave
import dev.alonx3.ktnacontrol.protocol.ModFxInternalParams
import dev.alonx3.ktnacontrol.protocol.ParamKind
import dev.alonx3.ktnacontrol.protocol.ModFxType
import dev.alonx3.ktnacontrol.protocol.displayBounds
import dev.alonx3.ktnacontrol.protocol.rawToDisplay
import dev.alonx3.ktnacontrol.protocol.ReverbHighCutFrequency
import dev.alonx3.ktnacontrol.protocol.ReverbLowCutFrequency
import dev.alonx3.ktnacontrol.protocol.ReverbType
import dev.alonx3.ktnacontrol.protocol.EffectColor
import dev.alonx3.ktnacontrol.R
import dev.alonx3.ktnacontrol.ui.theme.KTNAControlTheme
import dev.alonx3.ktnacontrol.usb.UsbConnectionState
import dev.alonx3.ktnacontrol.usb.UsbLogLine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import dev.alonx3.ktnacontrol.protocol.ChainDiagramBlock
import dev.alonx3.ktnacontrol.protocol.displayStep

@Composable
fun DebugConnectionScreen(
    modifier: Modifier = Modifier,
    viewModel: DebugConnectionViewModel = viewModel(),
) {
    val log by viewModel.log.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val levels by viewModel.levels.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val selectors by viewModel.selectors.collectAsStateWithLifecycle()
    val effectColors by viewModel.effectColors.collectAsStateWithLifecycle()
    val effectEnabled by viewModel.effectEnabled.collectAsStateWithLifecycle()
    val variationApplies by viewModel.ampVariationApplies.collectAsStateWithLifecycle()
    val exportInFlight by viewModel.exportInFlight.collectAsStateWithLifecycle()
    val reloadInFlight by viewModel.reloadInFlight.collectAsStateWithLifecycle()
    val effectTypes by viewModel.effectTypes.collectAsStateWithLifecycle()
    val boosterParams by viewModel.boosterParams.collectAsStateWithLifecycle()
    val boosterSoloEnabled by viewModel.boosterSoloEnabled.collectAsStateWithLifecycle()
    val ampSoloLevel by viewModel.ampSoloLevel.collectAsStateWithLifecycle()
    val ampSoloEnabledPanel by viewModel.ampSoloEnabledPanel.collectAsStateWithLifecycle()
    val ampSoloLevelPanel by viewModel.ampSoloLevelPanel.collectAsStateWithLifecycle()
    val delayParams by viewModel.delayParams.collectAsStateWithLifecycle()
    val reverbParams by viewModel.reverbParams.collectAsStateWithLifecycle()
    val reverbTime by viewModel.reverbTime.collectAsStateWithLifecycle()
    val modChorusPreDelayLow by viewModel.modChorusPreDelayLow.collectAsStateWithLifecycle()
    val modChorusPreDelayHigh by viewModel.modChorusPreDelayHigh.collectAsStateWithLifecycle()
    val modFxInternalRaw by viewModel.modFxInternalRaw.collectAsStateWithLifecycle()
    val fxInternalRaw by viewModel.fxInternalRaw.collectAsStateWithLifecycle()
    val noPanelParams by viewModel.noPanelParams.collectAsStateWithLifecycle()
    val contourSlotValues by viewModel.contourSlots.collectAsStateWithLifecycle()
    val eq1Raw by viewModel.eq1Raw.collectAsStateWithLifecycle()
    val eq2Raw by viewModel.eq2Raw.collectAsStateWithLifecycle()
    val chainSlotValues by viewModel.chainSlots.collectAsStateWithLifecycle()
    val presetSaveInFlight by viewModel.presetSaveInFlight.collectAsStateWithLifecycle()
    val presetSendState by viewModel.presetSend.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.debug_connection_log_copied)

    DebugConnectionScreen(
        log = log,
        state = state,
        levels = levels,
        selectors = selectors,
        effectColors = effectColors,
        effectEnabled = effectEnabled,
        variationApplies = variationApplies,
        effectTypes = effectTypes,
        boosterParams = boosterParams,
        boosterSoloEnabled = boosterSoloEnabled,
        ampSoloLevel = ampSoloLevel,
        delayParams = delayParams,
        reverbParams = reverbParams,
        reverbTime = reverbTime,
        modChorusPreDelayLow = modChorusPreDelayLow,
        modChorusPreDelayHigh = modChorusPreDelayHigh,
        modInternalRaw = modFxInternalRaw,
        fxInternalRaw = fxInternalRaw,
        editMode = editMode,
        snackbarHostState = snackbarHostState,
        onScanClicked = viewModel::onScanClicked,
        onHandshakeClicked = viewModel::onHandshakeClicked,
        onHandshakeRealVersionClicked = viewModel::onHandshakeRealVersionClicked,
        onIdentityRequestClicked = viewModel::onIdentityRequestClicked,
        onReadDeviceNameClicked = viewModel::onReadDeviceNameClicked,
        onReadPresetNamesClicked = viewModel::onReadPresetNamesClicked,
        onEditModeChanged = viewModel::onEditModeChanged,
        onReadMemoryDumpClicked = viewModel::onReadMemoryDumpClicked,
        onLevelChanged = viewModel::onLevelChanged,
        onReadLevelClicked = viewModel::onReadLevelClicked,
        onSelectorChanged = viewModel::onSelectorChanged,
        onAmpVariationChanged = viewModel::onAmpVariationChanged,
        onEffectColorChanged = viewModel::onEffectColorChanged,
        onEffectEnabledChanged = viewModel::onEffectEnabledChanged,
        onEffectTypeChanged = viewModel::onEffectTypeChanged,
        onBoosterParamChanged = viewModel::onBoosterParamChanged,
        onReadBoosterParamClicked = viewModel::onReadBoosterParamClicked,
        onBoosterSoloEnabledChanged = viewModel::onBoosterSoloEnabledChanged,
        onAmpSoloLevelChanged = viewModel::onAmpSoloLevelChanged,
        onReadAmpSoloLevelClicked = viewModel::onReadAmpSoloLevelClicked,
        diagnostics = SoloDiagnostics(
            panelEnabled = ampSoloEnabledPanel,
            panelLevel = ampSoloLevelPanel,
            onPanelEnabledChanged = viewModel::onAmpSoloPanelEnabledChanged,
            onPanelLevelChanged = viewModel::onAmpSoloPanelLevelChanged,
            onReadPanelClicked = viewModel::onReadAmpSoloPanelClicked,
            onProbeSoloPreamp = viewModel::onProbeAmpSoloPreampClicked,
            onProbeSoloPanel = viewModel::onProbeAmpSoloPanelClicked,
        ),
        onDelayParamChanged = viewModel::onDelayParamChanged,
        onReadDelayParamClicked = viewModel::onReadDelayParamClicked,
        onReverbParamChanged = viewModel::onReverbParamChanged,
        onReadReverbParamClicked = viewModel::onReadReverbParamClicked,
        onReverbTimeChanged = viewModel::onReverbTimeChanged,
        onReadReverbTimeClicked = viewModel::onReadReverbTimeClicked,
        onModChorusPreDelayLowChanged = viewModel::onModChorusPreDelayLowChanged,
        onReadModChorusPreDelayLowClicked = viewModel::onReadModChorusPreDelayLowClicked,
        onModChorusPreDelayHighChanged = viewModel::onModChorusPreDelayHighChanged,
        onReadModChorusPreDelayHighClicked = viewModel::onReadModChorusPreDelayHighClicked,
        noPanelParams = noPanelParams,
        contourSlotValues = contourSlotValues,
        eq1Raw = eq1Raw,
        eq2Raw = eq2Raw,
        chainSlotValues = chainSlotValues,
        onNoPanelParamChanged = viewModel::onNoPanelParamChanged,
        onReadNoPanelParamClicked = viewModel::onReadNoPanelParamClicked,
        onContourShapeChanged = viewModel::onContourShapeChanged,
        onContourFreqShiftChanged = viewModel::onContourFreqShiftChanged,
        onReadContourSlotClicked = viewModel::onReadContourSlotClicked,
        onEqParamChanged = viewModel::onEqParamChanged,
        onReadEqParamClicked = viewModel::onReadEqParamClicked,
        presetSaveInFlight = presetSaveInFlight,
        onSavePreset = viewModel::onSavePresetClicked,
        // Mandar un preset de la Biblioteca al amplificador. `canSend` es el mismo `canEdit`
        // que gobierna el resto de la escritura destructiva (conectado + Edit Mode, §4.2); la
        // lógica de los dos pasos y del envío vive en `PresetSendFlow`, probada en JVM.
        presetSend = PresetSendControls(
            state = presetSendState,
            canSend = state is UsbConnectionState.Connected && editMode,
            onRequest = { name, image -> viewModel.presetSend.request(name, image) },
            onContinue = viewModel.presetSend::onContinue,
            onBack = viewModel.presetSend::onBack,
            onCancel = viewModel.presetSend::onCancel,
            onConfirmed = viewModel.presetSend::onConfirmed,
            onResultShown = viewModel.presetSend::onResultShown,
        ),
        onModParamChanged = { type, label, value -> viewModel.onModFxParamChanged(false, type, label, value) },
        onReadModParamClicked = { type, label -> viewModel.onReadModFxParamClicked(false, type, label) },
        onFxParamChanged = { type, label, value -> viewModel.onModFxParamChanged(true, type, label, value) },
        onReadFxParamClicked = { type, label -> viewModel.onReadModFxParamClicked(true, type, label) },
        reloadInFlight = reloadInFlight,
        onRefreshClicked = viewModel::onRefreshClicked,
        exportInFlight = exportInFlight,
        onExportPreset = viewModel::onExportPreset,
        onCopyLog = { text ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, text)))
                snackbarHostState.showSnackbar(copiedMessage)
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugConnectionScreen(
    log: List<UsbLogLine>,
    state: UsbConnectionState,
    levels: Map<LevelId, Int?>,
    selectors: Map<SelectorId, Int?>,
    effectColors: Map<EffectId, Int?>,
    effectEnabled: Map<EffectId, Boolean?>,
    variationApplies: Boolean,
    effectTypes: Map<EffectId, Int?>,
    boosterParams: Map<BoosterParamId, Int?>,
    boosterSoloEnabled: Boolean?,
    ampSoloLevel: Int?,
    delayParams: Map<DelayParamId, Int?>,
    reverbParams: Map<ReverbParamId, Int?>,
    editMode: Boolean,
    snackbarHostState: SnackbarHostState,
    onScanClicked: () -> Unit,
    onHandshakeClicked: () -> Unit,
    onHandshakeRealVersionClicked: () -> Unit,
    onIdentityRequestClicked: () -> Unit,
    onReadDeviceNameClicked: () -> Unit,
    onReadPresetNamesClicked: () -> Unit,
    onEditModeChanged: (Boolean) -> Unit,
    onReadMemoryDumpClicked: () -> Unit,
    onLevelChanged: (LevelId, Int) -> Unit,
    onReadLevelClicked: (LevelId) -> Unit,
    onSelectorChanged: (SelectorId, Int) -> Unit,
    onAmpVariationChanged: (Boolean) -> Unit,
    onEffectColorChanged: (EffectId, Int) -> Unit,
    onEffectEnabledChanged: (EffectId, Boolean) -> Unit,
    onEffectTypeChanged: (EffectId, Int) -> Unit,
    onBoosterParamChanged: (BoosterParamId, Int) -> Unit,
    onReadBoosterParamClicked: (BoosterParamId) -> Unit,
    onBoosterSoloEnabledChanged: (Boolean) -> Unit,
    onAmpSoloLevelChanged: (Int) -> Unit,
    onReadAmpSoloLevelClicked: () -> Unit,
    diagnostics: SoloDiagnostics,
    onDelayParamChanged: (DelayParamId, Int) -> Unit,
    onReadDelayParamClicked: (DelayParamId) -> Unit,
    onReverbParamChanged: (ReverbParamId, Int) -> Unit,
    onReadReverbParamClicked: (ReverbParamId) -> Unit,
    reverbTime: Double?,
    onReverbTimeChanged: (Double) -> Unit,
    onReadReverbTimeClicked: () -> Unit,
    modChorusPreDelayLow: Double?,
    modChorusPreDelayHigh: Double?,
    onModChorusPreDelayLowChanged: (Double) -> Unit,
    onReadModChorusPreDelayLowClicked: () -> Unit,
    onModChorusPreDelayHighChanged: (Double) -> Unit,
    onReadModChorusPreDelayHighClicked: () -> Unit,
    modInternalRaw: Map<ModFxType, Map<String, Int?>>,
    fxInternalRaw: Map<ModFxType, Map<String, Int?>>,
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
    presetSaveInFlight: Boolean,
    onSavePreset: (String, Int) -> Unit,
    presetSend: PresetSendControls,
    onModParamChanged: (ModFxType, String, Double) -> Unit,
    onReadModParamClicked: (ModFxType, String) -> Unit,
    onFxParamChanged: (ModFxType, String, Double) -> Unit,
    onReadFxParamClicked: (ModFxType, String) -> Unit,
    reloadInFlight: Boolean,
    onRefreshClicked: () -> Unit,
    exportInFlight: Boolean,
    onExportPreset: (String) -> Unit,
    onCopyLog: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // La navegación: un enum y un `when`, sin `NavHost` (CLAUDE.md §4.2, "La navegación").
    // `ShellNavigation` es Kotlin puro y tiene tests: qué pestaña está activa y qué hace el back
    // son decisiones, y las decisiones se prueban.
    val navigation = rememberSaveable(saver = ShellNavigationSaver) { ShellNavigation() }
    val section by navigation.current.collectAsStateWithLifecycle()

    // El back replica lo que haría un `NavHost` con `popUpTo(startDestination)`: desde cualquier
    // pestaña vuelve a la de inicio. En la de inicio se deshabilita, para que ahí el back cierre
    // la app como espera cualquiera. ⚠️ El de la Biblioteca con un preset abierto se registra más
    // adentro y gana a este, que es lo correcto: primero se cierra el preset.
    BackHandler(enabled = section != navigation.start) { navigation.onBack() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(section.titleRes)) },
                actions = {
                    // ⚠️ El estado de la conexión, visible en las cuatro pantallas. Antes solo se
                    // sabía yendo a Logs — o sea que la respuesta a "¿por qué no se mueve nada?"
                    // estaba en otra pantalla.
                    ConnectionBadge(state = state)
                    AdvancedAction(current = section, onSelect = navigation::select)
                },
            )
        },
        // Logs no está aquí: es entrada secundaria de la barra de arriba (CLAUDE.md §4.2).
        bottomBar = { ShellBottomBar(current = section, onSelect = navigation::select) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (section) {
                DebugSection.LOGS -> LogsPane(
                    log = log,
                    state = state,
                    editMode = editMode,
                    onScanClicked = onScanClicked,
                    onHandshakeClicked = onHandshakeClicked,
                    onHandshakeRealVersionClicked = onHandshakeRealVersionClicked,
                    onIdentityRequestClicked = onIdentityRequestClicked,
                    onReadDeviceNameClicked = onReadDeviceNameClicked,
                    onReadPresetNamesClicked = onReadPresetNamesClicked,
                    onEditModeChanged = onEditModeChanged,
                    onReadMemoryDumpClicked = onReadMemoryDumpClicked,
                    onCopyLog = onCopyLog,
                )

                // La pantalla de dominio del amplificador. Recibe **solo** lo suyo: si
                // algún día hace falta pasarle algo de un efecto, es que el reparto de
                // `AmpDomain` está mal, no que falte un parámetro aquí.
                DebugSection.AMP -> AmpScreen(
                    levels = levels,
                    selectors = selectors,
                    variationApplies = variationApplies,
                    ampSoloLevel = ampSoloLevel,
                    state = state,
                    editMode = editMode,
                    onEditModeChanged = onEditModeChanged,
                    onLevelChanged = onLevelChanged,
                    onReadLevelClicked = onReadLevelClicked,
                    onSelectorChanged = onSelectorChanged,
                    onAmpVariationChanged = onAmpVariationChanged,
                    onAmpSoloLevelChanged = onAmpSoloLevelChanged,
                    onReadAmpSoloLevelClicked = onReadAmpSoloLevelClicked,
                    diagnostics = diagnostics,
                    noPanelParams = noPanelParams,
                    contourSlotValues = contourSlotValues,
                    eq1Raw = eq1Raw,
                    eq2Raw = eq2Raw,
                    chainSlotValues = chainSlotValues,
                    onNoPanelParamChanged = onNoPanelParamChanged,
                    onReadNoPanelParamClicked = onReadNoPanelParamClicked,
                    onContourShapeChanged = onContourShapeChanged,
                    onContourFreqShiftChanged = onContourFreqShiftChanged,
                    onReadContourSlotClicked = onReadContourSlotClicked,
                    onEqParamChanged = onEqParamChanged,
                    onReadEqParamClicked = onReadEqParamClicked,
                    reloadInFlight = reloadInFlight,
                    onRefreshClicked = onRefreshClicked,
                    onScanClicked = onScanClicked,
                )

                // La pantalla de dominio de los efectos. Recibe **solo** lo suyo, mismo
                // criterio que la de amplificador: si algún día hace falta pasarle un
                // control del amp, es que `AmpDomain` está mal, no que falte aquí.
                DebugSection.EFFECTS -> EffectsScreen(
                    levels = levels,
                    effectColors = effectColors,
                    effectEnabled = effectEnabled,
                    effectTypes = effectTypes,
                    state = state,
                    editMode = editMode,
                    onEditModeChanged = onEditModeChanged,
                    onLevelChanged = onLevelChanged,
                    onReadLevelClicked = onReadLevelClicked,
                    onEffectColorChanged = onEffectColorChanged,
                    onEffectEnabledChanged = onEffectEnabledChanged,
                    onEffectTypeChanged = onEffectTypeChanged,
                    boosterParams = boosterParams,
                    boosterSoloEnabled = boosterSoloEnabled,
                    onBoosterParamChanged = onBoosterParamChanged,
                    onReadBoosterParamClicked = onReadBoosterParamClicked,
                    onBoosterSoloEnabledChanged = onBoosterSoloEnabledChanged,
                    delayParams = delayParams,
                    reverbParams = reverbParams,
                    selectors = selectors,
                    onSelectorChanged = onSelectorChanged,
                    onDelayParamChanged = onDelayParamChanged,
                    onReadDelayParamClicked = onReadDelayParamClicked,
                    onReverbParamChanged = onReverbParamChanged,
                    onReadReverbParamClicked = onReadReverbParamClicked,
                    reverbTime = reverbTime,
                    onReverbTimeChanged = onReverbTimeChanged,
                    onReadReverbTimeClicked = onReadReverbTimeClicked,
                    modChorusPreDelayLow = modChorusPreDelayLow,
                    modChorusPreDelayHigh = modChorusPreDelayHigh,
                    onModChorusPreDelayLowChanged = onModChorusPreDelayLowChanged,
                    onReadModChorusPreDelayLowClicked = onReadModChorusPreDelayLowClicked,
                    onModChorusPreDelayHighChanged = onModChorusPreDelayHighChanged,
                    onReadModChorusPreDelayHighClicked = onReadModChorusPreDelayHighClicked,
                    modInternalRaw = modInternalRaw,
                    fxInternalRaw = fxInternalRaw,
                    onModParamChanged = onModParamChanged,
                    onReadModParamClicked = onReadModParamClicked,
                    onFxParamChanged = onFxParamChanged,
                    onReadFxParamClicked = onReadFxParamClicked,
                    reloadInFlight = reloadInFlight,
                    onRefreshClicked = onRefreshClicked,
                    onScanClicked = onScanClicked,
                )

                // **Presets**: la mitad de arriba escribe en el amplificador (guardar en
                // canal, exportar) y la de abajo es la Biblioteca de ficheros, que funciona
                // con el cable desenchufado. La pantalla las separa a propósito — ver
                // CLAUDE.md §4.2, "Cómo se distingue en vivo de Biblioteca".
                DebugSection.PRESETS -> PresetsScreen(
                    state = state,
                    editMode = editMode,
                    onEditModeChanged = onEditModeChanged,
                    currentChannel = selectors[SelectorId.ACTIVE_CHANNEL],
                    presetSaveInFlight = presetSaveInFlight,
                    onSavePreset = onSavePreset,
                    exportInFlight = exportInFlight,
                    onExportPreset = onExportPreset,
                    onScanClicked = onScanClicked,
                    sendToAmp = presetSend,
                )
            }
        }
    }
}

/**
 * Guarda la pestaña activa a través de un cambio de configuración (girar la pantalla).
 *
 * Antes bastaba un `rememberSaveable` sobre el enum; ahora que la navegación es un objeto con
 * estado hay que decirle a Compose **qué** guardar: el nombre de la sección y nada más, que es
 * todo lo que no se puede reconstruir.
 */
private val ShellNavigationSaver = Saver<ShellNavigation, String>(
    save = { it.current.value.name },
    restore = { name ->
        ShellNavigation().also { nav ->
            DebugSection.entries.firstOrNull { it.name == name }?.let(nav::select)
        }
    },
)

/**
 * Actions plus the live console.
 *
 * The buttons wrap in a [FlowRow] instead of sitting in hand-made rows, so the console keeps
 * whatever height is left over however many buttons there are.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogsPane(
    log: List<UsbLogLine>,
    state: UsbConnectionState,
    editMode: Boolean,
    onScanClicked: () -> Unit,
    onHandshakeClicked: () -> Unit,
    onHandshakeRealVersionClicked: () -> Unit,
    onIdentityRequestClicked: () -> Unit,
    onReadDeviceNameClicked: () -> Unit,
    onReadPresetNamesClicked: () -> Unit,
    onEditModeChanged: (Boolean) -> Unit,
    onReadMemoryDumpClicked: () -> Unit,
    onCopyLog: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = state is UsbConnectionState.Connected

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onScanClicked,
                enabled = state !is UsbConnectionState.Searching,
            ) {
                Text(stringResource(R.string.debug_connection_scan))
            }
            Button(onClick = onHandshakeClicked, enabled = connected) {
                Text(stringResource(R.string.debug_connection_handshake))
            }
            // La última hipótesis viva del handshake mudo: la misma trama con los bytes de
            // versión que el propio amplificador reportó. Ver CLAUDE.md §4.1.
            Button(onClick = onHandshakeRealVersionClicked, enabled = connected) {
                Text(stringResource(R.string.debug_connection_handshake_real_version))
            }
            Button(onClick = onIdentityRequestClicked, enabled = connected) {
                Text(stringResource(R.string.debug_connection_identity_request))
            }
            Button(onClick = onReadDeviceNameClicked, enabled = connected) {
                Text(stringResource(R.string.debug_connection_read_device_name))
            }
            Button(onClick = onReadPresetNamesClicked, enabled = connected) {
                Text(stringResource(R.string.debug_connection_read_preset_names))
            }
            Button(onClick = onReadMemoryDumpClicked, enabled = connected) {
                Text(stringResource(R.string.debug_connection_read_memory_dump))
            }
        }
        EditModeToggle(
            editMode = editMode,
            enabled = connected,
            onEditModeChanged = onEditModeChanged,
        )
        Text(
            text = state.label(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ConsoleLog(
            lines = log,
            onCopyLog = onCopyLog,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}


/** `01` is "on" for every switch here. See `KatanaAddresses.BOOST_ENABLED` for the caveat. */
internal const val SWITCH_ON_VALUE = 0x01

/** `00` is "off" — the other half of [SWITCH_ON_VALUE], spelled out for `AMP_BRIGHT`/`AMP_SOLO`. */
internal const val SWITCH_OFF_VALUE = 0x00

/**
 * The 9 values of the active-channel selector, labelled the way the front panel groups them:
 * Panel, then bank A 1-4, then bank B 1-4. See `KatanaAddresses.ACTIVE_CHANNEL`.
 */
@Composable
internal fun channelOptions(): List<Pair<Int, String>> {
    val panel = 0 to stringResource(R.string.channel_panel)
    val bankA = (1..4).map { number -> number to stringResource(R.string.channel_bank_a, number) }
    val bankB = (5..8).map { number -> number to stringResource(R.string.channel_bank_b, number - 4) }
    return listOf(panel) + bankA + bankB
}

// `gainSwOptions()` se eliminó junto con el control de Gain SW (2026-09-06): probado contra el
// amplificador y sin efecto. La dirección sigue documentada en `KatanaAddresses`.

/**
 * Explains why every parameter below is greyed out while Edit Mode is off.
 *
 * Un control deshabilitado sin explicación se lee como un bug; con una línea de texto se lee
 * como una regla. Solo aparece con el amplificador conectado: sin conexión ya está todo
 * apagado por otra razón y dos avisos a la vez no aclaran nada.
 */
@Composable
internal fun EditModeNotice(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.edit_mode_required),
        modifier = modifier.fillMaxWidth().padding(bottom = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
internal fun LevelRow(
    id: LevelId,
    level: Int?,
    canEdit: Boolean,
    onLevelChanged: (LevelId, Int) -> Unit,
    onReadLevelClicked: (LevelId) -> Unit,
) {
    LevelControl(
        label = stringResource(
            id.labelRes,
            level?.toString() ?: stringResource(R.string.debug_connection_unknown_value),
        ),
        level = level,
        enabled = canEdit,
        onLevelChanged = { value -> onLevelChanged(id, value) },
        onRead = { onReadLevelClicked(id) },
    )
}

/**
 * One effect: its on/off, its colour and its level, in the order the panel presents them.
 *
 * A [Card] because the three controls belong together — the colour and the switch are useless
 * without knowing which effect they act on, and five loose triples would read as one long
 * list of unrelated widgets.
 */
@Composable
internal fun EffectCard(
    effect: EffectId,
    level: Int?,
    color: Int?,
    enabled: Boolean?,
    type: Int?,
    /** Todo lo de esta tarjeta es un parámetro, así que todo cae bajo el contrato de Edit Mode. */
    canEdit: Boolean,
    onLevelChanged: (LevelId, Int) -> Unit,
    onReadLevelClicked: (LevelId) -> Unit,
    onColorChanged: (EffectId, Int) -> Unit,
    onEnabledChanged: (EffectId, Boolean) -> Unit,
    onTypeChanged: (EffectId, Int) -> Unit,
    boosterParams: Map<BoosterParamId, Int?>,
    boosterSoloEnabled: Boolean?,
    onBoosterParamChanged: (BoosterParamId, Int) -> Unit,
    onReadBoosterParamClicked: (BoosterParamId) -> Unit,
    onBoosterSoloEnabledChanged: (Boolean) -> Unit,
    delayParams: Map<DelayParamId, Int?>,
    reverbParams: Map<ReverbParamId, Int?>,
    selectors: Map<SelectorId, Int?>,
    onSelectorChanged: (SelectorId, Int) -> Unit,
    onDelayParamChanged: (DelayParamId, Int) -> Unit,
    onReadDelayParamClicked: (DelayParamId) -> Unit,
    onReverbParamChanged: (ReverbParamId, Int) -> Unit,
    onReadReverbParamClicked: (ReverbParamId) -> Unit,
    reverbTime: Double?,
    onReverbTimeChanged: (Double) -> Unit,
    onReadReverbTimeClicked: () -> Unit,
    modChorusPreDelayLow: Double?,
    modChorusPreDelayHigh: Double?,
    onModChorusPreDelayLowChanged: (Double) -> Unit,
    onReadModChorusPreDelayLowClicked: () -> Unit,
    onModChorusPreDelayHighChanged: (Double) -> Unit,
    onReadModChorusPreDelayHighClicked: () -> Unit,
    modInternalRaw: Map<ModFxType, Map<String, Int?>>,
    fxInternalRaw: Map<ModFxType, Map<String, Int?>>,
    onModParamChanged: (ModFxType, String, Double) -> Unit,
    onReadModParamClicked: (ModFxType, String) -> Unit,
    onFxParamChanged: (ModFxType, String, Double) -> Unit,
    onReadFxParamClicked: (ModFxType, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resuelto una sola vez para las dos ramas de abajo — Mod y FX comparten catálogo de
    // tipos (CLAUDE.md §5.2), así que null aquí significa "el amp todavía no reportó el tipo",
    // no "el efecto no tiene tipo".
    val modFxType = remember(type) { ModFxType.entries.firstOrNull { it.value == type } }
    Card(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(effect.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                )
                Switch(
                    checked = enabled == true,
                    onCheckedChange = { on -> onEnabledChanged(effect, on) },
                    enabled = canEdit,
                )
            }
            ChipSelector(
                label = stringResource(R.string.effect_color),
                options = EffectColor.entries.map { it.value to it.displayLabel() },
                selected = color,
                enabled = canEdit,
                onSelected = { value -> onColorChanged(effect, value) },
            )
            // El tipo va justo debajo del color porque son lo mismo visto de dos maneras: el
            // color elige el slot y el tipo dice qué modelo tiene ese slot dentro.
            DropdownSelector(
                label = stringResource(R.string.effect_type),
                options = effectTypeOptions(effect),
                selected = type,
                enabled = canEdit,
                onSelected = { value -> onTypeChanged(effect, value) },
            )
            LevelRow(effect.level, level, canEdit, onLevelChanged, onReadLevelClicked)
            // Booster, Delay y Reverb tienen sus parámetros internos cableados (CLAUDE.md
            // §5.2, "DSP simple"); Mod y FX ("DSP complejo") los tendrán cuando les toque
            // (BACKLOG.md, bloque 2).
            when (effect) {
                EffectId.BOOST -> BoosterInternalParams(
                    params = boosterParams,
                    soloEnabled = boosterSoloEnabled,
                    canEdit = canEdit,
                    onParamChanged = onBoosterParamChanged,
                    onReadParam = onReadBoosterParamClicked,
                    onSoloEnabledChanged = onBoosterSoloEnabledChanged,
                )

                EffectId.DELAY -> DelayInternalParams(
                    params = delayParams,
                    highCut = selectors[SelectorId.DELAY_HIGH_CUT],
                    canEdit = canEdit,
                    onParamChanged = onDelayParamChanged,
                    onReadParam = onReadDelayParamClicked,
                    onHighCutChanged = { value -> onSelectorChanged(SelectorId.DELAY_HIGH_CUT, value) },
                )

                EffectId.REVERB -> ReverbInternalParams(
                    params = reverbParams,
                    time = reverbTime,
                    lowCut = selectors[SelectorId.REVERB_LOW_CUT],
                    highCut = selectors[SelectorId.REVERB_HIGH_CUT],
                    canEdit = canEdit,
                    onParamChanged = onReverbParamChanged,
                    onReadParam = onReadReverbParamClicked,
                    onTimeChanged = onReverbTimeChanged,
                    onReadTime = onReadReverbTimeClicked,
                    onLowCutChanged = { value -> onSelectorChanged(SelectorId.REVERB_LOW_CUT, value) },
                    onHighCutChanged = { value -> onSelectorChanged(SelectorId.REVERB_HIGH_CUT, value) },
                )

                EffectId.MOD -> {
                    ModInternalParams(
                        activeType = type,
                        preDelayLow = modChorusPreDelayLow,
                        preDelayHigh = modChorusPreDelayHigh,
                        canEdit = canEdit,
                        onPreDelayLowChanged = onModChorusPreDelayLowChanged,
                        onReadPreDelayLow = onReadModChorusPreDelayLowClicked,
                        onPreDelayHighChanged = onModChorusPreDelayHighChanged,
                        onReadPreDelayHigh = onReadModChorusPreDelayHighClicked,
                    )
                    ModFxGenericParams(
                        activeType = type,
                        values = modFxType?.let { modInternalRaw[it] } ?: emptyMap(),
                        canEdit = canEdit,
                        onParamChanged = { label, value ->
                            modFxType?.let { onModParamChanged(it, label, value) }
                        },
                        onReadParam = { label -> modFxType?.let { onReadModParamClicked(it, label) } },
                    )
                }

                EffectId.FX -> ModFxGenericParams(
                    activeType = type,
                    values = modFxType?.let { fxInternalRaw[it] } ?: emptyMap(),
                    canEdit = canEdit,
                    onParamChanged = { label, value ->
                        modFxType?.let { onFxParamChanged(it, label, value) }
                    },
                    onReadParam = { label -> modFxType?.let { onReadFxParamClicked(it, label) } },
                )
            }
        }
    }
}

/**
 * Booster's five internal continuous parameters plus its Solo switch (CLAUDE.md §5.2).
 *
 * ⚠️ Implemented but unconfirmed against the amplifier (2026-09-04) — same visual weight as
 * the rest of the card, since nothing in the UI should hint at that on its own; the warning
 * lives in the docs, not the widget.
 */
@Composable
private fun BoosterInternalParams(
    params: Map<BoosterParamId, Int?>,
    soloEnabled: Boolean?,
    canEdit: Boolean,
    onParamChanged: (BoosterParamId, Int) -> Unit,
    onReadParam: (BoosterParamId) -> Unit,
    onSoloEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        BoosterParamRow(BoosterParamId.DRIVE, params[BoosterParamId.DRIVE], canEdit, onParamChanged, onReadParam)
        BoosterParamRow(BoosterParamId.BOTTOM, params[BoosterParamId.BOTTOM], canEdit, onParamChanged, onReadParam)
        BoosterParamRow(BoosterParamId.TONE, params[BoosterParamId.TONE], canEdit, onParamChanged, onReadParam)
        SwitchRow(
            label = stringResource(R.string.booster_solo),
            checked = soloEnabled == true,
            enabled = canEdit,
            onCheckedChange = onSoloEnabledChanged,
        )
        BoosterParamRow(BoosterParamId.SOLO_LEVEL, params[BoosterParamId.SOLO_LEVEL], canEdit, onParamChanged, onReadParam)
        BoosterParamRow(BoosterParamId.EFFECT_LEVEL, params[BoosterParamId.EFFECT_LEVEL], canEdit, onParamChanged, onReadParam)
        BoosterParamRow(BoosterParamId.DIRECT_MIX, params[BoosterParamId.DIRECT_MIX], canEdit, onParamChanged, onReadParam)
    }
}

@Composable
private fun BoosterParamRow(
    id: BoosterParamId,
    value: Int?,
    canEdit: Boolean,
    onParamChanged: (BoosterParamId, Int) -> Unit,
    onReadParam: (BoosterParamId) -> Unit,
) {
    LevelControl(
        label = stringResource(
            id.labelRes,
            value?.toString() ?: stringResource(R.string.debug_connection_unknown_value),
        ),
        level = value,
        enabled = canEdit,
        onLevelChanged = { v -> onParamChanged(id, v) },
        onRead = { onReadParam(id) },
        valueRange = id.displayRange.first.toFloat()..id.displayRange.last.toFloat(),
    )
}

/**
 * Delay 1's four internal continuous parameters plus its High Cut selector (CLAUDE.md §5.2).
 *
 * ⚠️ Implemented but unconfirmed against the amplifier — same visual weight as the rest of the
 * card, same reasoning as [BoosterInternalParams].
 */
@Composable
private fun DelayInternalParams(
    params: Map<DelayParamId, Int?>,
    highCut: Int?,
    canEdit: Boolean,
    onParamChanged: (DelayParamId, Int) -> Unit,
    onReadParam: (DelayParamId) -> Unit,
    onHighCutChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        DelayParamRow(DelayParamId.TIME, params[DelayParamId.TIME], canEdit, onParamChanged, onReadParam)
        DelayParamRow(DelayParamId.FEEDBACK, params[DelayParamId.FEEDBACK], canEdit, onParamChanged, onReadParam)
        DropdownSelector(
            label = stringResource(R.string.delay_high_cut),
            options = DelayHighCutFrequency.entries.map { it.value to it.displayName },
            selected = highCut,
            enabled = canEdit,
            onSelected = onHighCutChanged,
        )
        DelayParamRow(DelayParamId.EFFECT_LEVEL, params[DelayParamId.EFFECT_LEVEL], canEdit, onParamChanged, onReadParam)
        DelayParamRow(DelayParamId.DIRECT_MIX, params[DelayParamId.DIRECT_MIX], canEdit, onParamChanged, onReadParam)
    }
}

@Composable
private fun DelayParamRow(
    id: DelayParamId,
    value: Int?,
    canEdit: Boolean,
    onParamChanged: (DelayParamId, Int) -> Unit,
    onReadParam: (DelayParamId) -> Unit,
) {
    LevelControl(
        label = stringResource(
            id.labelRes,
            value?.toString() ?: stringResource(R.string.debug_connection_unknown_value),
        ),
        level = value,
        enabled = canEdit,
        onLevelChanged = { v -> onParamChanged(id, v) },
        onRead = { onReadParam(id) },
        valueRange = id.displayRange.first.toFloat()..id.displayRange.last.toFloat(),
    )
}

/**
 * Reverb's three internal continuous parameters plus its Low Cut and High Cut selectors
 * (CLAUDE.md §5.2). Time and Effect Level are not here on purpose — see [ReverbParamId].
 *
 * ⚠️ Implemented but unconfirmed against the amplifier — same reasoning as
 * [BoosterInternalParams].
 */
@Composable
private fun ReverbInternalParams(
    params: Map<ReverbParamId, Int?>,
    time: Double?,
    lowCut: Int?,
    highCut: Int?,
    canEdit: Boolean,
    onParamChanged: (ReverbParamId, Int) -> Unit,
    onReadParam: (ReverbParamId) -> Unit,
    onTimeChanged: (Double) -> Unit,
    onReadTime: () -> Unit,
    onLowCutChanged: (Int) -> Unit,
    onHighCutChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FractionalLevelControl(
            label = stringResource(
                R.string.reverb_time,
                time?.let { "%.1f".format(it) } ?: stringResource(R.string.debug_connection_unknown_value),
            ),
            level = time,
            enabled = canEdit,
            onLevelChanged = onTimeChanged,
            onRead = onReadTime,
            valueRange = 0.1f..10.0f,
        )
        ReverbParamRow(ReverbParamId.PRE_DELAY, params[ReverbParamId.PRE_DELAY], canEdit, onParamChanged, onReadParam)
        DropdownSelector(
            label = stringResource(R.string.reverb_low_cut),
            options = ReverbLowCutFrequency.entries.map { it.value to it.displayName },
            selected = lowCut,
            enabled = canEdit,
            onSelected = onLowCutChanged,
        )
        DropdownSelector(
            label = stringResource(R.string.reverb_high_cut),
            options = ReverbHighCutFrequency.entries.map { it.value to it.displayName },
            selected = highCut,
            enabled = canEdit,
            onSelected = onHighCutChanged,
        )
        ReverbParamRow(ReverbParamId.DENSITY, params[ReverbParamId.DENSITY], canEdit, onParamChanged, onReadParam)
        ReverbParamRow(ReverbParamId.DIRECT_MIX, params[ReverbParamId.DIRECT_MIX], canEdit, onParamChanged, onReadParam)
    }
}

@Composable
private fun ReverbParamRow(
    id: ReverbParamId,
    value: Int?,
    canEdit: Boolean,
    onParamChanged: (ReverbParamId, Int) -> Unit,
    onReadParam: (ReverbParamId) -> Unit,
) {
    LevelControl(
        label = stringResource(
            id.labelRes,
            value?.toString() ?: stringResource(R.string.debug_connection_unknown_value),
        ),
        level = value,
        enabled = canEdit,
        onLevelChanged = { v -> onParamChanged(id, v) },
        onRead = { onReadParam(id) },
        valueRange = id.displayRange.first.toFloat()..id.displayRange.last.toFloat(),
    )
}

/**
 * Mod's **only** internal parameter cabled so far: the Pre Delay of its 2x2 Chorus type
 * (CLAUDE.md §5.2). Mod is "DSP complejo" — each type has its own address block — so unlike
 * Booster/Delay/Reverb these two sliders **only mean what they say while Mod's active type is
 * 2x2 Chorus**: with any other type active, the same two addresses hold that other type's own
 * parameters. Showing the sliders anyway would silently mislabel them, so con otro tipo activo
 * simplemente no se dibujan.
 *
 * ⚠️ **Aquí había un aviso de "Solo disponible con el tipo 2x2 Chorus", quitado el 2026-09-06
 * por obsoleto.** Tenía sentido cuando estos dos sliders eran lo único cableado de Mod: si no
 * era Chorus, no había nada más que enseñar y convenía decirlo. Ahora los 31 tipos están
 * cableados y [ModFxParams], justo debajo, muestra los del tipo que esté activo — así que el
 * aviso decía "no hay nada para ti aquí" **debajo de una tarjeta llena de controles**.
 */
@Composable
private fun ModInternalParams(
    activeType: Int?,
    preDelayLow: Double?,
    preDelayHigh: Double?,
    canEdit: Boolean,
    onPreDelayLowChanged: (Double) -> Unit,
    onReadPreDelayLow: () -> Unit,
    onPreDelayHighChanged: (Double) -> Unit,
    onReadPreDelayHigh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (activeType != ModFxType.CHORUS.value) return
    Column(modifier = modifier.fillMaxWidth()) {
        FractionalLevelControl(
            label = stringResource(
                R.string.mod_chorus_pre_delay_low,
                preDelayLow?.let { "%.1f".format(it) } ?: stringResource(R.string.debug_connection_unknown_value),
            ),
            level = preDelayLow,
            enabled = canEdit,
            onLevelChanged = onPreDelayLowChanged,
            onRead = onReadPreDelayLow,
            valueRange = 0.0f..40.0f,
        )
        FractionalLevelControl(
            label = stringResource(
                R.string.mod_chorus_pre_delay_high,
                preDelayHigh?.let { "%.1f".format(it) } ?: stringResource(R.string.debug_connection_unknown_value),
            ),
            level = preDelayHigh,
            enabled = canEdit,
            onLevelChanged = onPreDelayHighChanged,
            onRead = onReadPreDelayHigh,
            valueRange = 0.0f..40.0f,
        )
    }
}

/**
 * Los parámetros internos de **cualquiera** de los 31 tipos de Mod/FX (CLAUDE.md §5.2,
 * `ModFxInternalParams`), tabla en mano en vez de una sección por tipo: con 31 tipos y ~6
 * parámetros de media, una función por tipo sería la misma forma repetida 31 veces. Solo se
 * muestran los del tipo activo — el resto de direcciones del bloque pertenecen a otro tipo que
 * comparte el mismo espacio (CLAUDE.md §5.2, "DSP complejo").
 *
 * **No incluye el Pre Delay de 2x2 Chorus**: esos dos sliders son [ModInternalParams], arriba,
 * y no están en `ModFxInternalParams.byType` — se cablearon aparte por su paso fraccionario
 * antes de que existiera esta tabla, y siguen así para no duplicar el control.
 */
@Composable
private fun ModFxGenericParams(
    activeType: Int?,
    values: Map<String, Int?>,
    canEdit: Boolean,
    onParamChanged: (String, Double) -> Unit,
    onReadParam: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = ModFxType.entries.firstOrNull { it.value == activeType } ?: return
    val specs = ModFxInternalParams.byType[type].orEmpty()
    // Los dos tipos que son un ecualizador se dibujan como tal, con la misma UI que EQ1/EQ2
    // (rediseño de 2026-09-06). ⚠️ Sus escalas **no** son las de EQ1/EQ2 —el gráfico de Mod/FX
    // es entero sobre ±20 dB, no fraccionario sobre ±12— pero eso lo trae cada `ParamSpec` en
    // su `kind`, así que los widgets no necesitan saberlo.
    when (type) {
        ModFxType.GRAPHIC_EQ -> GraphicEqBars(
            specs = specs,
            values = values,
            canEdit = canEdit,
            onParamChanged = onParamChanged,
            modifier = modifier,
        )

        ModFxType.PARAMETRIC_EQ -> ParametricEqKnobs(
            specs = specs,
            values = values,
            canEdit = canEdit,
            onParamChanged = onParamChanged,
            modifier = modifier,
        )

        else -> TableParams(
            specs = specs,
            values = values,
            canEdit = canEdit,
            onParamChanged = onParamChanged,
            onReadParam = onReadParam,
            modifier = modifier,
        )
    }
}

/**
 * Un ecualizador **gráfico** como barras verticales, al estilo de Boss Tone Studio.
 *
 * Las bandas y sus frecuencias salen de los [ParamSpec] que se le pasan — no hay ninguna lista
 * de frecuencias escrita aquí. Eso importa porque hay **dos** gráficos distintos en el proyecto
 * y no comparten escala: el de EQ1/EQ2 del amplificador va en pasos de 0,5 dB sobre ±12, y el
 * interno de Mod/FX es entero sobre ±20 (CLAUDE.md §5.2). Cada uno trae la suya en su `kind`.
 *
 * Va en un [Row] con scroll horizontal porque once barras no caben en el ancho de un móvil, y
 * estrecharlas hasta que quepan las volvería imposibles de tocar con el dedo.
 */
@Composable
private fun GraphicEqBars(
    specs: List<ParamSpec>,
    values: Map<String, Int?>,
    canEdit: Boolean,
    onParamChanged: (String, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .knobAwareHorizontalScroll()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        specs.forEach { spec ->
            VerticalBarControl(
                label = spec.label,
                value = values[spec.label]?.let(spec.kind::rawToDisplay),
                range = spec.kind.displayBounds,
                enabled = canEdit,
                onValueChanged = { value -> onParamChanged(spec.label, value) },
            )
        }
    }
}

/**
 * Un ecualizador **paramétrico** como perillas, al estilo de Boss Tone Studio.
 *
 * Sirve para los dos paramétricos del proyecto, que tienen **los mismos once parámetros**: el
 * de EQ1/EQ2 del amplificador (`60 00 00 42`–`4C`) y el tipo `PARAMETRIC_EQ` de Mod/FX
 * (`60 00 01 2C`–`36`). Verificado en el mapa ya extraído, no supuesto.
 *
 * ⚠️ **Las frecuencias y la Q son selectores, no valores continuos**, y aun así van en perilla:
 * la perilla mueve el **índice** y el texto de debajo enseña la etiqueta real (`1.60k`, `0.5`).
 * Es lo que hace BTS y lo que hace un paramétrico físico — un desplegable por frecuencia
 * ocuparía media pantalla para algo que se ajusta al oído, girando.
 */
@Composable
private fun ParametricEqKnobs(
    specs: List<ParamSpec>,
    values: Map<String, Int?>,
    canEdit: Boolean,
    onParamChanged: (String, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unknown = stringResource(R.string.debug_connection_unknown_value)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .knobAwareHorizontalScroll()
            .padding(vertical = 4.dp),
    ) {
        specs.forEach { spec ->
            val raw = values[spec.label]
            val kind = spec.kind
            if (kind is ParamKind.Enum) {
                val index = raw?.let { kind.values.indexOf(it) }?.takeIf { it >= 0 }
                KnobControl(
                    label = spec.label,
                    value = index?.toDouble(),
                    valueText = index?.let { kind.labels.getOrNull(it) } ?: unknown,
                    range = 0.0..(kind.values.size - 1).coerceAtLeast(1).toDouble(),
                    // Un paso es **una posición de la lista**: la perilla trabaja con el
                    // índice, no con el valor crudo, que puede tener huecos.
                    step = 1.0,
                    enabled = canEdit,
                    onValueChanged = { picked ->
                        // La perilla da una posición continua; el selector solo acepta uno de
                        // sus valores, así que se redondea al índice más cercano y se manda
                        // **el valor crudo de la tabla**, que no tiene por qué ser el índice.
                        kind.values.getOrNull(picked.roundToInt())?.let { value ->
                            onParamChanged(spec.label, value.toDouble())
                        }
                    },
                )
            } else {
                val display = raw?.let(kind::rawToDisplay)
                KnobControl(
                    label = spec.label,
                    value = display,
                    valueText = display?.let { "%.1f".format(it) } ?: unknown,
                    range = kind.displayBounds,
                    step = kind.displayStep,
                    enabled = canEdit,
                    onValueChanged = { value -> onParamChanged(spec.label, value) },
                )
            }
        }
    }
}

/**
 * Renderiza una lista de [ParamSpec] con los widgets que ya existen, eligiendo cada uno por el
 * [ParamKind] del parámetro.
 *
 * Lo comparten los dos bloques que se describen con tabla: los 31 tipos de Mod/FX
 * ([ModFxGenericParams]) y los dos de EQ ([EqBlock]). La UI sigue sin conocer ninguna
 * dirección: `ParamSpec` le da etiqueta, rango y opciones, y nada más.
 */
@Composable
private fun TableParams(
    specs: List<ParamSpec>,
    values: Map<String, Int?>,
    canEdit: Boolean,
    onParamChanged: (String, Double) -> Unit,
    onReadParam: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        specs.forEach { spec ->
            val raw = values[spec.label]
            when (val kind = spec.kind) {
                is ParamKind.Enum -> {
                    val options = kind.values.zip(kind.labels)
                    if (options.size <= 4) {
                        ChipSelector(
                            label = spec.label,
                            options = options,
                            selected = raw,
                            enabled = canEdit,
                            onSelected = { value -> onParamChanged(spec.label, value.toDouble()) },
                        )
                    } else {
                        DropdownSelector(
                            label = spec.label,
                            options = options,
                            selected = raw,
                            enabled = canEdit,
                            onSelected = { value -> onParamChanged(spec.label, value.toDouble()) },
                        )
                    }
                }

                else -> {
                    val display = raw?.let(kind::rawToDisplay)
                    val bounds = kind.displayBounds
                    LevelControl(
                        label = "${spec.label}: ${
                            display?.let { "%.1f".format(it) }
                                ?: stringResource(R.string.debug_connection_unknown_value)
                        }",
                        level = display?.roundToInt(),
                        enabled = canEdit,
                        onLevelChanged = { value -> onParamChanged(spec.label, value.toDouble()) },
                        onRead = { onReadParam(spec.label) },
                        valueRange = bounds.start.toFloat()..bounds.endInclusive.toFloat(),
                    )
                }
            }
        }
    }
}


/**
 * Todo lo que el amplificador tiene y el panel no: Noise Gate, Contour, la posición de los dos
 * EQ, sus bloques internos, y la cadena de efectos (CLAUDE.md §5).
 *
 * Va en su **propia sección** y no repartido por las tarjetas de efecto porque no pertenece a
 * ningún efecto: son ajustes del amplificador que en el panel no tienen dónde vivir, y meterlos
 * en la tarjeta de, digamos, Reverb sugeriría una relación que no existe.
 *
 * ⚠️ Nada de esta sección está confirmado con audio.
 */
@Composable
internal fun NoPanelPane(
    selectors: Map<SelectorId, Int?>,
    params: Map<NoPanelParamId, Int?>,
    contourSlots: List<DebugConnectionViewModel.ContourSlotValues>,
    eq1Raw: Map<String, Int?>,
    eq2Raw: Map<String, Int?>,
    chainSlots: List<Int?>,
    canEdit: Boolean,
    onSelectorChanged: (SelectorId, Int) -> Unit,
    onParamChanged: (NoPanelParamId, Int) -> Unit,
    onReadParam: (NoPanelParamId) -> Unit,
    onContourShapeChanged: (Int, Int) -> Unit,
    onContourFreqShiftChanged: (Int, Int) -> Unit,
    onReadContourSlot: (Int) -> Unit,
    onEqParamChanged: (Boolean, String, Double) -> Unit,
    onReadEqParam: (Boolean, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.section_no_panel))
        Text(
            text = stringResource(R.string.no_panel_notice),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                SwitchRow(
                    label = stringResource(R.string.noise_gate),
                    checked = selectors[SelectorId.NOISE_GATE] == SWITCH_ON_VALUE,
                    enabled = canEdit,
                    onCheckedChange = { on ->
                        onSelectorChanged(
                            SelectorId.NOISE_GATE,
                            if (on) SWITCH_ON_VALUE else SWITCH_OFF_VALUE,
                        )
                    },
                )
                NoPanelParamRow(NoPanelParamId.NOISE_GATE_THRESHOLD, params, canEdit, onParamChanged, onReadParam)
                NoPanelParamRow(NoPanelParamId.NOISE_GATE_RELEASE, params, canEdit, onParamChanged, onReadParam)
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                SwitchRow(
                    label = stringResource(R.string.contour),
                    checked = selectors[SelectorId.CONTOUR] == SWITCH_ON_VALUE,
                    enabled = canEdit,
                    onCheckedChange = { on ->
                        onSelectorChanged(
                            SelectorId.CONTOUR,
                            if (on) SWITCH_ON_VALUE else SWITCH_OFF_VALUE,
                        )
                    },
                )
                ChipSelector(
                    label = stringResource(R.string.contour_select),
                    options = (0 until KatanaAddresses.CONTOUR_SLOT_COUNT).map { slot ->
                        slot to stringResource(R.string.contour_slot, slot + 1)
                    },
                    selected = selectors[SelectorId.CONTOUR_SELECT],
                    enabled = canEdit,
                    onSelected = { value -> onSelectorChanged(SelectorId.CONTOUR_SELECT, value) },
                )
                NoPanelParamRow(NoPanelParamId.CONTOUR_FREQ_SHIFT, params, canEdit, onParamChanged, onReadParam)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = stringResource(R.string.contour_slots_outside_dump),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                contourSlots.forEachIndexed { slot, values ->
                    Text(
                        text = stringResource(R.string.contour_slot, slot + 1),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    ChipSelector(
                        label = stringResource(R.string.contour_slot_shape),
                        options = KatanaAddresses.CONTOUR_SHAPE_VALUES.map { it to "${it + 1}" },
                        selected = values.shape,
                        enabled = canEdit,
                        onSelected = { value -> onContourShapeChanged(slot, value) },
                    )
                    LevelControl(
                        label = stringResource(
                            R.string.contour_slot_freq_shift,
                            values.freqShift?.toString()
                                ?: stringResource(R.string.debug_connection_unknown_value),
                        ),
                        level = values.freqShift,
                        enabled = canEdit,
                        onLevelChanged = { value -> onContourFreqShiftChanged(slot, value) },
                        onRead = { onReadContourSlot(slot) },
                        valueRange = -50f..50f,
                    )
                }
            }
        }

        EqBlock(
            titleRes = R.string.eq1_title,
            isEq2 = false,
            positionId = SelectorId.EQ1_POSITION,
            positionOptions = listOf(
                0x00 to stringResource(R.string.eq1_position_in),
                0x01 to stringResource(R.string.eq1_position_out),
            ),
            selectors = selectors,
            values = eq1Raw,
            canEdit = canEdit,
            onSelectorChanged = onSelectorChanged,
            onParamChanged = onEqParamChanged,
            onReadParam = onReadEqParam,
        )
        EqBlock(
            titleRes = R.string.eq2_title,
            isEq2 = true,
            positionId = SelectorId.EQ2_POSITION,
            positionOptions = listOf(
                0x00 to stringResource(R.string.eq2_position_in),
                0x01 to stringResource(R.string.eq2_position_out),
            ),
            selectors = selectors,
            values = eq2Raw,
            canEdit = canEdit,
            onSelectorChanged = onSelectorChanged,
            onParamChanged = onEqParamChanged,
            onReadParam = onReadEqParam,
        )

        ChainCard(
            selectors = selectors,
            slots = chainSlots,
            canEdit = canEdit,
            onSelectorChanged = onSelectorChanged,
        )
    }
}

@Composable
private fun NoPanelParamRow(
    id: NoPanelParamId,
    params: Map<NoPanelParamId, Int?>,
    canEdit: Boolean,
    onParamChanged: (NoPanelParamId, Int) -> Unit,
    onReadParam: (NoPanelParamId) -> Unit,
) {
    val value = params[id]
    LevelControl(
        label = stringResource(
            id.labelRes,
            value?.toString() ?: stringResource(R.string.debug_connection_unknown_value),
        ),
        level = value,
        enabled = canEdit,
        onLevelChanged = { v -> onParamChanged(id, v) },
        onRead = { onReadParam(id) },
        valueRange = id.displayRange.first.toFloat()..id.displayRange.last.toFloat(),
    )
}

/**
 * Uno de los dos bloques de EQ: su posición en la cadena, su on/off, el selector
 * paramétrico/gráfico y los 22 parámetros de las dos mitades (CLAUDE.md §5, [EqParams]).
 *
 * Las dos mitades se muestran **siempre**, pero la que no está seleccionada lleva un aviso: las
 * 24 direcciones existen a la vez y aceptan escritura, lo que cambia con `Selection` es cuál
 * suena. Ocultar la mitad inactiva escondería que su valor sigue ahí y se puede editar.
 */
@Composable
private fun EqBlock(
    @androidx.annotation.StringRes titleRes: Int,
    isEq2: Boolean,
    positionId: SelectorId,
    positionOptions: List<Pair<Int, String>>,
    selectors: Map<SelectorId, Int?>,
    values: Map<String, Int?>,
    canEdit: Boolean,
    onSelectorChanged: (SelectorId, Int) -> Unit,
    onParamChanged: (Boolean, String, Double) -> Unit,
    onReadParam: (Boolean, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Los 24 specs vienen partidos por donde la fuente los parte: 13 de cabecera + paramétrico
    // (`00 40`–`00 4C`) y 11 del gráfico (`00 4D`–`00 57`).
    // Los 24 specs vienen partidos por donde la fuente los parte. Aquí se parten una vez más,
    // por cómo se enseñan: On/Off y Selection son interruptores normales, los once del
    // paramétrico van en perillas y los once del gráfico en barras (rediseño de 2026-09-06).
    val header = EqParams.SPECS.take(2)
    val parametric = EqParams.SPECS.subList(2, 13)
    val graphic = EqParams.SPECS.drop(13)
    val selection = values["Selection"]?.let(EqSelection::fromValue)

    Card(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
            )
            ChipSelector(
                label = stringResource(R.string.eq_position),
                options = positionOptions,
                selected = selectors[positionId],
                enabled = canEdit,
                onSelected = { value -> onSelectorChanged(positionId, value) },
            )

            // On/Off y Selection siguen siendo interruptores normales: no son parte de
            // ninguna de las dos mitades, las eligen.
            TableParams(
                specs = header,
                values = values,
                canEdit = canEdit,
                onParamChanged = { label, value -> onParamChanged(isEq2, label, value) },
                onReadParam = { label -> onReadParam(isEq2, label) },
            )

            SectionHeader(stringResource(R.string.eq_parametric_header))
            if (selection == EqSelection.GRAPHIC) {
                EqInactiveNotice(EqSelection.GRAPHIC)
            }
            ParametricEqKnobs(
                specs = parametric,
                values = values,
                canEdit = canEdit,
                onParamChanged = { label, value -> onParamChanged(isEq2, label, value) },
            )

            SectionHeader(stringResource(R.string.eq_graphic_header))
            if (selection == EqSelection.PARAMETRIC) {
                EqInactiveNotice(EqSelection.PARAMETRIC)
            }
            GraphicEqBars(
                specs = graphic,
                values = values,
                canEdit = canEdit,
                onParamChanged = { label, value -> onParamChanged(isEq2, label, value) },
            )
        }
    }
}

@Composable
private fun EqInactiveNotice(active: EqSelection, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.eq_inactive_half, active.displayName),
        modifier = modifier.fillMaxWidth().padding(bottom = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * La cadena de efectos: las siete cadenas predefinidas, los dos puntos de inserción, y el array
 * de veinte posiciones (CLAUDE.md §5).
 *
 * Las veinte van con [DropdownSelector] y no con chips: son veinte opciones cada una, y veinte
 * filas de veinte chips no cabrían en ninguna pantalla.
 */
@Composable
private fun ChainCard(
    selectors: Map<SelectorId, Int?>,
    slots: List<Int?>,
    canEdit: Boolean,
    onSelectorChanged: (SelectorId, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.chain_title),
                style = MaterialTheme.typography.titleSmall,
            )
            ChipSelector(
                label = stringResource(R.string.chain_type),
                options = KatanaAddresses.CHAIN_TYPE_VALUES.map { it to "${it + 1}" },
                selected = selectors[SelectorId.CHAIN_TYPE],
                enabled = canEdit,
                onSelected = { value -> onSelectorChanged(SelectorId.CHAIN_TYPE, value) },
            )
            ChipSelector(
                label = stringResource(R.string.chain_loop_position),
                options = listOf(
                    0x00 to stringResource(R.string.chain_loop_post_amp),
                    0x01 to stringResource(R.string.chain_loop_post_reverb),
                ),
                selected = selectors[SelectorId.LOOP_POSITION],
                enabled = canEdit,
                onSelected = { value -> onSelectorChanged(SelectorId.LOOP_POSITION, value) },
            )
            ChipSelector(
                label = stringResource(R.string.chain_pedal_fx_position),
                options = listOf(
                    0x00 to stringResource(R.string.chain_pedal_fx_input),
                    0x01 to stringResource(R.string.chain_pedal_fx_post_amp),
                ),
                selected = selectors[SelectorId.PEDAL_FX_POSITION],
                enabled = canEdit,
                onSelected = { value -> onSelectorChanged(SelectorId.PEDAL_FX_POSITION, value) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ChainDiagram(slots = slots)
        }
    }
}

/**
 * El orden real de la cadena, en texto: `INPUT → BOOSTER → MOD → … → SPEAKER`.
 *
 * ❌ **Antes aquí había veinte desplegables para reordenar la cadena a mano, quitados el
 * 2026-09-06**: el array de permutación (`60 00 06 00`–`06 13`) no se comporta bien contra el
 * amplificador real. Se ofrece solo el selector de las **siete cadenas predefinidas**
 * (`60 00 06 20`), que es un valor y no veinte.
 *
 * ✅ **El orden se sigue leyendo y enseñando**, dibujado **a partir de los valores reales de las
 * veinte direcciones**: el orden cambia con la cadena elegida, así que una lista fija sería
 * falsa en seis de los siete casos.
 *
 * ⚠️ **Pero no se dibujan los veinte identificadores crudos, sino un vocabulario de diez**
 * (2026-09-06). Enseñar `CN_S`, `CH_B` o `USB` en un diagrama de señal no informa de nada: son
 * ruteo interno. El filtro y su justificación viven en [ChainBlock.diagramBlock], no aquí — la
 * UI sigue sin saber qué significa cada identificador.
 *
 * Las ranuras sin leer no se marcan: al filtrar, un hueco es indistinguible de un bloque que no
 * se dibuja, así que fingir precisión con un `?` sería peor. Si **nada** se ha leído todavía, se
 * dice explícitamente en vez de enseñar `INPUT → SPEAKER`, que parecería una cadena vacía real.
 */
@Composable
private fun ChainDiagram(slots: List<Int?>, modifier: Modifier = Modifier) {
    val blocks = ChainBlock.diagramSequence(slots)
    Text(
        text = stringResource(R.string.chain_diagram_title),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = if (slots.all { it == null }) {
            stringResource(R.string.chain_diagram_empty)
        } else {
            (listOf(ChainDiagramBlock.INPUT) + blocks.map { it.displayName } +
                ChainDiagramBlock.SPEAKER).joinToString(" → ")
        },
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * The console. Long-pressing anywhere on it copies the whole log, which beats retyping a
 * few hundred lines of hex by hand.
 */
@Composable
private fun ConsoleLog(
    lines: List<UsbLogLine>,
    onCopyLog: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val formatter = remember {
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    }

    fun format(line: UsbLogLine) =
        "${formatter.format(Instant.ofEpochMilli(line.timestampMillis))}  ${line.text}"

    // Autoscroll: keep the newest line visible as the log grows.
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    Surface(
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = { onCopyLog(lines.joinToString("\n") { line -> format(line) }) },
        ),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        if (lines.isEmpty()) {
            Text(
                text = stringResource(R.string.debug_connection_empty_log),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(lines) { line ->
                    Text(
                        text = format(line),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun UsbConnectionState.label(): String = when (this) {
    UsbConnectionState.Idle -> stringResource(R.string.debug_connection_state_idle)
    UsbConnectionState.Searching -> stringResource(R.string.debug_connection_state_scanning)
    UsbConnectionState.KatanaNotFound -> stringResource(R.string.debug_connection_state_not_found)
    UsbConnectionState.AwaitingPermission -> stringResource(R.string.debug_connection_state_permission)
    is UsbConnectionState.Connected -> stringResource(R.string.debug_connection_state_connected, deviceName)
    is UsbConnectionState.Failed -> stringResource(R.string.debug_connection_state_failed, reason)
}

@Preview(showBackground = true)
@Composable
private fun DebugConnectionScreenPreview() {
    KTNAControlTheme {
        DebugConnectionScreen(
            log = listOf(
                UsbLogLine(0L, "Dispositivos USB detectados: 1"),
                UsbLogLine(0L, "  ✓ Transporte abierto."),
                UsbLogLine(0L, "→ Enviando Identity Request: F0 7E 7F 06 01 F7"),
                UsbLogLine(0L, "← Recibidos 14 bytes:"),
            ),
            state = UsbConnectionState.Connected("KATANA"),
            levels = LevelId.entries.associateWith { 42 },
            selectors = mapOf(
                SelectorId.AMP_CATEGORY to AmpCategory.CRUNCH.value,
                SelectorId.AMP_TYPE to AmpType.MS_1959_I.value,
                SelectorId.AMP_VARIATION to 0,
                SelectorId.ACTIVE_CHANNEL to 1,
                SelectorId.AMP_BRIGHT to 0,
                SelectorId.AMP_GAIN_SW to 1,
                SelectorId.AMP_SOLO to 0,
                SelectorId.DELAY_HIGH_CUT to DelayHighCutFrequency.KHZ_2_00.value,
                SelectorId.REVERB_LOW_CUT to ReverbLowCutFrequency.FLAT.value,
                SelectorId.REVERB_HIGH_CUT to ReverbHighCutFrequency.KHZ_5_00.value,
            ),
            effectColors = EffectId.entries.associateWith { EffectColor.GREEN.value },
            effectEnabled = EffectId.entries.associateWith { true },
            variationApplies = true,
            effectTypes = mapOf(
                EffectId.BOOST to BoostType.TUBE_SCREAMER.value,
                EffectId.MOD to ModFxType.CHORUS.value,
                EffectId.FX to ModFxType.PHASER.value,
                EffectId.DELAY to DelayType.ANALOG.value,
                EffectId.REVERB to ReverbType.PLATE.value,
            ),
            boosterParams = mapOf(
                BoosterParamId.DRIVE to 60,
                BoosterParamId.BOTTOM to 0,
                BoosterParamId.TONE to 10,
                BoosterParamId.SOLO_LEVEL to 50,
                BoosterParamId.EFFECT_LEVEL to 80,
                BoosterParamId.DIRECT_MIX to 20,
            ),
            boosterSoloEnabled = false,
            ampSoloLevel = 30,
            delayParams = mapOf(
                DelayParamId.TIME to 350,
                DelayParamId.FEEDBACK to 40,
                DelayParamId.EFFECT_LEVEL to 90,
                DelayParamId.DIRECT_MIX to 100,
            ),
            reverbParams = mapOf(
                ReverbParamId.PRE_DELAY to 20,
                ReverbParamId.DENSITY to 5,
                ReverbParamId.DIRECT_MIX to 100,
            ),
            reverbTime = 2.5,
            modChorusPreDelayLow = 12.0,
            modChorusPreDelayHigh = 18.5,
            editMode = true,
            snackbarHostState = SnackbarHostState(),
            onScanClicked = {},
            onHandshakeClicked = {},
            onHandshakeRealVersionClicked = {},
            onIdentityRequestClicked = {},
            onReadDeviceNameClicked = {},
            onReadPresetNamesClicked = {},
            onEditModeChanged = {},
            onReadMemoryDumpClicked = {},
            onLevelChanged = { _, _ -> },
            onReadLevelClicked = {},
            onSelectorChanged = { _, _ -> },
            onAmpVariationChanged = {},
            onEffectColorChanged = { _, _ -> },
            onEffectEnabledChanged = { _, _ -> },
            onEffectTypeChanged = { _, _ -> },
            onBoosterParamChanged = { _, _ -> },
            onReadBoosterParamClicked = {},
            onBoosterSoloEnabledChanged = {},
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
            onDelayParamChanged = { _, _ -> },
            onReadDelayParamClicked = {},
            onReverbParamChanged = { _, _ -> },
            onReadReverbParamClicked = {},
            onReverbTimeChanged = {},
            onReadReverbTimeClicked = {},
            onModChorusPreDelayLowChanged = {},
            onReadModChorusPreDelayLowClicked = {},
            onModChorusPreDelayHighChanged = {},
            onReadModChorusPreDelayHighClicked = {},
            modInternalRaw = mapOf(
                ModFxType.CHORUS to mapOf("Low" to 60, "High" to 70),
            ),
            fxInternalRaw = mapOf(
                ModFxType.PHASER to mapOf("Rate" to 50, "Depth" to 60),
            ),
            noPanelParams = NoPanelParamId.entries.associateWith { 50 },
            contourSlotValues = List(3) { DebugConnectionViewModel.ContourSlotValues(0, 0) },
            eq1Raw = mapOf("On/Off" to 1, "Selection" to 0, "Low Gain" to 20),
            eq2Raw = mapOf("On/Off" to 0, "Selection" to 1, "31Hz" to 24),
            chainSlotValues = List(20) { it },
            onNoPanelParamChanged = { _, _ -> },
            onReadNoPanelParamClicked = {},
            onContourShapeChanged = { _, _ -> },
            onContourFreqShiftChanged = { _, _ -> },
            onReadContourSlotClicked = {},
            onEqParamChanged = { _, _, _ -> },
            onReadEqParamClicked = { _, _ -> },
            presetSaveInFlight = false,
            onSavePreset = { _, _ -> },
            // La preview no habla con ningún amplificador: `canSend = false` deja el botón de
            // enviar apagado y con su explicación, que es justo lo que se ve sin cable.
            presetSend = PresetSendControls(
                state = PresetSendState.Idle,
                canSend = false,
                onRequest = { _, _ -> },
                onContinue = {},
                onBack = {},
                onCancel = {},
                onConfirmed = {},
                onResultShown = {},
            ),
            onModParamChanged = { _, _, _ -> },
            onReadModParamClicked = { _, _ -> },
            onFxParamChanged = { _, _, _ -> },
            onReadFxParamClicked = { _, _ -> },
            reloadInFlight = false,
            onRefreshClicked = {},
            exportInFlight = false,
            onExportPreset = {},
            onCopyLog = {},
        )
    }
}

/** Label the system shows for the copied log. */
private const val CLIP_LABEL = "KTNA Control log"

// --- Bloque de diagnóstico temporal (CLAUDE.md §5) -------------------------------------------
//
// Tres controles no tuvieron ningún efecto al probarlos con el amplificador: el Solo del
// amplificador (switch + level), Bright y Gain SW. Esto instrumenta las dos preguntas que hay
// que contestar antes de tocar nada:
//
//  1. **Solo**: de las dos direcciones candidatas, ¿responde la otra? La candidata 1 (PREAMP,
//     `60 00 00 2B`/`2C`) sigue cableada como control normal más arriba y no se toca; aquí va
//     la candidata 2 (panel, `60 00 06 14`/`15`), aparte.
// ✅ **La segunda pregunta ya está contestada (2026-09-06) y su instrumentación se quitó**:
// Bright (`60 00 00 29`) y Gain SW (`60 00 00 2A`) no mueven ni el sonido ni el estado interno,
// así que salieron también de la UI de producto. Ver BACKLOG.md.
//
// Es material de investigación, no de producto: se quita cuando la pregunta que queda esté
// contestada con audio.

/**
 * Estado y acciones del bloque de diagnóstico, agrupados en un objeto.
 *
 * Juntos porque [AmpScreen] ya recibe treinta y tantos parámetros sueltos, y estos nueve
 * llegan juntos y se van juntos el día que se quite el bloque.
 */
data class SoloDiagnostics(
    val panelEnabled: Int?,
    val panelLevel: Int?,
    val onPanelEnabledChanged: (Boolean) -> Unit,
    val onPanelLevelChanged: (Int) -> Unit,
    val onReadPanelClicked: () -> Unit,
    val onProbeSoloPreamp: (Boolean) -> Unit,
    val onProbeSoloPanel: (Boolean) -> Unit,
)

@Composable
internal fun DiagnosticsCard(
    diagnostics: SoloDiagnostics,
    canEdit: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            SectionHeader(stringResource(R.string.diag_section))

            Text(
                text = stringResource(R.string.diag_solo_caption),
                style = MaterialTheme.typography.bodySmall,
            )
            SwitchRow(
                label = stringResource(R.string.diag_solo_panel),
                checked = diagnostics.panelEnabled == SWITCH_ON_VALUE,
                enabled = canEdit,
                onCheckedChange = diagnostics.onPanelEnabledChanged,
            )
            LevelControl(
                label = stringResource(
                    R.string.diag_solo_panel_level,
                    diagnostics.panelLevel?.toString()
                        ?: stringResource(R.string.debug_connection_unknown_value),
                ),
                level = diagnostics.panelLevel,
                enabled = canEdit,
                onLevelChanged = diagnostics.onPanelLevelChanged,
                onRead = diagnostics.onReadPanelClicked,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.diag_probe_caption),
                style = MaterialTheme.typography.bodySmall,
            )
            // Los dos valores de cada switch, porque una sola prueba no distingue nada si el
            // amplificador ya estaba en ese valor — ver `WriteProbe.verdict`.
            ProbeRow(
                firstLabel = stringResource(R.string.diag_probe_solo1_on),
                onFirst = { diagnostics.onProbeSoloPreamp(true) },
                secondLabel = stringResource(R.string.diag_probe_solo1_off),
                onSecond = { diagnostics.onProbeSoloPreamp(false) },
                enabled = canEdit,
            )
            ProbeRow(
                firstLabel = stringResource(R.string.diag_probe_solo2_on),
                onFirst = { diagnostics.onProbeSoloPanel(true) },
                secondLabel = stringResource(R.string.diag_probe_solo2_off),
                onSecond = { diagnostics.onProbeSoloPanel(false) },
                enabled = canEdit,
            )
        }
    }
}

/** Dos botones de prueba en una fila: el mismo parámetro con sus dos valores. */
@Composable
private fun ProbeRow(
    firstLabel: String,
    onFirst: () -> Unit,
    secondLabel: String,
    onSecond: () -> Unit,
    enabled: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        OutlinedButton(
            onClick = onFirst,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        ) { Text(firstLabel) }
        OutlinedButton(
            onClick = onSecond,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        ) { Text(secondLabel) }
    }
}

@Composable
private fun EffectColor.displayLabel(): String = when (this) {
    EffectColor.GREEN -> stringResource(R.string.color_green)
    EffectColor.RED -> stringResource(R.string.color_red)
    EffectColor.YELLOW -> stringResource(R.string.color_yellow)
}

/**
 * El catálogo de tipos de un efecto.
 *
 * Mod y FX comparten tabla porque **son la misma lista** en las dos fuentes de Mk2, no por
 * ahorrar código: ver [ModFxType].
 */
private fun effectTypeOptions(effect: EffectId): List<Pair<Int, String>> = when (effect) {
    EffectId.BOOST -> BoostType.entries.map { it.value to it.displayName }
    EffectId.MOD, EffectId.FX -> ModFxType.entries.map { it.value to it.displayName }
    EffectId.DELAY -> DelayType.entries.map { it.value to it.displayName }
    EffectId.REVERB -> ReverbType.entries.map { it.value to it.displayName }
}

/**
 * Edit mode, shown in **both** sections because it means different things in each: in Logs it
 * is what makes the amp report anything at all, and in Sliders it is what keeps them in sync
 * with the front-panel knobs.
 *
 * It changes the state of the amplifier, so it stays an explicit, visible control with an
 * obvious way back off — CLAUDE.md §4.2. The two switches are one piece of state, so flipping
 * either moves both.
 */
@Composable
internal fun EditModeToggle(
    editMode: Boolean,
    enabled: Boolean,
    onEditModeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.debug_connection_edit_mode),
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(checked = editMode, onCheckedChange = onEditModeChanged, enabled = enabled)
    }
}
