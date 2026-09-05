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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import dev.alonx3.ktnacontrol.protocol.DelayType
import dev.alonx3.ktnacontrol.protocol.ModFxType
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

/**
 * The two halves of the diagnostics screen, reachable from the drawer.
 *
 * They are split because they compete for vertical space: with six sliders and eight buttons
 * on one screen the console was squeezed down to nothing.
 */
enum class DebugSection(@param:androidx.annotation.StringRes val titleRes: Int) {
    LOGS(R.string.debug_connection_section_logs),
    SLIDERS(R.string.debug_connection_section_sliders),
}

/**
 * Diagnostics screen: USB actions plus a live console on one side, the amp's continuous
 * controls on the other.
 */
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
    val effectTypes by viewModel.effectTypes.collectAsStateWithLifecycle()
    val boosterParams by viewModel.boosterParams.collectAsStateWithLifecycle()
    val boosterSoloEnabled by viewModel.boosterSoloEnabled.collectAsStateWithLifecycle()
    val ampSoloLevel by viewModel.ampSoloLevel.collectAsStateWithLifecycle()
    val delayParams by viewModel.delayParams.collectAsStateWithLifecycle()
    val reverbParams by viewModel.reverbParams.collectAsStateWithLifecycle()
    val reverbTime by viewModel.reverbTime.collectAsStateWithLifecycle()
    val modChorusPreDelayLow by viewModel.modChorusPreDelayLow.collectAsStateWithLifecycle()
    val modChorusPreDelayHigh by viewModel.modChorusPreDelayHigh.collectAsStateWithLifecycle()

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
    onCopyLog: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(DebugSection.LOGS) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = stringResource(R.string.app_name),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                HorizontalDivider()
                DebugSection.entries.forEach { entry ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(entry.titleRes)) },
                        selected = entry == section,
                        onClick = {
                            section = entry
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(section.titleRes)) },
                    navigationIcon = {
                        TextButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text(stringResource(R.string.debug_connection_menu))
                        }
                    },
                )
            },
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

                    DebugSection.SLIDERS -> SlidersPane(
                        levels = levels,
                        selectors = selectors,
                        effectColors = effectColors,
                        effectEnabled = effectEnabled,
                        variationApplies = variationApplies,
                        state = state,
                        editMode = editMode,
                        onEditModeChanged = onEditModeChanged,
                        onLevelChanged = onLevelChanged,
                        onReadLevelClicked = onReadLevelClicked,
                        onSelectorChanged = onSelectorChanged,
                        onAmpVariationChanged = onAmpVariationChanged,
                        onEffectColorChanged = onEffectColorChanged,
                        onEffectEnabledChanged = onEffectEnabledChanged,
                        effectTypes = effectTypes,
                        onEffectTypeChanged = onEffectTypeChanged,
                        boosterParams = boosterParams,
                        boosterSoloEnabled = boosterSoloEnabled,
                        ampSoloLevel = ampSoloLevel,
                        delayParams = delayParams,
                        reverbParams = reverbParams,
                        onBoosterParamChanged = onBoosterParamChanged,
                        onReadBoosterParamClicked = onReadBoosterParamClicked,
                        onBoosterSoloEnabledChanged = onBoosterSoloEnabledChanged,
                        onAmpSoloLevelChanged = onAmpSoloLevelChanged,
                        onReadAmpSoloLevelClicked = onReadAmpSoloLevelClicked,
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
                    )
                }
            }
        }
    }
}

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

/**
 * Everything that controls the sound: the amp section, then one card per effect.
 *
 * Split off from the log so neither competes for height, and scrollable because eleven
 * sliders plus five effect cards do not fit on a phone.
 *
 * Everything on this screen is confirmed against the amplifier except one detail: the
 * VARIATION switch reads one address and writes another, because `60 00 06 5C` only reports.
 * See `KatanaAddresses.AMP_VARIATION`.
 */
