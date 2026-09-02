package dev.alonx3.ktnacontrol.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Finds the Katana MK2 among the attached USB devices and secures access permission.
 *
 * Together with [KatanaUsbTransport] this replaces the discarded `midi/` package: the amp is
 * a vendor-specific USB device and is invisible to Android's MIDI framework (CLAUDE.md §4.1).
 */
class KatanaUsbScanner(context: Context) {

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _permissionResults = MutableSharedFlow<PermissionResult>(extraBufferCapacity = 8)

    /** Emits once per answered permission dialog. */
    val permissionResults: SharedFlow<PermissionResult> = _permissionResults.asSharedFlow()

    private var receiverRegistered = false

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return
            val device = IntentCompat.getParcelableExtra(
                intent,
                UsbManager.EXTRA_DEVICE,
                UsbDevice::class.java,
            )
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            _permissionResults.tryEmit(PermissionResult(device, granted))
        }
    }

    /** Whether this device exposes a USB host stack at all. */
    val isSupported: Boolean get() = usbManager != null

    /** Starts listening for permission-dialog answers. Idempotent. */
    fun start() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            appContext,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    /** Every currently attached USB device. */
    fun attachedDevices(): List<UsbDevice> =
        usbManager?.deviceList?.values?.toList().orEmpty()

    /** The first attached device whose vendor/product ids match the Katana MK2. */
    fun findKatana(): UsbDevice? =
        attachedDevices().firstOrNull { device -> device.describe().isKatana }

    fun hasPermission(device: UsbDevice): Boolean =
        usbManager?.hasPermission(device) == true

    /**
     * Shows the system permission dialog. The answer arrives asynchronously on
     * [permissionResults] — there is no return value to wait on.
     */
    fun requestPermission(device: UsbDevice) {
        val manager = usbManager ?: return
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName)
        // FLAG_MUTABLE is required from API 31: UsbManager fills EXTRA_DEVICE and
        // EXTRA_PERMISSION_GRANTED into this intent, and an immutable one arrives empty.
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(appContext, 0, intent, flags)
        manager.requestPermission(device, pendingIntent)
    }

    /** Opens the transport. The caller must hold permission for [device] first. */
    fun openTransport(device: UsbDevice): UsbOpenResult {
        val manager = usbManager
            ?: return UsbOpenResult.Failure("UsbManager no disponible")
        return KatanaUsbTransport.open(manager, device)
    }

    /** Stops listening. Call from `onCleared`. */
    fun close() {
        if (!receiverRegistered) return
        runCatching { appContext.unregisterReceiver(permissionReceiver) }
        receiverRegistered = false
    }

    /** Answer to one permission request. [device] may be null if the system sent none. */
    data class PermissionResult(val device: UsbDevice?, val granted: Boolean)

    private companion object {
        const val ACTION_USB_PERMISSION = "dev.alonx3.ktnacontrol.USB_PERMISSION"
    }
}

/**
 * Flattens a [UsbDevice] into the plain, loggable description.
 *
 * Careful with the interface count: `UsbDevice.getInterfaceCount()` does **not** return the
 * descriptor's `bNumInterfaces`. Android creates one [android.hardware.usb.UsbInterface] per
 * (interface, alternate setting) pair, so the Katana MK2 — 4 interfaces, of which 1, 2 and 3
 * have two alternate settings each — reports 7. The real count is the number of distinct
 * interface ids.
 */
fun UsbDevice.describe(): UsbDeviceDescription = UsbDeviceDescription(
    deviceName = deviceName,
    manufacturer = manufacturerName,
    product = productName,
    vendorId = vendorId,
    productId = productId,
    deviceClass = deviceClass,
    interfaceCount = (0 until interfaceCount).map { index -> getInterface(index).id }
        .distinct()
        .size,
    alternateSettingCount = interfaceCount,
)
