package de.fsauter.herbert.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

data class DiscoveredHerbert(
    val macAddress: String,
    val advertisedName: String?,
)

sealed interface BleScanEvent {
    data class DeviceFound(val device: DiscoveredHerbert) : BleScanEvent
    data class Failed(val errorCode: Int) : BleScanEvent
    data object Completed : BleScanEvent
}

class HerbertBleScanner(private val context: Context) : HerbertScanner {

    override val requiresRuntimePermissions: Boolean = true

    override fun isBluetoothEnabled(): Boolean {
        val manager = context.getSystemService(BluetoothManager::class.java)
        return manager?.adapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    override fun scan(durationMs: Long): Flow<BleScanEvent> = callbackFlow {
        val manager = context.getSystemService(BluetoothManager::class.java)
        val scanner = manager?.adapter?.bluetoothLeScanner
        if (scanner == null || !isBluetoothEnabled()) {
            trySend(BleScanEvent.Failed(SCAN_ERROR_BLE_UNAVAILABLE))
            close()
            return@callbackFlow
        }

        val seenMacs = HashSet<String>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val address = device.address ?: return
                if (seenMacs.add(address)) {
                    val advertisedName = try {
                        result.scanRecord?.deviceName ?: device.name
                    } catch (_: SecurityException) {
                        result.scanRecord?.deviceName
                    }
                    trySend(
                        BleScanEvent.DeviceFound(
                            DiscoveredHerbert(
                                macAddress = address,
                                advertisedName = advertisedName,
                            ),
                        ),
                    )
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                trySend(BleScanEvent.Failed(errorCode))
                close()
            }
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HerbertProtocol.SERVICE_UUID))
                .build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        try {
            scanner.startScan(filters, settings, callback)
        } catch (_: SecurityException) {
            trySend(BleScanEvent.Failed(SCAN_ERROR_PERMISSION))
            close()
            return@callbackFlow
        } catch (_: IllegalStateException) {
            trySend(BleScanEvent.Failed(SCAN_ERROR_BLE_UNAVAILABLE))
            close()
            return@callbackFlow
        }

        val timeoutJob = launch {
            delay(durationMs)
            trySend(BleScanEvent.Completed)
            close()
        }

        awaitClose {
            timeoutJob.cancel()
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
            } catch (_: IllegalStateException) {
            }
        }
    }

    companion object {
        const val SCAN_ERROR_BLE_UNAVAILABLE = -1
        const val SCAN_ERROR_PERMISSION = -2
    }
}