@Composable
private fun SlidersPane(
    levels: Map<LevelId, Int?>,
    selectors: Map<SelectorId, Int?>,
    effectColors: Map<EffectId, Int?>,
    effectEnabled: Map<EffectId, Boolean?>,
    variationApplies: Boolean,
    state: UsbConnectionState,
    editMode: Boolean,
    onEditModeChanged: (Boolean) -> Unit,
    onLevelChanged: (LevelId, Int) -> Unit,
    onReadLevelClicked: (LevelId) -> Unit,
    onSelectorChanged: (SelectorId, Int) -> Unit,
    onAmpVariationChanged: (Boolean) -> Unit,
    onEffectColorChanged: (EffectId, Int) -> Unit,
    onEffectEnabledChanged: (EffectId, Boolean) -> Unit,
    effectTypes: Map<EffectId, Int?>,
    onEffectTypeChanged: (EffectId, Int) -> Unit,
    boosterParams: Map<BoosterParamId, Int?>,
    boosterSoloEnabled: Boolean?,
    ampSoloLevel: Int?,
    delayParams: Map<DelayParamId, Int?>,
    reverbParams: Map<ReverbParamId, Int?>,
    onBoosterParamChanged: (BoosterParamId, Int) -> Unit,
    onReadBoosterParamClicked: (BoosterParamId) -> Unit,
    onBoosterSoloEnabledChanged: (Boolean) -> Unit,
    onAmpSoloLevelChanged: (Int) -> Unit,
    onReadAmpSoloLevelClicked: () -> Unit,
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
    modifier: Modifier = Modifier,
) {
    val connected = state is UsbConnectionState.Connected

    // El contrato de Edit Mode (CLAUDE.md §4.2): **apagado deja cambiar de canal y nada más**.
    // Sin edit mode el amplificador no manda reportes espontáneos, así que la app no puede
    // confirmar ningún parámetro que escriba — mostrar los controles como si funcionaran sería
    // mentir. El canal es la excepción deliberada: es un comando básico, no un ajuste fino.
    val canEdit = connected && editMode

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Repeated from the Logs pane on purpose: this is where the sliders live, and without
        // edit mode on they stop following the front-panel knobs. Having to switch sections to
        // find out why nothing moves would be its own little bug.
        EditModeToggle(
            editMode = editMode,
            enabled = connected,
            onEditModeChanged = onEditModeChanged,
        )
        if (connected && !editMode) {
            EditModeNotice()
        }
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

        // ✅ Confirmado en las dos direcciones (CLAUDE.md §5.1). 9 valores: Panel + los 8
        // canales de los dos bancos.
        //
        // **Único control que sigue habilitado con Edit Mode apagado**, a propósito: cambiar
        // de canal es la operación que el contrato deja siempre disponible.
        SectionHeader(stringResource(R.string.section_channel))
        ChipSelector(
            label = stringResource(R.string.section_channel),
            options = channelOptions(),
            selected = selectors[SelectorId.ACTIVE_CHANNEL],
            enabled = connected,
            onSelected = { value -> onSelectorChanged(SelectorId.ACTIVE_CHANNEL, value) },
        )

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

        // ⚠️ Resto del bloque PREAMP (`60 00 00 29`-`2C`), sin confirmar todavía — ver
        // BACKLOG.md, "Pendiente por probar".
        SwitchRow(
            label = stringResource(R.string.amp_bright),
            checked = selectors[SelectorId.AMP_BRIGHT] == SWITCH_ON_VALUE,
            enabled = canEdit,
            onCheckedChange = { on ->
                onSelectorChanged(
                    SelectorId.AMP_BRIGHT,
                    if (on) SWITCH_ON_VALUE else SWITCH_OFF_VALUE,
                )
            },
        )
        ChipSelector(
            label = stringResource(R.string.amp_gain_sw),
            options = gainSwOptions(),
            selected = selectors[SelectorId.AMP_GAIN_SW],
            enabled = canEdit,
            onSelected = { value -> onSelectorChanged(SelectorId.AMP_GAIN_SW, value) },
        )
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

        AMP_LEVELS.forEach { id ->
            LevelRow(id, levels[id], canEdit, onLevelChanged, onReadLevelClicked)
        }

        Spacer(modifier = Modifier.height(8.dp))
        SectionHeader(stringResource(R.string.section_effects))

        EffectId.entries.forEach { effect ->
            EffectCard(
                effect = effect,
                level = levels[effect.level],
                color = effectColors[effect],
                enabled = effectEnabled[effect],
                type = effectTypes[effect],
                canEdit = canEdit,
                onLevelChanged = onLevelChanged,
                onReadLevelClicked = onReadLevelClicked,
                onColorChanged = onEffectColorChanged,
                onEnabledChanged = onEffectEnabledChanged,
                onTypeChanged = onEffectTypeChanged,
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
            )
        }
    }
}

/**
 * The amp/EQ knobs, i.e. every level that is **not** an effect.
 *
 * Derived rather than listed so that adding a level to [LevelId] cannot leave it invisible:
 * anything no effect claims shows up in the amp section.
 */
