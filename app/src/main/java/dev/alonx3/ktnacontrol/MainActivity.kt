package dev.alonx3.ktnacontrol

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import dev.alonx3.ktnacontrol.ui.screens.DebugConnectionScreen
import dev.alonx3.ktnacontrol.ui.screens.DebugConnectionViewModel
import dev.alonx3.ktnacontrol.ui.theme.KTNAControlTheme

class MainActivity : ComponentActivity() {

    /**
     * The same instance the screen gets through `viewModel()`: both resolve against this
     * activity's `ViewModelStore`.
     */
    private val viewModel: DebugConnectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleUsbIntent(intent)
        setContent {
            KTNAControlTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DebugConnectionScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    /**
     * Arrives when the amp is plugged in while this activity is already on top — the
     * manifest declares `launchMode="singleTop"` precisely so it lands here instead of
     * starting a second copy of the screen.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbIntent(intent)
    }

    /**
     * Picks up the device Android hands over when it launches us from the
     * `USB_DEVICE_ATTACHED` intent-filter, and connects to it straight away.
     *
     * Without this the app opened on its own but sat there waiting for someone to press
     * «Buscar dispositivo». Reading the extra also matters because launching through the
     * intent-filter grants USB permission for that device implicitly, so this path normally
     * skips the system dialog.
     */
    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
        val device = IntentCompat.getParcelableExtra(
            intent,
            UsbManager.EXTRA_DEVICE,
            UsbDevice::class.java,
        ) ?: return
        viewModel.onDeviceAttachedByIntent(device)
    }
}
