package de.fsauter.herbert.ble

import android.content.Context
import android.os.Build
import de.fsauter.herbert.data.DeviceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

private class DebugBleProvider(context: Context) : HerbertBleProvider {
    private val appContext = context.applicationContext
    private val registry = MockHerbertRegistry()
    private val realScanner = HerbertBleScanner(appContext)
    private val mockScanner = MockHerbertScanner(registry)
    private val realDeviceStore = DeviceStore.forContext(appContext)
    private val mockDeviceStore = DeviceStore(File(appContext.filesDir, "herbert_mock_devices.json"))

    private val runningOnEmulator = isRunningOnEmulator()
    private val _isMockModeEnabled = MutableStateFlow(runningOnEmulator)
    override val isMockSupported: Boolean = runningOnEmulator
    override val isMockModeEnabled: StateFlow<Boolean> = _isMockModeEnabled.asStateFlow()

    override fun toggleMockMode() {
        _isMockModeEnabled.value = !_isMockModeEnabled.value
    }

    override fun scanner(): HerbertScanner =
        if (_isMockModeEnabled.value) mockScanner else realScanner

    override fun createSession(macAddress: String): HerbertSession =
        if (_isMockModeEnabled.value) {
            MockHerbertSession(macAddress, registry)
        } else {
            HerbertGattSession(appContext, macAddress)
        }

    override fun deviceStore(): DeviceStore =
        if (_isMockModeEnabled.value) mockDeviceStore else realDeviceStore

    private fun isRunningOnEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("emulator")
}

object BleProviderFactory {
    fun create(context: Context): HerbertBleProvider = DebugBleProvider(context)
}
