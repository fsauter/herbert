package de.fsauter.herbert.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

enum class BleConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

data class SyncedHerbertState(
    val name: String,
    val lightMode: LightMode,
    val brightness: Int,
    val sunriseTime: DeviceTime,
    val sunsetTime: DeviceTime,
    val waterLevel: WaterLevel,
)

class HerbertGattSession(
    private val context: Context,
    private val macAddress: String,
) : HerbertSession {
    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val operationMutex = Mutex()
    private val connectMutex = Mutex()

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var pendingConnect: CompletableDeferred<Boolean>? = null

    @Volatile
    private var pendingRead: CompletableDeferred<ByteArray?>? = null

    @Volatile
    private var pendingWrite: CompletableDeferred<Boolean>? = null

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                try {
                    if (!gatt.discoverServices()) {
                        completeConnect(false)
                    }
                } catch (_: SecurityException) {
                    completeConnect(false)
                }
            } else {
                _connectionState.value = BleConnectionState.DISCONNECTED
                completeConnect(false)
                pendingRead?.complete(null)
                pendingWrite?.complete(false)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = BleConnectionState.CONNECTED
                completeConnect(true)
            } else {
                _connectionState.value = BleConnectionState.DISCONNECTED
                completeConnect(false)
            }
        }

        // Android 13+ (API 33+) callback
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            val result = if (status == BluetoothGatt.GATT_SUCCESS) value.copyOf() else null
            pendingRead?.complete(result)
        }

        // Legacy Android 8–12 (API 26–32) callback
        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val result = if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristic.value?.copyOf()
            } else {
                null
            }
            pendingRead?.complete(result)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    private fun completeConnect(success: Boolean) {
        pendingConnect?.complete(success)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    override suspend fun connect(): Boolean = connectMutex.withLock {
        if (_connectionState.value == BleConnectionState.CONNECTED && gatt != null) {
            return true
        }
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return false
        val adapter = manager.adapter ?: return false
        if (!adapter.isEnabled) return false

        val device = try {
            adapter.getRemoteDevice(macAddress)
        } catch (_: IllegalArgumentException) {
            return false
        }

        repeat(MAX_CONNECT_RETRIES + 1) { attempt ->
            closeGattOnly()
            _connectionState.value = BleConnectionState.CONNECTING
            val deferred = CompletableDeferred<Boolean>()
            pendingConnect = deferred

            val connectedGatt = try {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } catch (_: SecurityException) {
                _connectionState.value = BleConnectionState.DISCONNECTED
                pendingConnect = null
                return false
            }

            if (connectedGatt == null) {
                _connectionState.value = BleConnectionState.DISCONNECTED
                pendingConnect = null
            } else {
                gatt = connectedGatt
                val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { deferred.await() } == true
                pendingConnect = null
                if (ok) return true
            }

            if (attempt < MAX_CONNECT_RETRIES) {
                delay(RETRY_DELAY_MS)
            }
        }

        closeGattOnly()
        _connectionState.value = BleConnectionState.DISCONNECTED
        return false
    }

    override suspend fun syncAll(currentTime: DeviceTime): SyncedHerbertState? {
        if (_connectionState.value != BleConnectionState.CONNECTED) return null
        _isSyncing.value = true
        return try {
            val nameBytes = readCharacteristic(HerbertProtocol.DEVICE_NAME_UUID)
            val modeBytes = readCharacteristic(HerbertProtocol.LIGHT_MODE_UUID)
            val brightnessBytes = readCharacteristic(HerbertProtocol.BRIGHTNESS_UUID)
            val sunriseBytes = readCharacteristic(HerbertProtocol.SUNRISE_TIME_UUID)
            val sunsetBytes = readCharacteristic(HerbertProtocol.SUNSET_TIME_UUID)
            val waterBytes = readCharacteristic(HerbertProtocol.WATER_LEVEL_UUID)

            // Sync phone clock to Herbert after reading characteristics, matching original app behavior
            writeCharacteristic(HerbertProtocol.CURRENT_TIME_UUID, currentTime.encode())

            SyncedHerbertState(
                name = HerbertProtocol.decodeDeviceName(nameBytes),
                lightMode = LightMode.decode(modeBytes),
                brightness = HerbertProtocol.decodeBrightness(brightnessBytes),
                sunriseTime = DeviceTime.decode(sunriseBytes),
                sunsetTime = DeviceTime.decode(sunsetBytes),
                waterLevel = WaterLevel.decode(waterBytes),
            )
        } finally {
            _isSyncing.value = false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun readCharacteristic(uuid: UUID): ByteArray? = operationMutex.withLock {
        val currentGatt = gatt ?: return null
        val characteristic = findCharacteristic(currentGatt, uuid) ?: return null
        val deferred = CompletableDeferred<ByteArray?>()
        pendingRead = deferred
        val started = try {
            currentGatt.readCharacteristic(characteristic)
        } catch (_: SecurityException) {
            false
        }
        if (!started) {
            pendingRead = null
            return null
        }
        val result = withTimeoutOrNull(OPERATION_TIMEOUT_MS) { deferred.await() }
        pendingRead = null
        return result
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    override suspend fun writeCharacteristic(uuid: UUID, data: ByteArray): Boolean = operationMutex.withLock {
        val currentGatt = gatt ?: return false
        if (_connectionState.value != BleConnectionState.CONNECTED) return false
        val characteristic = findCharacteristic(currentGatt, uuid) ?: return false

        val deferred = CompletableDeferred<Boolean>()
        pendingWrite = deferred

        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentGatt.writeCharacteristic(
                    characteristic,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.value = data
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                currentGatt.writeCharacteristic(characteristic)
            }
        } catch (_: SecurityException) {
            false
        }

        if (!started) {
            pendingWrite = null
            return false
        }
        val ok = withTimeoutOrNull(OPERATION_TIMEOUT_MS) { deferred.await() } == true
        pendingWrite = null
        return ok
    }

    override fun disconnect() {
        completeConnect(false)
        pendingRead?.complete(null)
        pendingWrite?.complete(false)
        closeGattOnly()
        _connectionState.value = BleConnectionState.DISCONNECTED
        _isSyncing.value = false
    }

    @SuppressLint("MissingPermission")
    private fun closeGattOnly() {
        val existing = gatt ?: return
        gatt = null
        try {
            existing.disconnect()
        } catch (_: SecurityException) {
        }
        try {
            existing.close()
        } catch (_: SecurityException) {
        }
    }

    private fun findCharacteristic(
        gatt: BluetoothGatt,
        uuid: UUID,
    ): BluetoothGattCharacteristic? {
        // Search all discovered services so GAP Device Name (00002A00 in 00001800) is also found
        for (service in gatt.services) {
            service.getCharacteristic(uuid)?.let { return it }
        }
        return null
    }

    companion object {
        private const val MAX_CONNECT_RETRIES = 2
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val OPERATION_TIMEOUT_MS = 5_000L
        private const val RETRY_DELAY_MS = 350L
    }
}
