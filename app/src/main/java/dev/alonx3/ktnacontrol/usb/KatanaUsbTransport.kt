package dev.alonx3.ktnacontrol.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import dev.alonx3.ktnacontrol.protocol.SysExFramer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/** Outcome of opening the transport; errors are values, not exceptions (CLAUDE.md §6). */
sealed interface UsbOpenResult {
    data class Success(val transport: KatanaUsbTransport) : UsbOpenResult
    data class Failure(val reason: String) : UsbOpenResult
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
     * A stream of complete `F0…F7` messages arriving from the amp, already unpacked.
     *
     * Runs a read loop on `Dispatchers.IO`: `bulkTransfer` blocks for at most [timeoutMs]
     * and returns -1 when nothing showed up, which is the normal idle case — the loop keeps
     * listening rather than treating it as an error or a disconnection. Whatever does arrive
     * goes through [unpackUsbMidi] and then a [SysExFramer], because one transfer may carry
     * half a message or several of them.
     *
     * Collect this from the moment the transport opens: the amp also talks unprompted (front
     * panel knobs, derived parameters), and those messages arrive here just the same.
     *
     * The flow ends on its own when [close] is called, and cancelling the collecting
     * coroutine stops it after at most [timeoutMs].
     */
    fun incomingMessages(timeoutMs: Int = READ_LOOP_TIMEOUT_MS): Flow<ByteArray> = flow {
        val framer = SysExFramer()
        while (currentCoroutineContext().isActive && !closed) {
            val wire = receiveWire(timeoutMs)
            if (wire.isEmpty()) continue // Timeout: nothing to read, keep listening.
            framer.feed(unpackUsbMidi(wire)).forEach { message -> emit(message) }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Sends the mandatory handshake: the frame twice in a row, [KatanaHandshake.GAP_MS]
     * apart. Until this succeeds the amp ignores every other command, so it belongs before
     * any Identity Request or SysEx query.
     *
     * Only sends — anything the amp replies arrives through [incomingMessages], which is the
     * single reader of the endpoint.
     *
     * `suspend` on purpose: the gap between the two sends is part of the protocol, and
     * `delay` keeps it without blocking a thread.
     *
     * @return bytes written on the wire by each of the two sends.
     */
    suspend fun sendHandshake(
        modelId: Byte = KatanaHandshake.MODEL_ID_KATANA,
    ): List<Int> {
        val message = KatanaHandshake.message(modelId)
        val written = ArrayList<Int>(KatanaHandshake.REPEAT_COUNT)
        repeat(KatanaHandshake.REPEAT_COUNT) { index ->
            written += sendRaw(message)
            if (index < KatanaHandshake.REPEAT_COUNT - 1) {
                delay(KatanaHandshake.GAP_MS)
            }
        }
        return written
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
         * Poll interval of [incomingMessages]. `bulkTransfer` blocks for this long when
         * there is nothing to read, so it is not a busy loop; it also bounds how long
         * cancelling the collector takes to take effect.
         */
        const val READ_LOOP_TIMEOUT_MS = 100

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
