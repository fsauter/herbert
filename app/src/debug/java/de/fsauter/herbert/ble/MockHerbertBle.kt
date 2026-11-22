package de.fsauter.herbert.ble

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * In-memory registry holding simulated Herbert garden state for debug/emulator testing.
 */
class MockHerbertRegistry {
    private val lock = Any()
    private val devices = linkedMapOf(
        MAC_KITCHEN to SyncedHerbertState(
            name = "Kitchen Herbs",
            lightMode = LightMode(auto = true, smoothChange = true, holiday = false),
            brightness = 80,
            sunriseTime = DeviceTime(hour = 7, minute = 0),
            sunsetTime = DeviceTime(hour = 21, minute = 0),
            waterLevel = WaterLevel.OK,
        ),
        MAC_LIVING_ROOM to SyncedHerbertState(
            name = "Living Room",
            lightMode = LightMode(manualOn = true, auto = false),
            brightness = 45,
            sunriseTime = DeviceTime(hour = 6, minute = 30),
            sunsetTime = DeviceTime(hour = 20, minute = 30),
            waterLevel = WaterLevel.LOW,
        ),
    )

    fun listDevices(): List<DiscoveredHerbert> = synchronized(lock) {
        devices.map { (mac, state) ->
            DiscoveredHerbert(macAddress = mac, advertisedName = state.name)
        }
    }

    fun get(macAddress: String): SyncedHerbertState? = synchronized(lock) {
        devices[macAddress.uppercase()]
    }

    fun update(macAddress: String, transform: (SyncedHerbertState) -> SyncedHerbertState): Boolean =
        synchronized(lock) {
            val key = macAddress.uppercase()
            val current = devices[key] ?: return false
            devices[key] = transform(current)
            true
        }

    companion object {
        const val MAC_KITCHEN = "AA:BB:CC:00:00:01"
        const val MAC_LIVING_ROOM = "AA:BB:CC:00:00:02"
    }
}

class MockHerbertScanner(
    private val registry: MockHerbertRegistry,
    private val stepDelayMs: Long = 350L,
) : HerbertScanner {

    override val requiresRuntimePermissions: Boolean = false

    override fun isBluetoothEnabled(): Boolean = true

    override fun scan(durationMs: Long): Flow<BleScanEvent> = flow {
        val devices = registry.listDevices()
        val effectiveDelay = minOf(stepDelayMs, durationMs / (devices.size + 1).coerceAtLeast(1))
        for (device in devices) {
            delay(effectiveDelay)
            emit(BleScanEvent.DeviceFound(device))
        }
        delay(effectiveDelay)
        emit(BleScanEvent.Completed)
    }
}

class MockHerbertSession(
    private val macAddress: String,
    private val registry: MockHerbertRegistry,
    private val operationDelayMs: Long = 250L,
) : HerbertSession {

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    override suspend fun connect(): Boolean {
        _connectionState.value = BleConnectionState.CONNECTING
        delay(operationDelayMs)
        val exists = registry.get(macAddress) != null
        _connectionState.value = if (exists) {
            BleConnectionState.CONNECTED
        } else {
            BleConnectionState.DISCONNECTED
        }
        return exists
    }

    override suspend fun syncAll(currentTime: DeviceTime): SyncedHerbertState? {
        if (_connectionState.value != BleConnectionState.CONNECTED) return null
        _isSyncing.value = true
        return try {
            delay(operationDelayMs)
            registry.get(macAddress)
        } finally {
            _isSyncing.value = false
        }
    }

    override suspend fun writeCharacteristic(uuid: UUID, data: ByteArray): Boolean {
        if (_connectionState.value != BleConnectionState.CONNECTED) return false
        return registry.update(macAddress) { state ->
            when (uuid) {
                HerbertProtocol.DEVICE_NAME_UUID ->
                    state.copy(name = HerbertProtocol.decodeDeviceName(data))
                HerbertProtocol.LIGHT_MODE_UUID ->
                    state.copy(lightMode = LightMode.decode(data))
                HerbertProtocol.BRIGHTNESS_UUID ->
                    state.copy(brightness = HerbertProtocol.decodeBrightness(data))
                HerbertProtocol.SUNRISE_TIME_UUID ->
                    state.copy(sunriseTime = DeviceTime.decode(data))
                HerbertProtocol.SUNSET_TIME_UUID ->
                    state.copy(sunsetTime = DeviceTime.decode(data))
                HerbertProtocol.CURRENT_TIME_UUID -> state
                else -> state
            }
        }
    }

    override fun disconnect() {
        _connectionState.value = BleConnectionState.DISCONNECTED
        _isSyncing.value = false
    }
}
