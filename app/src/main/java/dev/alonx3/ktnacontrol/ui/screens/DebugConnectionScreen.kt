package dev.alonx3.ktnacontrol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.ClipData
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
import dev.alonx3.ktnacontrol.usb.UsbConnectionState
import dev.alonx3.ktnacontrol.usb.UsbLogLine
import dev.alonx3.ktnacontrol.ui.theme.KTNAControlTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Diagnostics-only screen: a scan button plus a console showing what the USB layer sees.
 * No amp controls here — this exists to confirm on-device that the Katana is detected and
 * its bulk endpoints open, without needing adb.
 */
@Composable
fun DebugConnectionScreen(
    modifier: Modifier = Modifier,
    viewModel: DebugConnectionViewModel = viewModel(),
) {
    val log by viewModel.log.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reverbLevel by viewModel.reverbLevel.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.debug_connection_log_copied)

    Box(modifier = modifier) {
        DebugConnectionScreen(
            log = log,
            state = state,
            onScanClicked = viewModel::onScanClicked,
            onHandshakeClicked = viewModel::onHandshakeClicked,
            onIdentityRequestClicked = viewModel::onIdentityRequestClicked,
            onReadDeviceNameClicked = viewModel::onReadDeviceNameClicked,
            onReadPresetNamesClicked = viewModel::onReadPresetNamesClicked,
            onEditModeOnClicked = viewModel::onEditModeOnClicked,
            onEditModeOffClicked = viewModel::onEditModeOffClicked,
            onReadMemoryDumpClicked = viewModel::onReadMemoryDumpClicked,
            reverbLevel = reverbLevel,
            onReverbLevelChanged = viewModel::onReverbLevelChanged,
            onReadReverbLevelClicked = viewModel::onReadReverbLevelClicked,
            onCopyLog = { text ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, text)))
                    snackbarHostState.showSnackbar(copiedMessage)
                }
            },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun DebugConnectionScreen(
    log: List<UsbLogLine>,
    state: UsbConnectionState,
    onScanClicked: () -> Unit,
    onHandshakeClicked: () -> Unit,
    onIdentityRequestClicked: () -> Unit,
    onReadDeviceNameClicked: () -> Unit,
    onReadPresetNamesClicked: () -> Unit,
    onEditModeOnClicked: () -> Unit,
    onEditModeOffClicked: () -> Unit,
    onReadMemoryDumpClicked: () -> Unit,
    reverbLevel: Int?,
    onReverbLevelChanged: (Int) -> Unit,
    onReadReverbLevelClicked: () -> Unit,
    onCopyLog: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.debug_connection_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Button(
            onClick = onScanClicked,
            enabled = state !is UsbConnectionState.Searching,
        ) {
            Text(stringResource(R.string.debug_connection_scan))
        }
        // Two rows so the longer labels do not overflow on a narrow screen.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onHandshakeClicked,
                enabled = state is UsbConnectionState.Connected,
            ) {
                Text(stringResource(R.string.debug_connection_handshake))
            }
            Button(
                onClick = onIdentityRequestClicked,
                enabled = state is UsbConnectionState.Connected,
            ) {
                Text(stringResource(R.string.debug_connection_identity_request))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onReadDeviceNameClicked,
                enabled = state is UsbConnectionState.Connected,
            ) {
                Text(stringResource(R.string.debug_connection_read_device_name))
            }
            Button(
                onClick = onReadPresetNamesClicked,
                enabled = state is UsbConnectionState.Connected,
            ) {
                Text(stringResource(R.string.debug_connection_read_preset_names))
            }
        }
        // Edit mode changes the state of the amp, so it gets its own explicit pair of
        // buttons — CLAUDE.md §4.2 asks for it to be visible, never silent.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onEditModeOnClicked,
                enabled = state is UsbConnectionState.Connected,
            ) {
                Text(stringResource(R.string.debug_connection_edit_mode_on))
            }
            Button(
                onClick = onEditModeOffClicked,
                enabled = state is UsbConnectionState.Connected,
            ) {
                Text(stringResource(R.string.debug_connection_edit_mode_off))
            }
        }
        Button(
            onClick = onReadMemoryDumpClicked,
            enabled = state is UsbConnectionState.Connected,
        ) {
            Text(stringResource(R.string.debug_connection_read_memory_dump))
        }
        Text(
            text = state.label(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReverbLevelControl(
            level = reverbLevel,
            enabled = state is UsbConnectionState.Connected,
            onLevelChanged = onReverbLevelChanged,
        )
        Button(
            onClick = onReadReverbLevelClicked,
            enabled = state is UsbConnectionState.Connected,
        ) {
            Text(stringResource(R.string.debug_connection_read_reverb_level))
        }
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
 * First real control: reverb level, 0..100 (`60 00 06 18`).
 *
 * Deliberately a bare [Slider] — the point is to validate the read / write / cache path
 * against the amp, not the visual design.
 */
@Composable
private fun ReverbLevelControl(
    level: Int?,
    enabled: Boolean,
    onLevelChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(
                R.string.debug_connection_reverb_level,
                level?.toString() ?: stringResource(R.string.debug_connection_unknown_value),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
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
                UsbLogLine(0L, "  \u2022 KATANA \u2014 fabricante: BOSS, vendorId: 1410 (0x0582), productId: 472 (0x01D8), clase: 255, interfaces: 4"),
                UsbLogLine(0L, "  \u2713 Transporte abierto."),
                UsbLogLine(0L, "\u2192 Enviando Identity Request: F0 7E 7F 06 01 F7"),
                UsbLogLine(0L, "\u2190 Recibidos 14 bytes:"),
            ),
            state = UsbConnectionState.Connected("KATANA"),
            onScanClicked = {},
            onHandshakeClicked = {},
            onIdentityRequestClicked = {},
            onReadDeviceNameClicked = {},
            onReadPresetNamesClicked = {},
            onEditModeOnClicked = {},
            onEditModeOffClicked = {},
            onReadMemoryDumpClicked = {},
            reverbLevel = 42,
            onReverbLevelChanged = {},
            onReadReverbLevelClicked = {},
            onCopyLog = {},
        )
    }
}

/** Label the system shows for the copied log. */
private const val CLIP_LABEL = "KTNA Control log"