private val AMP_LEVELS: List<LevelId> =
    LevelId.entries - EffectId.entries.map { it.level }.toSet()

/** `01` is "on" for every switch here. See `KatanaAddresses.BOOST_ENABLED` for the caveat. */
private const val SWITCH_ON_VALUE = 0x01

/** `00` is "off" — the other half of [SWITCH_ON_VALUE], spelled out for `AMP_BRIGHT`/`AMP_SOLO`. */
private const val SWITCH_OFF_VALUE = 0x00

/**
 * The 9 values of the active-channel selector, labelled the way the front panel groups them:
 * Panel, then bank A 1-4, then bank B 1-4. See `KatanaAddresses.ACTIVE_CHANNEL`.
 */
@Composable
private fun channelOptions(): List<Pair<Int, String>> {
    val panel = 0 to stringResource(R.string.channel_panel)
    val bankA = (1..4).map { number -> number to stringResource(R.string.channel_bank_a, number) }
    val bankB = (5..8).map { number -> number to stringResource(R.string.channel_bank_b, number - 4) }
    return listOf(panel) + bankA + bankB
}

/** The 3 values of `AMP_GAIN_SW` (`60 00 00 2A`): Low / Middle / High. */
@Composable
private fun gainSwOptions(): List<Pair<Int, String>> = listOf(
    0x00 to stringResource(R.string.amp_gain_sw_low),
    0x01 to stringResource(R.string.amp_gain_sw_middle),
    0x02 to stringResource(R.string.amp_gain_sw_high),
)

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
    )
}

/**
 * Explains why every parameter below is greyed out while Edit Mode is off.
 *
 * Un control deshabilitado sin explicación se lee como un bug; con una línea de texto se lee
 * como una regla. Solo aparece con el amplificador conectado: sin conexión ya está todo
 * apagado por otra razón y dos avisos a la vez no aclaran nada.
 */
@Composable
private fun EditModeNotice(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.edit_mode_required),
        modifier = modifier.fillMaxWidth().padding(bottom = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun LevelRow(
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
private fun EffectCard(
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
    modifier: Modifier = Modifier,
) {
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

                EffectId.MOD -> ModInternalParams(
                    activeType = type,
                    preDelayLow = modChorusPreDelayLow,
                    preDelayHigh = modChorusPreDelayHigh,
                    canEdit = canEdit,
                    onPreDelayLowChanged = onModChorusPreDelayLowChanged,
                    onReadPreDelayLow = onReadModChorusPreDelayLowClicked,
                    onPreDelayHighChanged = onModChorusPreDelayHighChanged,
                    onReadPreDelayHigh = onReadModChorusPreDelayHighClicked,
                )

                EffectId.FX -> Unit
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
 * parameters. Showing the sliders anyway would silently mislabel them, so this hides them and
 * explains why instead — same spirit as [EditModeNotice].
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
    if (activeType != ModFxType.CHORUS.value) {
        Text(
            text = stringResource(R.string.mod_internal_params_requires_chorus),
            modifier = modifier.fillMaxWidth().padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
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

@Composable
private fun EffectColor.displayLabel(): String = when (this) {
    EffectColor.GREEN -> stringResource(R.string.color_green)
    EffectColor.RED -> stringResource(R.string.color_red)
    EffectColor.YELLOW -> stringResource(R.string.color_yellow)
}

/**
 * A short list of options as chips.
 *
 * Nothing is selected until the amp says so: with an unconfirmed address the honest state is
 * "unknown", and pre-selecting the first option would look like a value the amp confirmed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipSelector(
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
private fun DropdownSelector(
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
private fun SwitchRow(
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
 * Edit mode, shown in **both** sections because it means different things in each: in Logs it
 * is what makes the amp report anything at all, and in Sliders it is what keeps them in sync
 * with the front-panel knobs.
 *
 * It changes the state of the amplifier, so it stays an explicit, visible control with an
 * obvious way back off — CLAUDE.md §4.2. The two switches are one piece of state, so flipping
 * either moves both.
 */
@Composable
private fun EditModeToggle(
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
@Composable
private fun LevelControl(
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
            TextButton(onClick = onRead, enabled = enabled) {
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
private fun FractionalLevelControl(
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
            TextButton(onClick = onRead, enabled = enabled) {
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
            onCopyLog = {},
        )
    }
}

/** Label the system shows for the copied log. */
private const val CLIP_LABEL = "KTNA Control log"
