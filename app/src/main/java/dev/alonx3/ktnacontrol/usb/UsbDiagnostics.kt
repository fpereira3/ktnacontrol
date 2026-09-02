package dev.alonx3.ktnacontrol.usb

/**
 * Plain Kotlin model and constants for the USB transport. Deliberately free of `android.*`
 * imports so it can be unit-tested on the JVM (see CLAUDE.md §4.2).
 */

/** USB identifiers and endpoint layout of the Katana MK2, confirmed with `lsusb -v`. */
object KatanaUsbIds {
    /** Roland, 0x0582. */
    const val VENDOR_ID = 1410

    /** Boss Katana MK2, 0x01D8. */
    const val PRODUCT_ID = 472

    /** Vendor-specific control interface (class 255, subclass 3, protocol 0). */
    const val CONTROL_INTERFACE_ID = 3

    /** The alternate setting that is active by default on plug-in. */
    const val CONTROL_ALTERNATE_SETTING = 0

    /** Bulk OUT — app writes to the amp here. */
    const val ENDPOINT_BULK_OUT = 0x03

    /** Bulk IN — app reads the amp's answers here. */
    const val ENDPOINT_BULK_IN = 0x84

    /** `wMaxPacketSize` of both bulk endpoints (USB High Speed). */
    const val MAX_PACKET_SIZE = 512
}

/**
 * The fixed handshake the amp needs before it answers anything at all (CLAUDE.md §4.1).
 *
 * Shape of an Identity Reply: `F0 7E <dev> 06 02 <maker> <model> …  F7`. Source is
 * MrHaroldA/MS3 (`MS3.h`, `setEditorMode()`), which naturally carries the **MS-3** model id;
 * [MODEL_ID_MS3] is kept next to [MODEL_ID_KATANA] so swapping them is a one-word change if
 * the Katana turns out to want the literal MS-3 frame.
 */
object KatanaHandshake {
    /** Boss Katana MK2 — matches the `00 00 00 33` model id of the SysEx spec. */
    const val MODEL_ID_KATANA: Byte = 0x33

    /** Boss MS-3 — the value the reference library uses. Fallback if `0x33` gets no answer. */
    const val MODEL_ID_MS3: Byte = 0x3B

    /** The amp needs the frame twice before it starts talking. */
    const val REPEAT_COUNT = 2

    /** Gap between the two sends, as in the reference library. */
    const val GAP_MS = 4L

    /** `F0 7E 00 06 02 41 <modelId> 03 00 00 00 00 00 00 F7` — 15 bytes. */
    fun message(modelId: Byte = MODEL_ID_KATANA): ByteArray = byteArrayOf(
        0xF0.toByte(), 0x7E, 0x00, 0x06, 0x02, 0x41, modelId, 0x03,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF7.toByte(),
    )
}

/** A single line of the on-screen console. */
data class UsbLogLine(
    val timestampMillis: Long,
    val text: String,
)

/**
 * Snapshot of a `UsbDevice`, flattened into plain values for logging.
 *
 * [interfaceCount] is the number of **real** interfaces (distinct `bInterfaceNumber`), which
 * is what the USB descriptor calls `bNumInterfaces`. [alternateSettingCount] is what
 * `UsbDevice.getInterfaceCount()` returns, and it is a different number: Android exposes one
 * `UsbInterface` per (interface, alternate setting) pair. For the Katana MK2 that is 4 vs 7.
 */
data class UsbDeviceDescription(
    val deviceName: String,
    val manufacturer: String?,
    val product: String?,
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val interfaceCount: Int,
    val alternateSettingCount: Int,
) {
    /** True when the ids match the Katana MK2 exactly. */
    val isKatana: Boolean
        get() = vendorId == KatanaUsbIds.VENDOR_ID && productId == KatanaUsbIds.PRODUCT_ID

    /** One-line summary for the console. */
    fun toLogText(): String = buildString {
        append(product ?: deviceName)
        append(" — fabricante: ").append(manufacturer ?: "¿?")
        append(", vendorId: ").append(formatId(vendorId))
        append(", productId: ").append(formatId(productId))
        append(", clase: ").append(deviceClass)
        append(", interfaces: ").append(interfaceCount)
        append(" (").append(alternateSettingCount).append(" alt settings)")
    }

    private fun formatId(id: Int): String = "$id (0x%04X)".format(id)
}

/** Where the USB transport currently stands. */
sealed interface UsbConnectionState {
    /** Nothing attempted yet. */
    data object Idle : UsbConnectionState

    /** Enumerating devices. */
    data object Searching : UsbConnectionState

    /** Devices were listed but none matched the Katana's ids. */
    data object KatanaNotFound : UsbConnectionState

    /** The system permission dialog is up; waiting for the user. */
    data object AwaitingPermission : UsbConnectionState

    /** Interface claimed and both endpoints resolved. */
    data class Connected(val deviceName: String) : UsbConnectionState

    /** Something went wrong; [reason] is already human-readable. */
    data class Failed(val reason: String) : UsbConnectionState
}

/** Formats raw bytes as uppercase hex, e.g. `F0 7E 7F 06 01 F7`. */
fun ByteArray.toHexString(): String =
    joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }
