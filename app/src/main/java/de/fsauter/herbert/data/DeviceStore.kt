package de.fsauter.herbert.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Lightweight local JSON persistence for paired/discovered Herbert gardens.
 */
class DeviceStore(private val storageFile: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val lock = Any()
    private val _devices = MutableStateFlow(readFromDisk())
    val devices: StateFlow<List<SavedHerbertDevice>> = _devices.asStateFlow()

    fun getDevice(macAddress: String): SavedHerbertDevice? =
        _devices.value.firstOrNull { it.macAddress.equals(macAddress, ignoreCase = true) }

    fun upsertDiscovered(macAddress: String, advertisedName: String?): SavedHerbertDevice =
        updateList { current ->
            val index = current.indexOfFirst { it.macAddress.equals(macAddress, ignoreCase = true) }
            if (index >= 0) {
                current
            } else {
                val initialName = advertisedName
                    ?.takeIf { it.isNotBlank() }
                    ?.take(de.fsauter.herbert.ble.HerbertProtocol.MAX_NAME_LENGTH)
                    ?: de.fsauter.herbert.ble.HerbertProtocol.DEFAULT_NAME
                current + SavedHerbertDevice(macAddress = macAddress, name = initialName)
            }
        }.first { it.macAddress.equals(macAddress, ignoreCase = true) }

    fun updateDevice(
        macAddress: String,
        transform: (SavedHerbertDevice) -> SavedHerbertDevice,
    ): SavedHerbertDevice = updateList { current ->
        val index = current.indexOfFirst { it.macAddress.equals(macAddress, ignoreCase = true) }
        if (index >= 0) {
            current.toMutableList().apply { this[index] = transform(this[index]) }
        } else {
            current + transform(SavedHerbertDevice(macAddress = macAddress))
        }
    }.first { it.macAddress.equals(macAddress, ignoreCase = true) }

    private fun updateList(
        transform: (List<SavedHerbertDevice>) -> List<SavedHerbertDevice>,
    ): List<SavedHerbertDevice> {
        synchronized(lock) {
            val updated = transform(_devices.value)
            writeToDisk(updated)
            _devices.value = updated
            return updated
        }
    }

    private fun readFromDisk(): List<SavedHerbertDevice> {
        if (!storageFile.exists()) return emptyList()
        return try {
            val raw = storageFile.readText(Charsets.UTF_8)
            if (raw.isBlank()) emptyList() else json.decodeFromString<List<SavedHerbertDevice>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeToDisk(list: List<SavedHerbertDevice>) {
        storageFile.parentFile?.mkdirs()
        val tempFile = File(storageFile.parentFile, "${storageFile.name}.tmp")
        tempFile.writeText(json.encodeToString(list), Charsets.UTF_8)
        if (!tempFile.renameTo(storageFile)) {
            tempFile.copyTo(storageFile, overwrite = true)
            tempFile.delete()
        }
    }

    companion object {
        fun forContext(context: Context): DeviceStore =
            DeviceStore(File(context.applicationContext.filesDir, "herbert_devices.json"))
    }
}
