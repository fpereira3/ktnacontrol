package dev.alonx3.ktnacontrol.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.delay

/** Outcome of opening the transport; errors are values, not exceptions (CLAUDE.md §6). */
sealed interface UsbOpenResult {
    data class Success(val transport: KatanaUsbTransport) : UsbOpenResult
    data class Failure(val reason: String) : UsbOpenResult
}

/**
 * What one of the two handshake sends produced.
 *
 * Not a `data class`: it holds a [ByteArray], whose identity-based `equals` would make the
 * generated one misleading.
 */
class HandshakeAttempt(
    val attempt: Int,
    val written: Int,
    val wireResponse: ByteArray,
) {
    /** [wireResponse] with the USB-MIDI packet framing removed. */
    val sysexResponse: ByteArray get() = unpackUsbMidi(wireResponse)
}

/**
 * Raw byte pipe to the Katana MK2 over its vendor-specific bulk endpoints.
 *
 * Claims interface [KatanaUsbIds.CONTROL_INTERFACE_ID] and talks over
 * [KatanaUsbIds.ENDPOINT_BULK_OUT] / [KatanaUsbIds.ENDPOINT_BULK_IN]. This class knows
 * nothing about SysEx: it moves bytes and nothing else, so the framing and parsing stay in
 * `protocol/` where they can be tested on the JVM.
 *
 * `bulkTransfer` **blocks**, so every call here belongs on `Dispatchers.IO`.
 */
class KatanaUsbTransport private constructor(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val endpointOut: UsbEndpoint,
    private val endpointIn: UsbEndpoint,
) {

    private var closed = false

    /**
     * Packs [sysex] into 4-byte USB-MIDI packets and writes them to the bulk OUT endpoint.
     *
     * Takes a clean `F0…F7` message; the wire encoding is this class's business.
     *
     * @return bytes written **on the wire** (so 4/3 of the message, rounded up), or -1 on
     *   error/timeout.
     */
    fun sendRaw(sysex: ByteArray, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Int {
        if (closed) return -1
        val packed = packUsbMidi(sysex)
        return connection.bulkTransfer(endpointOut, packed, packed.size, timeoutMs)
    }

    /**
     * Reads one transfer from the bulk IN endpoint and returns it **exactly as it came off
     * the wire**, still in 4-byte packets.
     *
     * A timeout with no data is the normal idle case, not a failure: `bulkTransfer` returns
     * -1 and this returns an **empty array**. Callers should treat that as "nothing to read"
     * and keep polling, never as a disconnection.
     */
    fun receiveWire(timeoutMs: Int = DEFAULT_TIMEOUT_MS): ByteArray {
        if (closed) return ByteArray(0)
        val buffer = ByteArray(KatanaUsbIds.MAX_PACKET_SIZE)
        val read = connection.bulkTransfer(endpointIn, buffer, buffer.size, timeoutMs)
        return if (read > 0) buffer.copyOf(read) else ByteArray(0)
    }

    /**
     * Reads from the bulk IN endpoint and unpacks the USB-MIDI packets, so the caller gets a
     * plain MIDI byte stream.
     *
     * @return the unpacked bytes, or an empty array when there was nothing to read.
     */
    fun receiveRaw(timeoutMs: Int = DEFAULT_TIMEOUT_MS): ByteArray =
        unpackUsbMidi(receiveWire(timeoutMs))

    /**
     * Sends the mandatory handshake — the frame twice in a row, [KatanaHandshake.GAP_MS]
     * apart — and reports what came back after each send.
     *
     * Until this succeeds the amp ignores every other command, so it belongs before any
     * Identity Request or SysEx query.
     *
     * `suspend` on purpose: the gap between the two sends is part of the protocol, and
     * `delay` keeps it without blocking a thread. The read between the two sends uses
     * [KatanaHandshake.SHORT_READ_TIMEOUT_MS] precisely so that peeking at the answer does
     * not stretch that gap — the trade-off is that a slow first reply will be missed here
     * and show up on the next read instead.
     */
    suspend fun sendHandshake(
        modelId: Byte = KatanaHandshake.MODEL_ID_KATANA,
    ): List<HandshakeAttempt> {
        val message = KatanaHandshake.message(modelId)
        val attempts = ArrayList<HandshakeAttempt>(KatanaHandshake.REPEAT_COUNT)
        repeat(KatanaHandshake.REPEAT_COUNT) { index ->
            val written = sendRaw(message)
            val response = receiveWire(KatanaHandshake.SHORT_READ_TIMEOUT_MS)
            attempts += HandshakeAttempt(
                attempt = index + 1,
                written = written,
                wireResponse = response,
            )
            if (index < KatanaHandshake.REPEAT_COUNT - 1) {
                delay(KatanaHandshake.GAP_MS)
            }
        }
        return attempts
    }

    /** Releases the interface and the device connection. Idempotent. */
    fun close() {
        if (closed) return
        closed = true
        runCatching { connection.releaseInterface(usbInterface) }
        runCatching { connection.close() }
    }

    companion object {
        /** Long enough for the amp to answer, short enough not to freeze a read loop. */
        const val DEFAULT_TIMEOUT_MS = 1_000

        /**
         * Claims the control interface of [device] and resolves both bulk endpoints.
         *
         * The caller must already hold USB permission for [device].
         */
        fun open(manager: UsbManager, device: UsbDevice): UsbOpenResult {
            val controlInterface = device.findControlInterface()
                ?: return UsbOpenResult.Failure(
                    "El dispositivo no expone la interfaz ${KatanaUsbIds.CONTROL_INTERFACE_ID} " +
                        "(alt ${KatanaUsbIds.CONTROL_ALTERNATE_SETTING})"
                )

            val endpointOut = controlInterface.findEndpoint(KatanaUsbIds.ENDPOINT_BULK_OUT)
                ?: return UsbOpenResult.Failure("Falta el endpoint bulk OUT 0x03")
            val endpointIn = controlInterface.findEndpoint(KatanaUsbIds.ENDPOINT_BULK_IN)
                ?: return UsbOpenResult.Failure("Falta el endpoint bulk IN 0x84")

            val connection = manager.openDevice(device)
                ?: return UsbOpenResult.Failure("openDevice() falló (¿permiso denegado?)")

            if (!connection.claimInterface(controlInterface, true)) {
                connection.close()
                return UsbOpenResult.Failure(
                    "claimInterface(${KatanaUsbIds.CONTROL_INTERFACE_ID}) falló"
                )
            }

            return UsbOpenResult.Success(
                KatanaUsbTransport(connection, controlInterface, endpointOut, endpointIn)
            )
        }

        private fun UsbDevice.findControlInterface(): UsbInterface? =
            (0 until interfaceCount)
                .map { index -> getInterface(index) }
                .firstOrNull { candidate ->
                    candidate.id == KatanaUsbIds.CONTROL_INTERFACE_ID &&
                        candidate.alternateSetting == KatanaUsbIds.CONTROL_ALTERNATE_SETTING
                }

        private fun UsbInterface.findEndpoint(address: Int): UsbEndpoint? =
            (0 until endpointCount)
                .map { index -> getEndpoint(index) }
                .firstOrNull { endpoint -> endpoint.address == address }
    }
}
