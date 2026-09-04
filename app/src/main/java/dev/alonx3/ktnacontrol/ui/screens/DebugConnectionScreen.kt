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
import dev.alonx3.ktnacontrol.protocol.DelayType
import dev.alonx3.ktnacontrol.protocol.ModFxType
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
                        onBoosterParamChanged = onBoosterParamChanged,
                        onReadBoosterParamClicked = onReadBoosterParamClicked,
                        onBoosterSoloEnabledChanged = onBoosterSoloEnabledChanged,
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
    onBoosterParamChanged: (BoosterParamId, Int) -> Unit,
    onReadBoosterParamClicked: (BoosterParamId) -> Unit,
    onBoosterSoloEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = state is UsbConnectionState.Connected

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
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

        // ⚠️ Investigado, sin confirmar contra el amplificador (CLAUDE.md §5.1). 9 valores:
        // Panel + los 8 canales de los dos bancos.
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
            enabled = connected,
            onSelected = { value -> onSelectorChanged(SelectorId.AMP_CATEGORY, value) },
        )
        DropdownSelector(
            label = stringResource(R.string.amp_type),
            options = AmpType.entries.map { it.value to it.displayName },
            selected = selectors[SelectorId.AMP_TYPE],
            enabled = connected,
            onSelected = { value -> onSelectorChanged(SelectorId.AMP_TYPE, value) },
        )
        // El switch **lee** de `06 5C` y **escribe** por el modelo (`00 21`): esa dirección
        // solo reporta. Se apaga cuando el modelo activo no es uno de los cinco canales base,
        // porque entonces "variación" no tiene a qué referirse. Ver el KDoc de AMP_VARIATION.
        SwitchRow(
            label = stringResource(R.string.amp_variation),
            checked = selectors[SelectorId.AMP_VARIATION] == SWITCH_ON_VALUE,
            enabled = connected && variationApplies,
            onCheckedChange = onAmpVariationChanged,
        )

        AMP_LEVELS.forEach { id ->
            LevelRow(id, levels[id], connected, onLevelChanged, onReadLevelClicked)
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
                connected = connected,
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

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun LevelRow(
    id: LevelId,
    level: Int?,
    connected: Boolean,
    onLevelChanged: (LevelId, Int) -> Unit,
    onReadLevelClicked: (LevelId) -> Unit,
) {
    LevelControl(
        label = stringResource(
            id.labelRes,
            level?.toString() ?: stringResource(R.string.debug_connection_unknown_value),
        ),
        level = level,
        enabled = connected,
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
    connected: Boolean,
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
                    enabled = connected,
                )
            }
            ChipSelector(
                label = stringResource(R.string.effect_color),
                options = EffectColor.entries.map { it.value to it.displayLabel() },
                selected = color,
                enabled = connected,
                onSelected = { value -> onColorChanged(effect, value) },
            )
            // El tipo va justo debajo del color porque son lo mismo visto de dos maneras: el
            // color elige el slot y el tipo dice qué modelo tiene ese slot dentro.
            DropdownSelector(
                label = stringResource(R.string.effect_type),
                options = effectTypeOptions(effect),
                selected = type,
                enabled = connected,
                onSelected = { value -> onTypeChanged(effect, value) },
            )
            LevelRow(effect.level, level, connected, onLevelChanged, onReadLevelClicked)
            // Solo Booster tiene sus parámetros internos cableados por ahora (CLAUDE.md
            // §5.2); el resto de efectos los tendrá cuando les toque (BACKLOG.md, bloque 2).
            if (effect == EffectId.BOOST) {
                BoosterInternalParams(
                    params = boosterParams,
                    soloEnabled = boosterSoloEnabled,
                    connected = connected,
                    onParamChanged = onBoosterParamChanged,
                    onReadParam = onReadBoosterParamClicked,
                    onSoloEnabledChanged = onBoosterSoloEnabledChanged,
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
    connected: Boolean,
    onParamChanged: (BoosterParamId, Int) -> Unit,
    onReadParam: (BoosterParamId) -> Unit,
    onSoloEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        BoosterParamRow(BoosterParamId.DRIVE, params[BoosterParamId.DRIVE], connected, onParamChanged, onReadParam)
        BoosterParamRow(BoosterParamId.BOTTOM, params[BoosterParamId.BOTTOM], connected, onParamChanged, onReadParam)
        BoosterParamRow(BoosterParamId.TONE, params[BoosterParamId.TONE], connected, onParamChanged, onReadParam)
        SwitchRow(
            label = stringResource(R.string.booster_solo),
            checked = soloEnabled == true,
            enabled = connected,
            onCheckedChange = onSoloEnabledChanged,
        )
        BoosterParamRow(BoosterParamId.SOLO_LEVEL, params[BoosterParamId.SOLO_LEVEL], connected, onParamChanged, onReadParam)
        BoosterParamRow(BoosterParamId.EFFECT_LEVEL, params[BoosterParamId.EFFECT_LEVEL], connected, onParamChanged, onReadParam)
        BoosterParamRow(BoosterParamId.DIRECT_MIX, params[BoosterParamId.DIRECT_MIX], connected, onParamChanged, onReadParam)
    }
}

@Composable
private fun BoosterParamRow(
    id: BoosterParamId,
    value: Int?,
    connected: Boolean,
    onParamChanged: (BoosterParamId, Int) -> Unit,
    onReadParam: (BoosterParamId) -> Unit,
) {
    LevelControl(
        label = stringResource(
            id.labelRes,
            value?.toString() ?: stringResource(R.string.debug_connection_unknown_value),
        ),
        level = value,
        enabled = connected,
        onLevelChanged = { v -> onParamChanged(id, v) },
        onRead = { onReadParam(id) },
        valueRange = id.displayRange.first.toFloat()..id.displayRange.last.toFloat(),
    )
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
            onCopyLog = {},
        )
    }
}

/** Label the system shows for the copied log. */
private const val CLIP_LABEL = "KTNA Control log"
