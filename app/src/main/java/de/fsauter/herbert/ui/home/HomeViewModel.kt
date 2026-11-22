package de.fsauter.herbert.ui.home

import android.app.Application
import android.os.Build
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.fsauter.herbert.HerbertApplication
import de.fsauter.herbert.R
import de.fsauter.herbert.ble.BleScanEvent
import de.fsauter.herbert.ble.HerbertBleScanner
import de.fsauter.herbert.data.SavedHerbertDevice
import de.fsauter.herbert.ui.permissions.BlePermissions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeDeviceUiItem(
    val device: SavedHerbertDevice,
    val displayIndex: Int,
    val isNearby: Boolean,
)

data class HomeUiState(
    val devices: List<HomeDeviceUiItem> = emptyList(),
    val isScanning: Boolean = false,
    val hasPermissions: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val needsLocationService: Boolean = false,
    val usesModernBlePermission: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    val isMockSupported: Boolean = false,
    val isMockModeEnabled: Boolean = false,
    @param:StringRes val errorMessageRes: Int? = null,
)

private data class ScanStatus(
    val isScanning: Boolean = false,
    val hasPermissions: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val needsLocationService: Boolean = false,
    @param:StringRes val errorMessageRes: Int? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as HerbertApplication
    private val bleProvider = app.bleProvider

    private val nearbyMacs = MutableStateFlow<Set<String>>(emptySet())
    private val scanStatus = MutableStateFlow(readEnvironmentStatus())
    private var scanJob: Job? = null
    private var hasAutoScanned = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeDevicesFlow = bleProvider.isMockModeEnabled
        .flatMapLatest { bleProvider.deviceStore().devices }

    val uiState: StateFlow<HomeUiState> = combine(
        activeDevicesFlow,
        nearbyMacs,
        scanStatus,
        bleProvider.isMockModeEnabled,
    ) { savedDevices, nearby, status, mockEnabled ->
        val sorted = savedDevices.sortedByDescending { nearby.contains(it.macAddress.uppercase()) }
        val items = sorted.mapIndexed { idx, device ->
            HomeDeviceUiItem(
                device = device,
                displayIndex = idx + 1,
                isNearby = nearby.contains(device.macAddress.uppercase()),
            )
        }
        HomeUiState(
            devices = items,
            isScanning = status.isScanning,
            hasPermissions = status.hasPermissions,
            isBluetoothEnabled = status.isBluetoothEnabled,
            needsLocationService = status.needsLocationService,
            isMockSupported = bleProvider.isMockSupported,
            isMockModeEnabled = mockEnabled,
            errorMessageRes = status.errorMessageRes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HomeUiState(
            isMockSupported = bleProvider.isMockSupported,
            isMockModeEnabled = bleProvider.isMockModeEnabled.value,
        ),
    )

    fun toggleMockMode() {
        if (!bleProvider.isMockSupported) return
        scanJob?.cancel()
        nearbyMacs.value = emptySet()
        bleProvider.toggleMockMode()
        hasAutoScanned = false
        val env = readEnvironmentStatus()
        scanStatus.value = env
        if (env.hasPermissions && env.isBluetoothEnabled && !env.needsLocationService) {
            startScan()
        }
    }

    fun refreshEnvironment(triggerScanIfReady: Boolean = false) {
        val updated = readEnvironmentStatus()
        scanStatus.value = updated.copy(
            isScanning = scanStatus.value.isScanning,
            errorMessageRes = if (updated.hasPermissions && updated.isBluetoothEnabled) {
                scanStatus.value.errorMessageRes
            } else {
                null
            },
        )
        if (triggerScanIfReady &&
            !hasAutoScanned &&
            updated.hasPermissions &&
            updated.isBluetoothEnabled &&
            !updated.needsLocationService
        ) {
            hasAutoScanned = true
            startScan()
        }
    }

    fun startScan() {
        val scanner = bleProvider.scanner()
        val activeStore = bleProvider.deviceStore()
        val env = readEnvironmentStatus()
        scanStatus.value = env
        if (!env.hasPermissions || !env.isBluetoothEnabled || env.needsLocationService) {
            return
        }

        hasAutoScanned = true
        scanJob?.cancel()
        nearbyMacs.value = emptySet()
        scanStatus.value = env.copy(isScanning = true, errorMessageRes = null)

        scanJob = viewModelScope.launch {
            scanner.scan().collect { event ->
                when (event) {
                    is BleScanEvent.DeviceFound -> {
                        activeStore.upsertDiscovered(
                            macAddress = event.device.macAddress,
                            advertisedName = event.device.advertisedName,
                        )
                        nearbyMacs.value = nearbyMacs.value + event.device.macAddress.uppercase()
                    }

                    is BleScanEvent.Failed -> {
                        val errorRes = when (event.errorCode) {
                            HerbertBleScanner.SCAN_ERROR_BLE_UNAVAILABLE ->
                                R.string.error_bluetooth_unavailable
                            else -> R.string.error_scan_start_failed
                        }
                        scanStatus.value = readEnvironmentStatus().copy(
                            isScanning = false,
                            errorMessageRes = errorRes,
                        )
                    }

                    BleScanEvent.Completed -> {
                        scanStatus.value = readEnvironmentStatus().copy(isScanning = false)
                    }
                }
            }
        }
    }

    fun clearError() {
        scanStatus.value = scanStatus.value.copy(errorMessageRes = null)
    }

    private fun readEnvironmentStatus(): ScanStatus {
        val scanner = bleProvider.scanner()
        return if (!scanner.requiresRuntimePermissions) {
            ScanStatus(
                isScanning = false,
                hasPermissions = true,
                isBluetoothEnabled = scanner.isBluetoothEnabled(),
                needsLocationService = false,
            )
        } else {
            ScanStatus(
                isScanning = false,
                hasPermissions = BlePermissions.hasAllScanPermissions(app),
                isBluetoothEnabled = scanner.isBluetoothEnabled(),
                needsLocationService = BlePermissions.needsLocationServiceForScan(app),
            )
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
    }
}
