# Remote Control for Herbert (`Herbert Remote`)

Unofficial, open-source Android app for the **Ponix Herbert** vertical hydroponic smart garden — built with modern Kotlin and Jetpack Compose for Android 12–17+.

> **Disclaimer:** This project is an independent, open-source reimplementation and is not affiliated with, endorsed by, or supported by Ponix Systems. No original Ponix source code or proprietary artwork is included.

## What Changed vs. the Original App

- **Modern Android compatibility (`minSdk 26`, `targetSdk 37`):** Native Android BLE permissions (`BLUETOOTH_SCAN` with `neverForLocation`, `BLUETOOTH_CONNECT`, and `ACCESS_FINE_LOCATION` on Android 8–11), Scoped Storage safe photo picker (`PickVisualMedia` + `FileProvider`), predictive back support, and edge-to-edge Jetpack Compose UI.
- **100% offline & privacy-first:** Zero `INTERNET` permission, zero analytics/Crashlytics, zero external servers. All device data and custom device photos stay locally on your phone.
- **Removed dead backends:** Both the discontinued remote firmware update (DFU) flow (`s.vivefy.com`) and the offline web-based Plants tab (`ponix-systems.at`) have been removed. Your garden's existing firmware continues to work over Bluetooth LE.

## Features

- Pull-to-refresh Bluetooth LE discovery filtered to Herbert gardens
- Persistent local device list with custom device photos (camera or gallery) and device renaming (up to 15 characters)
- Live water-level status indicator (`Empty`, `Low`, `OK`, `Full`)
- LED brightness control (`0–100%`)
- Lighting modes:
  - **Schedule timer** with **Gradual fade**, **Sunrise** & **Sunset** 24-hour schedules, and **Vacation mode**
  - **Manual light** toggle
- Automatic local clock sync on connect
- English and German (`de`) localization

## BLE Protocol

Primary GATT service UUID: `29780001-35E9-11E8-B566-0800200C9A66`

| Characteristic | UUID | Access | Payload |
| :--- | :--- | :--- | :--- |
| Device Name (GAP) | `00002A00-0000-1000-8000-00805F9B34FB` | R/W | UTF-8 string (up to 15 chars) |
| Light Mode | `29780002-35E9-11E8-B566-0800200C9A66` | R/W | 1 byte bitfield: bit 0 = `holiday` (if `auto=1`) or `manualOn` (if `auto=0`), bit 1 = `auto`, bit 2 = `smoothChange` |
| Brightness | `29780003-35E9-11E8-B566-0800200C9A66` | R/W | 1 unsigned byte (`0–100`) |
| Sunrise Time | `29780004-35E9-11E8-B566-0800200C9A66` | R/W | 2 bytes: `[hour, minute]` |
| Sunset Time | `29780005-35E9-11E8-B566-0800200C9A66` | R/W | 2 bytes: `[hour, minute]` |
| Current Time | `29780007-35E9-11E8-B566-0800200C9A66` | W | 2 bytes: `[hour, minute]` synced on connect |
| Water Level | `29780008-35E9-11E8-B566-0800200C9A66` | R | 1 unsigned byte (`1`=Empty, `2`=Low, `3`=OK, `4`=Full) |

## Building & Testing on Your Phone

1. Open this folder in **Android Studio** (or build from the command line with `./gradlew assembleDebug`).
2. Enable **Developer options** and **USB debugging** on a physical Android phone (the Android Emulator does not emulate real Bluetooth LE hardware).
3. Connect the phone via USB, select it in Android Studio, and click **Run**.

### Command-line checks

```zsh
./gradlew testDebugUnitTest assembleDebug lintDebug
```

## Building a Signed Play Store `.aab`

1. Generate an upload keystore outside the repository (e.g. `~/keystores/herbert-upload.jks`).
2. Copy [`keystore.properties.example`](keystore.properties.example) to `keystore.properties` (gitignored) and fill in your keystore path, alias, and passwords (or export `HERBERT_KEYSTORE_PATH`, `HERBERT_KEYSTORE_PASSWORD`, `HERBERT_KEY_ALIAS`, and `HERBERT_KEY_PASSWORD`).
3. Build the signed release App Bundle:

```zsh
./gradlew bundleRelease
```

The bundle is written to `app/build/outputs/bundle/release/app-release.aab`.

## Privacy Policy (Play Store)

See [PRIVACY.md](PRIVACY.md). Remote Control for Herbert does not request `INTERNET` permission, does not collect or transmit any personal or telemetry data, and communicates solely via local Bluetooth LE with nearby Herbert hardware. Any custom photo or device name you assign is stored only in private app storage on your device.

## License

[MIT](LICENSE)
