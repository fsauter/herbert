package de.fsauter.herbert

import android.app.Application
import de.fsauter.herbert.ble.BleProviderFactory
import de.fsauter.herbert.ble.HerbertBleProvider
import de.fsauter.herbert.data.PhotoStorage

class HerbertApplication : Application() {
    val photoStorage: PhotoStorage by lazy { PhotoStorage(this) }
    val bleProvider: HerbertBleProvider by lazy { BleProviderFactory.create(this) }
}
