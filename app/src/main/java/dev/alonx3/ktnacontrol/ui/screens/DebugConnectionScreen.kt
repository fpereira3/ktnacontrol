package dev.alonx3.ktnacontrol.ui.screens

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.debug_connection_log_copied)

    DebugConnectionScreen(
        log = log,
        state = state,
        levels = levels,
        editMode = editMode,
        snackbarHostState = snackbarHostState,
        onScanClicked = viewModel::onScanClicked,
        onHandshakeClicked = viewModel::onHandshakeClicked,
        onIdentityRequestClicked = viewModel::onIdentityRequestClicked,
        onReadDeviceNameClicked = viewModel::onReadDeviceNameClicked,
        onReadPresetNamesClicked = viewModel::onReadPresetNamesClicked,
        onEditModeChanged = viewModel::onEditModeChanged,
        onReadMemoryDumpClicked = viewModel::onReadMemoryDumpClicked,
        onLevelChanged = viewModel::onLevelChanged,
        onReadLevelClicked = viewModel::onReadLevelClicked,
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
    editMode: Boolean,
    snackbarHostState: SnackbarHostState,
    onScanClicked: () -> Unit,
    onHandshakeClicked: () -> Unit,
    onIdentityRequestClicked: () -> Unit,
    onReadDeviceNameClicked: () -> Unit,
    onReadPresetNamesClicked: () -> Unit,
    onEditModeChanged: (Boolean) -> Unit,
    onReadMemoryDumpClicked: () -> Unit,
    onLevelChanged: (LevelId, Int) -> Unit,
    onReadLevelClicked: (LevelId) -> Unit,
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
                        onIdentityRequestClicked = onIdentityRequestClicked,
                        onReadDeviceNameClicked = onReadDeviceNameClicked,
                        onReadPresetNamesClicked = onReadPresetNamesClicked,
                        onEditModeChanged = onEditModeChanged,
                        onReadMemoryDumpClicked = onReadMemoryDumpClicked,
                        onCopyLog = onCopyLog,
                    )

                    DebugSection.SLIDERS -> SlidersPane(
                        levels = levels,
                        state = state,
                        editMode = editMode,
                        onEditModeChanged = onEditModeChanged,
                        onLevelChanged = onLevelChanged,
                        onReadLevelClicked = onReadLevelClicked,
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
 * Every continuous level, on its own screen so none of them competes with the console for
 * height. Scrolls, because six sliders do not fit on a short phone.
 */
@Composable
private fun SlidersPane(
    levels: Map<LevelId, Int?>,
    state: UsbConnectionState,
    editMode: Boolean,
    onEditModeChanged: (Boolean) -> Unit,
    onLevelChanged: (LevelId, Int) -> Unit,
    onReadLevelClicked: (LevelId) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            enabled = state is UsbConnectionState.Connected,
            onEditModeChanged = onEditModeChanged,
        )
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
        LevelId.entries.forEach { id ->
            val level = levels[id]
            LevelControl(
                label = stringResource(
                    id.labelRes,
                    level?.toString() ?: stringResource(R.string.debug_connection_unknown_value),
                ),
                level = level,
                enabled = state is UsbConnectionState.Connected,
                onLevelChanged = { value -> onLevelChanged(id, value) },
                onRead = { onReadLevelClicked(id) },
            )
        }
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
            valueRange = 0f..100f,
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
            levels = mapOf(
                LevelId.REVERB to 42,
                LevelId.PRESENCE to 60,
                LevelId.BOOST to 30,
                LevelId.MOD to 15,
                LevelId.FX to 0,
                LevelId.DELAY to 55,
            ),
            editMode = true,
            snackbarHostState = SnackbarHostState(),
            onScanClicked = {},
            onHandshakeClicked = {},
            onIdentityRequestClicked = {},
            onReadDeviceNameClicked = {},
            onReadPresetNamesClicked = {},
            onEditModeChanged = {},
            onReadMemoryDumpClicked = {},
            onLevelChanged = { _, _ -> },
            onReadLevelClicked = {},
            onCopyLog = {},
        )
    }
}

/** Label the system shows for the copied log. */
private const val CLIP_LABEL = "KTNA Control log"
