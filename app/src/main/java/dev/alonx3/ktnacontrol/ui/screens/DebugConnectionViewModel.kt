package dev.alonx3.ktnacontrol.ui.screens

import android.app.Application
import android.hardware.usb.UsbDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.alonx3.ktnacontrol.usb.KatanaUsbScanner
import dev.alonx3.ktnacontrol.usb.KatanaUsbTransport
import dev.alonx3.ktnacontrol.usb.UsbConnectionState
import dev.alonx3.ktnacontrol.usb.UsbLogLine
import dev.alonx3.ktnacontrol.usb.UsbOpenResult
import dev.alonx3.ktnacontrol.usb.describe
import dev.alonx3.ktnacontrol.usb.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the diagnostics screen. Holds the USB transport across configuration changes so
 * rotating the screen does not re-claim the interface.
 */
class DebugConnectionViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = KatanaUsbScanner(application)
    private var transport: KatanaUsbTransport? = null

    private val _log = MutableStateFlow<List<UsbLogLine>>(emptyList())
    val log: StateFlow<List<UsbLogLine>> = _log.asStateFlow()

    private val _state = MutableStateFlow<UsbConnectionState>(UsbConnectionState.Idle)
    val state: StateFlow<UsbConnectionState> = _state.asStateFlow()

    init {
        if (scanner.isSupported) {
            scanner.start()
            appendLog("Listo. Pulsa «Buscar dispositivo».")
        } else {
            appendLog("Este dispositivo no expone USB host (UsbManager no disponible).")
            _state.value = UsbConnectionState.Failed("USB host no disponible")
        }
        viewModelScope.launch {
            scanner.permissionResults.collect { result -> onPermissionResult(result) }
        }
    }

    fun onScanClicked() {
        viewModelScope.launch { scan() }
    }

    /**
     * Sends a universal MIDI Identity Request and dumps whatever comes back, verbatim.
     *
     * Purely observational: no parsing yet. The point is to see whether the amp answers with
     * plain MIDI bytes or with packed 4-byte USB-MIDI events (CLAUDE.md §4.1).
     */
    fun onIdentityRequestClicked() {
        val activeTransport = transport
        if (activeTransport == null) {
            appendLog("No hay transporte abierto; busca el dispositivo primero.")
            return
        }
        viewModelScope.launch {
            appendLog("→ Enviando Identity Request: ${IDENTITY_REQUEST.toHexString()}")
            val received = withContext(Dispatchers.IO) {
                val written = activeTransport.sendRaw(IDENTITY_REQUEST)
                if (written < 0) null else written to activeTransport.receiveRaw()
            }
            if (received == null) {
                appendLog("  ✗ bulkTransfer de escritura devolvió -1.")
                return@launch
            }
            val (written, answer) = received
            appendLog("  ✓ Escritos $written bytes.")
            if (answer.isEmpty()) {
                appendLog("  · Sin respuesta (timeout). Puede ser normal: reintenta.")
            } else {
                appendLog("← Recibidos ${answer.size} bytes:")
                appendLog("  ${answer.toHexString()}")
            }
        }
    }

    private suspend fun scan() {
        if (!scanner.isSupported) return
        _state.value = UsbConnectionState.Searching
        closeTransport()

        val devices = withContext(Dispatchers.IO) { scanner.attachedDevices() }
        if (devices.isEmpty()) {
            appendLog("No hay dispositivos USB conectados.")
        } else {
            appendLog("Dispositivos USB detectados: ${devices.size}")
            devices.forEach { device -> appendLog("  • ${device.describe().toLogText()}") }
        }

        val katana = devices.firstOrNull { device -> device.describe().isKatana }
        if (katana == null) {
            appendLog("No se encontró el Katana MK2 (vendorId 1410 / productId 472).")
            _state.value = UsbConnectionState.KatanaNotFound
            return
        }

        appendLog("Katana encontrado: ${katana.describe().toLogText()}")
        if (scanner.hasPermission(katana)) {
            openTransport(katana)
        } else {
            appendLog("Sin permiso todavía; pidiéndolo al sistema...")
            _state.value = UsbConnectionState.AwaitingPermission
            scanner.requestPermission(katana)
        }
    }

    private fun onPermissionResult(result: KatanaUsbScanner.PermissionResult) {
        val device = result.device
        if (!result.granted || device == null) {
            appendLog("  ✗ Permiso denegado.")
            _state.value = UsbConnectionState.Failed("Permiso USB denegado")
            return
        }
        appendLog("  ✓ Permiso concedido.")
        viewModelScope.launch { openTransport(device) }
    }

    private suspend fun openTransport(device: UsbDevice) {
        appendLog("Reclamando interfaz 3 y resolviendo endpoints 0x03 / 0x84...")
        when (val result = withContext(Dispatchers.IO) { scanner.openTransport(device) }) {
            is UsbOpenResult.Success -> {
                transport = result.transport
                appendLog("  ✓ Transporte abierto.")
                _state.value = UsbConnectionState.Connected(
                    device.describe().product ?: device.deviceName
                )
            }

            is UsbOpenResult.Failure -> {
                appendLog("  ✗ ${result.reason}")
                _state.value = UsbConnectionState.Failed(result.reason)
            }
        }
    }

    private fun closeTransport() {
        transport?.close()
        transport = null
    }

    private fun appendLog(text: String) {
        _log.update { lines ->
            (lines + UsbLogLine(System.currentTimeMillis(), text)).takeLast(MAX_LOG_LINES)
        }
    }

    override fun onCleared() {
        closeTransport()
        scanner.close()
    }

    private companion object {
        /** Universal MIDI Identity Request, broadcast device id (`7F`). No Roland prefix. */
        val IDENTITY_REQUEST = byteArrayOf(0xF0.toByte(), 0x7E, 0x7F, 0x06, 0x01, 0xF7.toByte())
        const val MAX_LOG_LINES = 500
    }
}
