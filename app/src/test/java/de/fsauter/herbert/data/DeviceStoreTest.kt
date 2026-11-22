package de.fsauter.herbert.data

import de.fsauter.herbert.ble.DeviceTime
import de.fsauter.herbert.ble.LightMode
import de.fsauter.herbert.ble.WaterLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DeviceStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `upsert and update persist across store instances`() {
        val file = File(tempFolder.root, "devices.json")
        val store = DeviceStore(file)

        store.upsertDiscovered("AA:BB:CC:DD:EE:FF", "Balcony")
        store.updateDevice("aa:bb:cc:dd:ee:ff") {
            it.copy(
                brightness = 80,
                lightMode = LightMode(auto = true, smoothChange = true),
                sunriseTime = DeviceTime(6, 30),
                sunsetTime = DeviceTime(22, 15),
                waterLevel = WaterLevel.LOW,
            )
        }

        val reloaded = DeviceStore(file)
        val saved = reloaded.getDevice("AA:BB:CC:DD:EE:FF")!!
        assertEquals("Balcony", saved.name)
        assertEquals(80, saved.brightness)
        assertEquals(LightMode(auto = true, smoothChange = true), saved.lightMode)
        assertEquals(DeviceTime(6, 30), saved.sunriseTime)
        assertEquals(DeviceTime(22, 15), saved.sunsetTime)
        assertEquals(WaterLevel.LOW, saved.waterLevel)
    }

    @Test
    fun `corrupt json file recovers safely to empty list`() {
        val file = tempFolder.newFile("corrupt.json")
        file.writeText("{not valid json")

        val store = DeviceStore(file)
        assertEquals(0, store.devices.value.size)
        assertNull(store.getDevice("AA:BB:CC:DD:EE:FF"))
    }
}
