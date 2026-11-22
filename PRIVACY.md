# Privacy Policy — Remote Control for Herbert (`Herbert Remote`)

**Effective date:** November 22, 2026  
**Package ID:** `de.fsauter.herbert`

**Remote Control for Herbert** (launcher name: **Herbert Remote**) is an unofficial, open-source Android app for controlling Ponix Herbert indoor gardens locally over Bluetooth Low Energy (BLE). Your privacy is respected by design: **this app does not collect, store, transmit, or share any personal or usage data.**

## 1. Data Collection & Sharing

- **No network access:** The app does not request Android's `INTERNET` permission and cannot send any data off your device.
- **No analytics or tracking:** No crash-reporting SDKs, advertising SDKs, or telemetry services are included.
- **No accounts:** No sign-in, registration, or cloud sync is required or offered.

## 2. Permissions Used & Why

- **Bluetooth (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` on Android 12+):** Used strictly to discover nearby Herbert gardens and read/update their settings (light mode, brightness, sunrise/sunset schedule, water level, and device name) directly over BLE. `BLUETOOTH_SCAN` uses `neverForLocation`.
- **Location (`ACCESS_FINE_LOCATION` on Android 8–11 only):** Older Android versions require this OS permission to perform BLE scans. The app **never** reads GPS coordinates or accesses, stores, or transmits your physical location.
- **Camera / Photo Picker:** No `CAMERA` or storage permissions are requested. If you choose to set a custom photo for a garden, Android's system Photo Picker or Camera intent returns only the photo you explicitly select or take.

## 3. On-Device Local Storage

All app state stays locally in private app storage on your device:

- `herbert_devices.json` stores discovered/saved Herbert MAC addresses, names, and last synced light/water settings.
- Custom device photos you choose are copied into private app storage (`files/photos/`).

You can delete custom photos anytime via **Change photo → Remove photo** in the app, or remove all stored data by clearing app storage or uninstalling the app. Standard Android system backup (`allowBackup`) is enabled and governed by your device's Android backup settings.

## 4. Children's Privacy

Because no data is collected from any user, no personal data is collected from children.

## 5. Changes to This Policy

Updates to this policy will be published in this repository with an updated effective date.

## 6. Contact

If you have questions about this privacy policy or the app, please reach out via:

- **Email:** [herbert-app.banister656@slmail.me](mailto:herbert-app.banister656@slmail.me)
- **GitHub Issues:** [https://github.com/fsauter/herbert/issues](https://github.com/fsauter/herbert/issues)
