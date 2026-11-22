package de.fsauter.herbert.ui.detail

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.fsauter.herbert.HerbertApplication
import de.fsauter.herbert.R
import de.fsauter.herbert.ble.BleConnectionState
import de.fsauter.herbert.ble.DeviceTime
import de.fsauter.herbert.ble.HerbertProtocol
import de.fsauter.herbert.ble.HerbertSession
import de.fsauter.herbert.ble.LightMode
import de.fsauter.herbert.data.SavedHerbertDevice
import de.fsauter.herbert.ui.permissions.BlePermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class DeviceDetailUiState(
    val device: SavedHerbertDevice,
    val connectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val isSyncing: Boolean = false,
    val sliderBrightnessOverride: Int? = null,
    @param:StringRes val messageRes: Int? = null,
) {
    val displayedBrightness: Int
        get() = sliderBrightnessOverride ?: device.brightness

    val controlsEnabled: Boolean
        get() = connectionState == BleConnectionState.CONNECTED && !isSyncing

    val showWaterLevel: Boolean
        get() = controlsEnabled
}

class DeviceDetailViewModel(
    application: Application,
    private val macAddress: String,
) : AndroidViewModel(application) {

    private val app = application as HerbertApplication
    private val deviceStore = app.bleProvider.deviceStore()
    private val photoStorage = app.photoStorage
    private val gattSession: HerbertSession = app.bleProvider.createSession(macAddress)

    private val sliderOverride = MutableStateFlow<Int?>(null)
    private val messageRes = MutableStateFlow<Int?>(null)

    private val deviceFlow = deviceStore.devices.map { list ->
        list.firstOrNull { it.macAddress.equals(macAddress, ignoreCase = true) }
            ?: SavedHerbertDevice(macAddress = macAddress)
    }

    val uiState: StateFlow<DeviceDetailUiState> = combine(
        deviceFlow,
        gattSession.connectionState,
        gattSession.isSyncing,
        sliderOverride,
        messageRes,
    ) { device, connState, syncing, brightnessDrag, msg ->
        DeviceDetailUiState(
            device = device,
            connectionState = connState,
            isSyncing = syncing,
            sliderBrightnessOverride = brightnessDrag,
            messageRes = msg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DeviceDetailUiState(
            device = deviceStore.getDevice(macAddress) ?: SavedHerbertDevice(macAddress = macAddress),
        ),
    )

    init {
        connectAndSync()
    }

    fun connectAndSync() {
        val scanner = app.bleProvider.scanner()
        if (scanner.requiresRuntimePermissions &&
            (!BlePermissions.hasConnectPermission(app) || !scanner.isBluetoothEnabled())
        ) {
            return
        }
        viewModelScope.launch {
            val connected = gattSession.connect()
            if (connected) {
                val synced = gattSession.syncAll()
                if (synced != null) {
                    deviceStore.updateDevice(macAddress) { current ->
                        current.copy(
                            name = synced.name,
                            lightMode = synced.lightMode,
                            brightness = synced.brightness,
                            sunriseTime = synced.sunriseTime,
                            sunsetTime = synced.sunsetTime,
                            waterLevel = synced.waterLevel,
                        )
                    }
                }
            }
        }
    }

    fun renameDevice(newName: String) {
        val sanitized = newName.trim().take(HerbertProtocol.MAX_NAME_LENGTH)
        if (sanitized.isEmpty()) return
        deviceStore.updateDevice(macAddress) { it.copy(name = sanitized) }
        writeCharacteristic(
            uuid = HerbertProtocol.DEVICE_NAME_UUID,
            data = HerbertProtocol.encodeDeviceName(sanitized),
        )
    }

    fun onBrightnessDragging(value: Int) {
        sliderOverride.value = value.coerceIn(0, HerbertProtocol.MAX_BRIGHTNESS)
    }

    fun commitBrightness() {
        val value = (sliderOverride.value ?: uiState.value.device.brightness)
            .coerceIn(0, HerbertProtocol.MAX_BRIGHTNESS)
        sliderOverride.value = null
        deviceStore.updateDevice(macAddress) { it.copy(brightness = value) }
        writeCharacteristic(
            uuid = HerbertProtocol.BRIGHTNESS_UUID,
            data = HerbertProtocol.encodeBrightness(value),
        )
    }

    fun updateLightMode(transform: (LightMode) -> LightMode) {
        val updated = transform(uiState.value.device.lightMode)
        deviceStore.updateDevice(macAddress) { it.copy(lightMode = updated) }
        writeCharacteristic(
            uuid = HerbertProtocol.LIGHT_MODE_UUID,
            data = updated.encode(),
        )
    }

    fun setSunriseTime(hour: Int, minute: Int) {
        val time = DeviceTime(hour = hour, minute = minute)
        deviceStore.updateDevice(macAddress) { it.copy(sunriseTime = time) }
        writeCharacteristic(
            uuid = HerbertProtocol.SUNRISE_TIME_UUID,
            data = time.encode(),
        )
    }

    fun setSunsetTime(hour: Int, minute: Int) {
        val time = DeviceTime(hour = hour, minute = minute)
        deviceStore.updateDevice(macAddress) { it.copy(sunsetTime = time) }
        writeCharacteristic(
            uuid = HerbertProtocol.SUNSET_TIME_UUID,
            data = time.encode(),
        )
    }

    fun createCameraCapture(): Pair<Uri, File> = photoStorage.createCameraCaptureUri()

    fun savePhotoFromGallery(uri: Uri) {
        viewModelScope.launch {
            val savedPath = photoStorage.savePhotoFromUri(
                macAddress = macAddress,
                sourceUri = uri,
                oldPhotoPath = uiState.value.device.photoPath,
            )
            if (savedPath != null) {
                deviceStore.updateDevice(macAddress) { it.copy(photoPath = savedPath) }
            } else {
                messageRes.value = R.string.photo_save_failed
            }
        }
    }

    fun savePhotoFromCamera(captureFile: File) {
        viewModelScope.launch {
            val savedPath = photoStorage.savePhotoFromCameraFile(
                macAddress = macAddress,
                captureFile = captureFile,
                oldPhotoPath = uiState.value.device.photoPath,
            )
            if (savedPath != null) {
                deviceStore.updateDevice(macAddress) { it.copy(photoPath = savedPath) }
            } else {
                messageRes.value = R.string.photo_save_failed
            }
        }
    }

    fun removePhoto() {
        val existingPath = uiState.value.device.photoPath
        photoStorage.deletePhoto(existingPath)
        deviceStore.updateDevice(macAddress) { it.copy(photoPath = null) }
    }

    fun clearMessage() {
        messageRes.value = null
    }

    private fun writeCharacteristic(uuid: java.util.UUID, data: ByteArray) {
        viewModelScope.launch {
            val ok = gattSession.writeCharacteristic(uuid, data)
            if (!ok) {
                messageRes.value = R.string.ble_write_failed
            }
        }
    }

    override fun onCleared() {
        gattSession.disconnect()
    }

    class Factory(
        private val application: Application,
        private val macAddress: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeviceDetailViewModel(application, macAddress) as T
        }
    }
}
