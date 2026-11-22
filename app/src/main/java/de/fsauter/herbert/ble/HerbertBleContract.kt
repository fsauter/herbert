package de.fsauter.herbert.ble

import de.fsauter.herbert.data.DeviceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

interface HerbertScanner {
    val requiresRuntimePermissions: Boolean
    fun isBluetoothEnabled(): Boolean
    fun scan(durationMs: Long = HerbertProtocol.SCAN_DURATION_MS): Flow<BleScanEvent>
}

interface HerbertSession {
    val connectionState: StateFlow<BleConnectionState>
    val isSyncing: StateFlow<Boolean>
    suspend fun connect(): Boolean
    suspend fun syncAll(currentTime: DeviceTime = DeviceTime.now()): SyncedHerbertState?
    suspend fun writeCharacteristic(uuid: UUID, data: ByteArray): Boolean
    fun disconnect()
}

interface HerbertBleProvider {
    val isMockSupported: Boolean
    val isMockModeEnabled: StateFlow<Boolean>
    fun toggleMockMode()
    fun scanner(): HerbertScanner
    fun createSession(macAddress: String): HerbertSession
    fun deviceStore(): DeviceStore
}
