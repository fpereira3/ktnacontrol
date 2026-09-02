package dev.alonx3.ktnacontrol.ui.screens

import android.app.Application
import android.hardware.usb.UsbDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.alonx3.ktnacontrol.device.KatanaLink
import dev.alonx3.ktnacontrol.device.KatanaRepository
import dev.alonx3.ktnacontrol.protocol.Address
import dev.alonx3.ktnacontrol.protocol.KatanaAddresses
import dev.alonx3.ktnacontrol.protocol.RolandMessage
import dev.alonx3.ktnacontrol.protocol.RolandSysEx
import dev.alonx3.ktnacontrol.protocol.awaitRolandReply
import dev.alonx3.ktnacontrol.protocol.sendAndCollect
import dev.alonx3.ktnacontrol.usb.KatanaHandshake
import dev.alonx3.ktnacontrol.usb.KatanaUsbScanner
import dev.alonx3.ktnacontrol.usb.KatanaUsbTransport
import dev.alonx3.ktnacontrol.usb.UsbConnectionState
import dev.alonx3.ktnacontrol.usb.UsbDeviceEvent
import dev.alonx3.ktnacontrol.usb.UsbLogLine
import dev.alonx3.ktnacontrol.usb.UsbOpenResult
import dev.alonx3.ktnacontrol.usb.describe
import dev.alonx3.ktnacontrol.usb.packUsbMidi
import dev.alonx3.ktnacontrol.usb.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the diagnostics screen, which works as a **live monitor**: once the transport is
 * open a read loop runs continuously and every message the amp sends is logged, whether or
 * not the screen asked for it. The buttons only ever send; their answers arrive through the
 * same stream as everything else.
 *
 * Holds the transport across configuration changes so rotating does not re-claim the
 * interface.
 */
class DebugConnectionViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = KatanaUsbScanner(application)
    private var transport: KatanaUsbTransport? = null
    private var readJob: Job? = null

    private val _log = MutableStateFlow<List<UsbLogLine>>(emptyList())
    val log: StateFlow<List<UsbLogLine>> = _log.asStateFlow()

    private val _state = MutableStateFlow<UsbConnectionState>(UsbConnectionState.Idle)
    val state: StateFlow<UsbConnectionState> = _state.asStateFlow()

    /**
     * Every message the read loop produces, fanned out to whoever is interested: the log,
     * and any query waiting for its own answer.
     *
     * There is exactly **one** reader of the endpoint — the [readJob] below. Nothing else
     * may call `incomingMessages()`, or two loops would steal each other's messages.
     */
    private val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    private val incomingMessages: SharedFlow<ByteArray> = incoming.asSharedFlow()

    private var repository: KatanaRepository? = null

    private val _reverbLevel = MutableStateFlow<Int?>(null)

    /** Reverb level as the repository sees it, or null before the first read. */
    val reverbLevel: StateFlow<Int?> = _reverbLevel.asStateFlow()

    private var repositoryMirror: Job? = null

    /** Adapts the USB transport to the narrow port the repository depends on. */
    private inner class TransportLink(
        private val activeTransport: KatanaUsbTransport,
    ) : KatanaLink {
        override val incoming: Flow<ByteArray> = incomingMessages
        override suspend fun send(message: ByteArray): Boolean =
            withContext(Dispatchers.IO) { activeTransport.sendRaw(message) >= 0 }
    }

    init {
        if (scanner.isSupported) {
            scanner.start()
            appendLog("Escuchando conexiones USB. Pulsa «Buscar dispositivo» o conecta el amp.")
        } else {
            appendLog("Este dispositivo no expone USB host (UsbManager no disponible).")
            _state.value = UsbConnectionState.Failed("USB host no disponible")
        }
        viewModelScope.launch {
            scanner.permissionResults.collect { result -> onPermissionResult(result) }
        }
        viewModelScope.launch {
            scanner.deviceEvents.collect { event -> onDeviceEvent(event) }
        }
    }

    fun onScanClicked() {
        viewModelScope.launch { scan() }
    }

    /**
     * Sends the mandatory handshake twice. Any reply shows up in the log on its own, through
     * the read loop.
     */
    fun onHandshakeClicked() {
        val activeTransport = transport ?: return appendLog(NO_TRANSPORT)
        viewModelScope.launch {
            val message = KatanaHandshake.message(HANDSHAKE_MODEL_ID)
            appendLog("→ Handshake (model 0x%02X), x2 con ~4 ms:".format(HANDSHAKE_MODEL_ID))
            appendLog("  sysex: ${message.toHexString()}")

            val written = withContext(Dispatchers.IO) {
                activeTransport.sendHandshake(HANDSHAKE_MODEL_ID)
            }
            written.forEachIndexed { index, bytes ->
                appendLog("  #${index + 1}: ${describeWrite(bytes)}")
            }
        }
    }

    /** Sends a universal MIDI Identity Request. */
    fun onIdentityRequestClicked() = send("Identity Request", IDENTITY_REQUEST)

    /**
     * Reads the whole "effective" block at `60 00 00 00`.
     *
     * **The reply is not a single message.** The amp answers a dump with several Roland
     * messages, each carrying its own address — `reference/TuxKatana/HOW.md` traces 5 of 241
     * data bytes plus a final one of 139. So this cannot use [awaitRolandReply], which
     * matches exactly one message by address and would silently return only the first chunk:
     * it collects everything for a generous window and reports the lot.
     */
    fun onReadMemoryDumpClicked() {
        val activeTransport = transport ?: return appendLog(NO_TRANSPORT)
        viewModelScope.launch {
            val query = RolandSysEx.get(
                address = KatanaAddresses.MEMORY_DUMP,
                size = KatanaAddresses.MEMORY_DUMP_SIZE,
            )
            appendLog(
                "→ GET dump de memoria (${KatanaAddresses.MEMORY_DUMP}, rango " +
                    "${KatanaAddresses.MEMORY_DUMP_SIZE} B):"
            )
            appendLog("  sysex: ${query.toHexString()}")

            var written = -1
            val seen = sendAndCollect(incomingMessages, DUMP_WINDOW_MS) {
                written = withContext(Dispatchers.IO) { activeTransport.sendRaw(query) }
            }
            appendLog("  ${describeWrite(written)}")
            logMemoryDump(seen)
        }
    }

    private fun logMemoryDump(messages: List<ByteArray>) {
        if (messages.isEmpty()) {
            appendLog("  · Sin respuesta al dump (timeout de ${DUMP_WINDOW_MS} ms).")
            return
        }

        val parsed = messages.map { message -> RolandSysEx.parse(message) }
        val chunks = parsed.filterIsInstance<RolandMessage.Data>()
        val invalid = parsed.filterIsInstance<RolandMessage.Invalid>()

        appendLog("← ${messages.size} mensaje(s) recibidos en la ventana:")
        appendLog(
            "  ${chunks.size} con checksum válido, ${invalid.size} inválido(s); " +
                "${messages.sumOf { it.size }} B en total, " +
                "${chunks.sumOf { it.data.size }} B de datos."
        )
        chunks.forEach { chunk ->
            appendLog("     ${chunk.address}: ${chunk.data.size} B")
        }
        invalid.forEach { bad -> appendLog("     ✗ ${bad.reason}") }

        // The first bytes of the block are the name of the preset in use, so they double as
        // a sanity check: they should match what the amp is showing right now.
        val head = chunks.firstOrNull { chunk -> chunk.address == KatanaAddresses.MEMORY_DUMP }
        if (head == null) {
            appendLog("  ! No llegó el trozo inicial (${KatanaAddresses.MEMORY_DUMP}).")
            return
        }
        val nameBytes = head.data.take(KatanaAddresses.PRESET_NAME_IN_DUMP).toByteArray()
        val name = asAscii(nameBytes) ?: nameBytes.toHexString()
        appendLog("  Preset actual (primeros ${nameBytes.size} B): \"$name\"")
    }

    /** Turns edit mode on, so the amp starts reporting changes it makes on its own. */
    fun onEditModeOnClicked() = setEditMode(enabled = true)

    /** Turns edit mode off, leaving the amp as it was before the test. */
    fun onEditModeOffClicked() = setEditMode(enabled = false)

    /**
     * Writes edit mode and then watches the stream for a moment.
     *
     * A Roland write is not acknowledged (see `sendAndCollect`), so silence here is the
     * expected outcome, not a failure — the interesting part happens afterwards: with edit
     * mode on, moving a front-panel knob should start producing spontaneous messages in the
     * log, which is the hypothesis being tested.
     */
    private fun setEditMode(enabled: Boolean) {
        val activeTransport = transport ?: return appendLog(NO_TRANSPORT)
        val label = if (enabled) "ON" else "OFF"
        val value = if (enabled) KatanaAddresses.EDIT_MODE_ON else KatanaAddresses.EDIT_MODE_OFF
        val message = RolandSysEx.set(KatanaAddresses.EDIT_MODE, byteArrayOf(value))

        viewModelScope.launch {
            appendLog("→ SET Edit Mode $label (${KatanaAddresses.EDIT_MODE}):")
            appendLog("  sysex: ${message.toHexString()}")

            var written = -1
            val seen = sendAndCollect(incomingMessages) {
                written = withContext(Dispatchers.IO) { activeTransport.sendRaw(message) }
            }
            appendLog("  ${describeWrite(written)}")

            if (seen.isEmpty()) {
                appendLog("  · Sin respuesta al poner Edit Mode $label (un SET no se confirma).")
            } else {
                appendLog("  ← ${seen.size} mensaje(s) durante la ventana de espera:")
                seen.forEach { message -> appendLog("     ${message.toHexString()}") }
            }
            if (enabled) {
                appendLog("  Ahora mueve una perilla del amplificador y mira si aparece algo.")
            }
        }
    }

    /** Reads the 16-byte device name at `10 00 00 00`, waiting for its own answer. */
    fun onReadDeviceNameClicked() {
        if (transport == null) return appendLog(NO_TRANSPORT)
        viewModelScope.launch {
            appendLog("→ GET nombre del dispositivo:")
            val reply = query(KatanaAddresses.DEVICE_NAME, KatanaAddresses.DEVICE_NAME_SIZE)
            appendLog("  ${KatanaAddresses.DEVICE_NAME}: ${describeReply(reply)}")
        }
    }

    /**
     * Queries the names of the 8 user presets **one at a time**, each waiting for its own
     * reply before the next goes out.
     *
     * Firing all eight and reading whatever came back would be a race: the answers arrive on
     * a shared asynchronous stream and nothing guarantees they keep the order of the
     * requests.
     */
    fun onReadPresetNamesClicked() {
        if (transport == null) return appendLog(NO_TRANSPORT)
        viewModelScope.launch {
            appendLog("→ GET nombres de los ${KatanaAddresses.PRESET_COUNT} presets:")
            for (number in 1..KatanaAddresses.PRESET_COUNT) {
                val address = KatanaAddresses.presetName(number)
                val reply = query(address, KatanaAddresses.PRESET_NAME_SIZE)
                appendLog("  #$number ($address): ${describeReply(reply)}")
            }
        }
    }

    /**
     * Sends a GET for [address] and waits for the reply carrying that same address.
     *
     * A timeout is logged by the caller and does not abort the sequence.
     */
    private suspend fun query(address: Address, size: Int): RolandMessage.Data? {
        val activeTransport = transport ?: return null
        val message = RolandSysEx.get(address, size)
        return awaitRolandReply(incomingMessages, address) {
            withContext(Dispatchers.IO) { activeTransport.sendRaw(message) }
        }
    }

    private fun describeReply(reply: RolandMessage.Data?): String = when {
        reply == null -> "· sin respuesta (timeout)."
        else -> {
            val text = asAscii(reply.data)
            if (text != null) "✓ \"$text\"" else "✓ ${reply.data.size} B: ${reply.data.toHexString()}"
        }
    }

    /** Sends [message], logging what went out. The answer, if any, arrives on the read loop. */
    private fun send(label: String, message: ByteArray) {
        val activeTransport = transport ?: return appendLog(NO_TRANSPORT)
        viewModelScope.launch {
            appendLog("→ $label:")
            appendLog("  sysex: ${message.toHexString()}")
            appendLog("  wire:  ${packUsbMidi(message).toHexString()}")
            val written = withContext(Dispatchers.IO) { activeTransport.sendRaw(message) }
            appendLog("  ${describeWrite(written)}")
        }
    }

    private fun describeWrite(written: Int): String =
        if (written < 0) "✗ bulkTransfer devolvió -1." else "✓ escritos $written bytes en el cable."

    // --- Connection lifecycle -------------------------------------------------------------

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
        connect(katana)
    }

    private suspend fun connect(device: UsbDevice) {
        if (scanner.hasPermission(device)) {
            openTransport(device)
        } else {
            appendLog("Sin permiso todavía; pidiéndolo al sistema...")
            _state.value = UsbConnectionState.AwaitingPermission
            scanner.requestPermission(device)
        }
    }

    private fun onDeviceEvent(event: UsbDeviceEvent) {
        when (event) {
            is UsbDeviceEvent.Attached -> {
                val description = event.device.describe()
                appendLog("+ USB conectado: ${description.toLogText()}")
                if (description.isKatana) {
                    viewModelScope.launch { connect(event.device) }
                }
            }

            is UsbDeviceEvent.Detached -> {
                val description = event.device.describe()
                appendLog("- USB desconectado: ${description.toLogText()}")
                if (description.isKatana) {
                    closeTransport()
                    _state.value = UsbConnectionState.Idle
                }
            }
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
                appendLog("  ✓ Transporte abierto. Escuchando en continuo.")
                _state.value = UsbConnectionState.Connected(
                    device.describe().product ?: device.deviceName
                )
                startReading(result.transport)
                startRepository(result.transport)
            }

            is UsbOpenResult.Failure -> {
                appendLog("  ✗ ${result.reason}")
                _state.value = UsbConnectionState.Failed(result.reason)
            }
        }
    }

    /**
     * Subscribes to the read loop. Every complete message the amp sends lands in the log,
     * including anything it volunteers on its own — a front-panel knob, say.
     */
    private fun startReading(activeTransport: KatanaUsbTransport) {
        readJob?.cancel()
        readJob = viewModelScope.launch {
            activeTransport.incomingMessages().collect { message ->
                incoming.emit(message)
                logIncoming(message)
            }
        }
    }

    private fun logIncoming(message: ByteArray) {
        appendLog("← ${message.size} B: ${message.toHexString()}")
        appendLog("   ${describeIncoming(message)}")
    }

    private fun describeIncoming(message: ByteArray): String {
        // Universal messages (Identity Reply) carry no Roland header or checksum.
        if (message.size >= 2 && (message[1].toInt() and 0xFF) == UNIVERSAL_NON_REALTIME) {
            return "SysEx universal (no Roland), p. ej. Identity Reply."
        }
        return when (val parsed = RolandSysEx.parse(message)) {
            is RolandMessage.Data -> buildString {
                append("Roland @ ").append(parsed.address)
                append(", ").append(parsed.data.size).append(" B de datos")
                asAscii(parsed.data)?.let { text -> append(" → \"").append(text).append('"') }
            }

            is RolandMessage.Invalid -> "No se pudo parsear: ${parsed.reason}"
        }
    }

    /** The payload as text, or null when it does not look like printable ASCII. */
    private fun asAscii(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val printable = data.all { byte -> (byte.toInt() and 0xFF) in 0x20..0x7E }
        return if (printable) String(data, Charsets.US_ASCII).trim() else null
    }

    /**
     * Brings up the repository over the freshly opened transport and reads the one parameter
     * it tracks, so the slider starts from the amp's real value instead of zero.
     */
    private fun startRepository(activeTransport: KatanaUsbTransport) {
        repository?.close()
        repositoryMirror?.cancel()

        val newRepository = KatanaRepository(
            link = TransportLink(activeTransport),
            scope = viewModelScope,
            onDiagnostic = { line -> appendLog("  [diag] $line") },
        )
        repository = newRepository
        repositoryMirror = viewModelScope.launch {
            newRepository.reverbLevel.collect { level -> _reverbLevel.value = level }
        }
        viewModelScope.launch {
            val level = newRepository.readReverbLevel()
            appendLog(
                if (level != null) "  Reverb level inicial: $level"
                else "  ! No se pudo leer el reverb level inicial."
            )
        }
    }

    /**
     * TEMPORARY: reads the reverb level straight from the amp and prints it.
     *
     * Answers question (b): pressed right after moving the slider, it says whether the SET
     * landed on the amp or not, which separates "the write never took" from "the write took
     * but the app does not show it".
     */
    fun onReadReverbLevelClicked() {
        val active = repository ?: return appendLog(NO_TRANSPORT)
        viewModelScope.launch {
            appendLog("→ GET reverb level (${KatanaAddresses.REVERB_LEVEL}):")
            val value = active.readReverbLevel()
            appendLog(
                if (value != null) "  ← el amp responde: $value"
                else "  · sin respuesta al GET de reverb level."
            )
        }
    }

    /** Moves the reverb level; the write is optimistic, so the slider does not lag. */
    fun onReverbLevelChanged(value: Int) {
        repository?.setReverbLevel(value)
    }

    private fun closeTransport() {
        readJob?.cancel()
        readJob = null
        repositoryMirror?.cancel()
        repositoryMirror = null
        repository?.close()
        repository = null
        _reverbLevel.value = null
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

        /** Second byte of a universal non-realtime SysEx. */
        const val UNIVERSAL_NON_REALTIME = 0x7E

        /**
         * Model id used in the handshake. Swap to [KatanaHandshake.MODEL_ID_MS3] (`0x3B`,
         * the value in the reference library) if `0x33` gets no answer from the amp.
         */
        const val HANDSHAKE_MODEL_ID = KatanaHandshake.MODEL_ID_KATANA

        /**
         * Window for the dump: much longer than a short GET, because the answer is several
         * messages spread over multiple `bulkTransfer` reads.
         */
        const val DUMP_WINDOW_MS = 3_000L

        const val NO_TRANSPORT = "No hay transporte abierto; busca el dispositivo primero."
        const val MAX_LOG_LINES = 500
    }
}
