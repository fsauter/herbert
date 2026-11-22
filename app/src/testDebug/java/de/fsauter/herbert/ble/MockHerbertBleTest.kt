package de.fsauter.herbert.ble

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockHerbertBleTest {

    @Test
    fun `mock scanner discovers both simulated gardens and completes`() = runTest {
        val registry = MockHerbertRegistry()
        val scanner = MockHerbertScanner(registry, stepDelayMs = 10L)

        assertFalse(scanner.requiresRuntimePermissions)
        assertTrue(scanner.isBluetoothEnabled())

        val events = scanner.scan(durationMs = 100L).toList()
        val found = events.filterIsInstance<BleScanEvent.DeviceFound>().map { it.device }
        assertEquals(2, found.size)
        assertEquals("Kitchen Herbs", found[0].advertisedName)
        assertEquals("Living Room", found[1].advertisedName)
        assertTrue(events.last() is BleScanEvent.Completed)
    }

    @Test
    fun `mock session reads initial state and persists characteristic writes`() = runTest {
        val registry = MockHerbertRegistry()
        val session = MockHerbertSession(
            macAddress = MockHerbertRegistry.MAC_LIVING_ROOM,
            registry = registry,
            operationDelayMs = 10L,
        )

        assertTrue(session.connect())
        assertEquals(BleConnectionState.CONNECTED, session.connectionState.value)

        val initial = session.syncAll()
        assertNotNull(initial)
        assertEquals("Living Room", initial!!.name)
        assertEquals(45, initial.brightness)
        assertEquals(WaterLevel.LOW, initial.waterLevel)
        assertTrue(initial.lightMode.manualOn)

        assertTrue(
            session.writeCharacteristic(
                HerbertProtocol.DEVICE_NAME_UUID,
                HerbertProtocol.encodeDeviceName("Basil Shelf"),
            ),
        )
        assertTrue(
            session.writeCharacteristic(
                HerbertProtocol.BRIGHTNESS_UUID,
                HerbertProtocol.encodeBrightness(90),
            ),
        )
        val newMode = LightMode(auto = true, smoothChange = true, holiday = true)
        assertTrue(
            session.writeCharacteristic(HerbertProtocol.LIGHT_MODE_UUID, newMode.encode()),
        )
        assertTrue(
            session.writeCharacteristic(
                HerbertProtocol.SUNRISE_TIME_UUID,
                DeviceTime(6, 15).encode(),
            ),
        )
        assertTrue(
            session.writeCharacteristic(
                HerbertProtocol.SUNSET_TIME_UUID,
                DeviceTime(22, 0).encode(),
            ),
        )
        session.disconnect()
        assertEquals(BleConnectionState.DISCONNECTED, session.connectionState.value)

        val secondSession = MockHerbertSession(
            macAddress = MockHerbertRegistry.MAC_LIVING_ROOM,
            registry = registry,
            operationDelayMs = 10L,
        )
        assertTrue(secondSession.connect())
        val updated = secondSession.syncAll()!!
        assertEquals("Basil Shelf", updated.name)
        assertEquals(90, updated.brightness)
        assertEquals(newMode, updated.lightMode)
        assertEquals(DeviceTime(6, 15), updated.sunriseTime)
        assertEquals(DeviceTime(22, 0), updated.sunsetTime)
    }
}
