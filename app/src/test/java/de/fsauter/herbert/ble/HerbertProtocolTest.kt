package de.fsauter.herbert.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class HerbertProtocolTest {

    @Test
    fun `sunrise and sunset UUIDs match original UI bindings`() {
        assertEquals(
            UUID.fromString("29780001-35E9-11E8-B566-0800200C9A66"),
            HerbertProtocol.SERVICE_UUID,
        )
        assertEquals(
            UUID.fromString("29780004-35E9-11E8-B566-0800200C9A66"),
            HerbertProtocol.SUNRISE_TIME_UUID,
        )
        assertEquals(
            UUID.fromString("29780005-35E9-11E8-B566-0800200C9A66"),
            HerbertProtocol.SUNSET_TIME_UUID,
        )
        assertEquals(
            UUID.fromString("29780007-35E9-11E8-B566-0800200C9A66"),
            HerbertProtocol.CURRENT_TIME_UUID,
        )
        assertEquals(
            UUID.fromString("29780008-35E9-11E8-B566-0800200C9A66"),
            HerbertProtocol.WATER_LEVEL_UUID,
        )
    }

    @Test
    fun `light mode encodes and decodes manual and auto bitfields accurately`() {
        val allOff = LightMode(manualOn = false, auto = false, holiday = false, smoothChange = false)
        assertArrayEquals(byteArrayOf(0x00), allOff.encode())
        assertEquals(allOff, LightMode.decode(byteArrayOf(0x00)))

        val manualOn = LightMode(manualOn = true, auto = false, holiday = false, smoothChange = false)
        assertArrayEquals(byteArrayOf(0x01), manualOn.encode())
        assertEquals(manualOn, LightMode.decode(byteArrayOf(0x01)))

        val autoOnly = LightMode(manualOn = false, auto = true, holiday = false, smoothChange = false)
        assertArrayEquals(byteArrayOf(0x02), autoOnly.encode())
        assertEquals(autoOnly, LightMode.decode(byteArrayOf(0x02)))

        val autoHolidaySmooth = LightMode(manualOn = false, auto = true, holiday = true, smoothChange = true)
        assertArrayEquals(byteArrayOf(0x07), autoHolidaySmooth.encode())
        assertEquals(autoHolidaySmooth, LightMode.decode(byteArrayOf(0x07)))

        val autoSmooth = LightMode(manualOn = false, auto = true, holiday = false, smoothChange = true)
        assertArrayEquals(byteArrayOf(0x06), autoSmooth.encode())
        assertEquals(autoSmooth, LightMode.decode(byteArrayOf(0x06)))
    }

    @Test
    fun `brightness clamps and handles unsigned values`() {
        assertArrayEquals(byteArrayOf(0), HerbertProtocol.encodeBrightness(-10))
        assertArrayEquals(byteArrayOf(75), HerbertProtocol.encodeBrightness(75))
        assertArrayEquals(byteArrayOf(100), HerbertProtocol.encodeBrightness(250))

        assertEquals(0, HerbertProtocol.decodeBrightness(null))
        assertEquals(65, HerbertProtocol.decodeBrightness(byteArrayOf(65)))
        assertEquals(100, HerbertProtocol.decodeBrightness(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun `device time encodes and decodes hours and minutes`() {
        val time = DeviceTime(hour = 19, minute = 45)
        val encoded = time.encode()
        assertArrayEquals(byteArrayOf(19, 45), encoded)
        assertEquals(time, DeviceTime.decode(encoded))
        assertEquals(DeviceTime(0, 0), DeviceTime.decode(byteArrayOf(5)))
    }

    @Test
    fun `water level decodes levels and warning state`() {
        assertEquals(WaterLevel.UNKNOWN, WaterLevel.decode(byteArrayOf(0)))
        assertEquals(WaterLevel.EMPTY, WaterLevel.decode(byteArrayOf(1)))
        assertEquals(WaterLevel.LOW, WaterLevel.decode(byteArrayOf(2)))
        assertEquals(WaterLevel.OK, WaterLevel.decode(byteArrayOf(3)))
        assertEquals(WaterLevel.FULL, WaterLevel.decode(byteArrayOf(4)))
        assertEquals(WaterLevel.UNKNOWN, WaterLevel.decode(byteArrayOf(99)))

        assertTrue(WaterLevel.EMPTY.isWarning)
        assertTrue(WaterLevel.LOW.isWarning)
        assertFalse(WaterLevel.OK.isWarning)
        assertFalse(WaterLevel.FULL.isWarning)
    }

    @Test
    fun `device name trims to 15 chars and falls back when empty`() {
        val longName = "KitchenHerbTower123"
        val encoded = HerbertProtocol.encodeDeviceName(longName)
        assertEquals("KitchenHerbTowe", HerbertProtocol.decodeDeviceName(encoded))
        assertEquals("Herbert", HerbertProtocol.decodeDeviceName(byteArrayOf()))
        assertEquals("Herbert", HerbertProtocol.decodeDeviceName(byteArrayOf(0, 0)))
    }
}
