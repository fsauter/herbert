package de.fsauter.herbert.data

import de.fsauter.herbert.ble.DeviceTime
import de.fsauter.herbert.ble.HerbertProtocol
import de.fsauter.herbert.ble.LightMode
import de.fsauter.herbert.ble.WaterLevel
import kotlinx.serialization.Serializable

@Serializable
data class SavedHerbertDevice(
    val macAddress: String,
    val name: String = HerbertProtocol.DEFAULT_NAME,
    val photoPath: String? = null,
    val brightness: Int = 0,
    val lightMode: LightMode = LightMode(),
    val sunriseTime: DeviceTime = DeviceTime(hour = 7, minute = 0),
    val sunsetTime: DeviceTime = DeviceTime(hour = 21, minute = 0),
    val waterLevel: WaterLevel = WaterLevel.UNKNOWN,
)
