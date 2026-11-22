package de.fsauter.herbert.ble

import kotlinx.serialization.Serializable
import java.time.LocalTime
import java.util.UUID

/**
 * BLE GATT UUIDs and binary codecs for communicating with Herbert garden hardware.
 *
 * Verified on hardware: `29780004-...` stores the sunrise time and `29780005-...` stores
 * the sunset time.
 */
object HerbertProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("29780001-35E9-11E8-B566-0800200C9A66")
    val DEVICE_NAME_UUID: UUID = UUID.fromString("00002A00-0000-1000-8000-00805F9B34FB")
    val LIGHT_MODE_UUID: UUID = UUID.fromString("29780002-35E9-11E8-B566-0800200C9A66")
    val BRIGHTNESS_UUID: UUID = UUID.fromString("29780003-35E9-11E8-B566-0800200C9A66")
    val SUNRISE_TIME_UUID: UUID = UUID.fromString("29780004-35E9-11E8-B566-0800200C9A66")
    val SUNSET_TIME_UUID: UUID = UUID.fromString("29780005-35E9-11E8-B566-0800200C9A66")
    val CURRENT_TIME_UUID: UUID = UUID.fromString("29780007-35E9-11E8-B566-0800200C9A66")
    val WATER_LEVEL_UUID: UUID = UUID.fromString("29780008-35E9-11E8-B566-0800200C9A66")

    const val DEFAULT_NAME = "Herbert"
    const val MAX_NAME_LENGTH = 15
    const val MAX_BRIGHTNESS = 100
    const val SCAN_DURATION_MS = 4_000L

    fun encodeBrightness(brightness: Int): ByteArray =
        byteArrayOf(brightness.coerceIn(0, MAX_BRIGHTNESS).toByte())

    fun decodeBrightness(data: ByteArray?): Int {
        if (data == null || data.isEmpty()) return 0
        return (data[0].toInt() and 0xFF).coerceIn(0, MAX_BRIGHTNESS)
    }

    fun encodeDeviceName(name: String): ByteArray =
        name.take(MAX_NAME_LENGTH).toByteArray(Charsets.UTF_8)

    fun decodeDeviceName(data: ByteArray?): String {
        if (data == null || data.isEmpty()) return DEFAULT_NAME
        return data.toString(Charsets.UTF_8)
            .trimEnd('\u0000')
            .ifBlank { DEFAULT_NAME }
    }
}

@Serializable
data class LightMode(
    val manualOn: Boolean = false,
    val auto: Boolean = false,
    val holiday: Boolean = false,
    val smoothChange: Boolean = false,
) {
    fun encode(): ByteArray {
        val bit0 = if (auto) holiday else manualOn
        var flags = 0
        if (bit0) flags = flags or 0x01
        if (auto) flags = flags or 0x02
        if (smoothChange) flags = flags or 0x04
        return byteArrayOf(flags.toByte())
    }

    companion object {
        fun decode(data: ByteArray?): LightMode {
            if (data == null || data.isEmpty()) return LightMode()
            val flags = data[0].toInt() and 0xFF
            val bit0 = (flags and 0x01) != 0
            val auto = (flags and 0x02) != 0
            val smooth = (flags and 0x04) != 0
            return LightMode(
                manualOn = if (!auto) bit0 else false,
                auto = auto,
                holiday = if (auto) bit0 else false,
                smoothChange = smooth,
            )
        }
    }
}

@Serializable
data class DeviceTime(
    val hour: Int = 0,
    val minute: Int = 0,
) {
    fun encode(): ByteArray = byteArrayOf(
        hour.coerceIn(0, 23).toByte(),
        minute.coerceIn(0, 59).toByte(),
    )

    companion object {
        fun now(): DeviceTime {
            val now = LocalTime.now()
            return DeviceTime(hour = now.hour, minute = now.minute)
        }

        fun decode(data: ByteArray?): DeviceTime {
            if (data == null || data.size < 2) return DeviceTime()
            return DeviceTime(
                hour = (data[0].toInt() and 0xFF).coerceIn(0, 23),
                minute = (data[1].toInt() and 0xFF).coerceIn(0, 59),
            )
        }
    }
}

@Serializable
enum class WaterLevel(val rawValue: Int) {
    UNKNOWN(0),
    EMPTY(1),
    LOW(2),
    OK(3),
    FULL(4);

    val isWarning: Boolean
        get() = this == EMPTY || this == LOW

    companion object {
        fun decode(data: ByteArray?): WaterLevel {
            if (data == null || data.isEmpty()) return UNKNOWN
            val level = data[0].toInt() and 0xFF
            return entries.firstOrNull { it.rawValue == level } ?: UNKNOWN
        }
    }
}
