package de.fsauter.herbert.ble

import android.content.Context
import de.fsauter.herbert.data.DeviceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private class RealBleProvider(context: Context) : HerbertBleProvider {
    private val appContext = context.applicationContext
    private val realScanner = HerbertBleScanner(appContext)
    private val realDeviceStore = DeviceStore.forContext(appContext)

    override val isMockSupported: Boolean = false
    override val isMockModeEnabled: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    override fun toggleMockMode() = Unit

    override fun scanner(): HerbertScanner = realScanner

    override fun createSession(macAddress: String): HerbertSession =
        HerbertGattSession(appContext, macAddress)

    override fun deviceStore(): DeviceStore = realDeviceStore
}

object BleProviderFactory {
    fun create(context: Context): HerbertBleProvider = RealBleProvider(context)
}
