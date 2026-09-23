# Android → ESP32 fallback validation

This checklist validates the alternate Android transport without changing the Wii protocol/session engine.

```text
Android
  WiiRemoteX / WiimoteSessionEngine
        |
        | BLE
        v
ESP32 WiiRemoteX Bridge
        |
        | Bluetooth Classic HID
        v
Nintendo Wii
```

## Prerequisites

- Android 9+ phone with BLE.
- Original dual-mode ESP32 (ESP32-WROOM / ESP32-DevKitC class board), not C3/C6/S3.
- WiiRemoteX ESP32 firmware flashed from `firmware/esp32`.
- Physical Nintendo Wii.
- Android Bluetooth enabled.
- Android 12+: grant Nearby devices / Bluetooth scan + connect permissions.
- Android 11 and below: grant location permission required by BLE scanning APIs.

## Gate A — Android ↔ ESP32

1. Open WiiRemoteX on Android.
2. Select **ESP32 Bridge**.
3. Start the transport.
4. Expected diagnostics:
   - scanning for WiiRemoteX ESP32 bridge
   - bridge discovered
   - BLE connected
   - GATT service discovered
   - indication subscription enabled
   - `BRIDGE_READY` received
5. Expected UI: bridge protocol `Ready v1`.

Failure classification:

- no scan result → advertising/service UUID/permission problem
- connects but service missing → firmware GATT mismatch
- CCC write fails → indication subscription/GATT compatibility problem
- no BRIDGE_READY → ESP32 notification/indication lifecycle problem
- incompatible version → phone/firmware protocol version mismatch

## Gate B — ESP32 ↔ Wii pairing

1. Tap **Pair Wii** in Android.
2. Start Wii red-SYNC controller pairing.
3. Expected Android state: `CONNECTING`.
4. Expected ESP32 serial trace:
   - limited discoverable enabled
   - Wii host PIN request
   - six-byte reversed binary host-BD-address PIN reply
   - authentication success
   - HID host connected
5. Expected Android state: `CONNECTED`.

If pairing fails, capture ESP32 serial logs before changing protocol code. Classify the failure as discovery, SDP/device identity, authentication/PIN, bond persistence, or HID channel establishment.

## Gate C — first Wii report exchange

After `CONNECTED`:

1. Capture the first Wii output report forwarded as bridge `OUTPUT_REPORT`.
2. Verify Android diagnostics show the same report ID/payload.
3. Confirm `WiimoteSessionEngine.onHostReport()` processes it.
4. Verify generated responses return as bridge `INPUT_REPORT` messages.

Expected common commands include status/report-mode/register traffic such as `0x12`, `0x15`, `0x16` and `0x17` depending on the Wii state/application.

## Gate D — A button

1. Press **A** in the Android WiiRemoteX UI.
2. Expected session report: report ID `0x30`, payload `00 08`.
3. Expected path:

```text
Android UI
 -> WiimoteSessionEngine
 -> AndroidEsp32BleTransport
 -> INPUT_REPORT bridge frame
 -> ESP32
 -> Classic HID
 -> Wii
```

4. A should navigate the physical Wii Menu.
5. Release A.
6. Expected payload: `00 00`.

This proves the fallback end-to-end transport independently of Android `BluetoothHidDevice`.

## Gate E — continuous traffic

Once core buttons work:

- switch Wii to a continuous data report mode
- verify 100 Hz scheduler traffic remains ordered
- watch Android BLE TX queue depth/errors
- verify no fragmented output command is lost
- exercise accelerometer + Motion Pointer
- test Nunchuk
- test MotionPlus
- test MotionPlus + Nunchuk pass-through

## Gate F — resilience

Test separately:

- disconnect/reconnect Android BLE while Wii stays connected
- power-cycle ESP32
- power-cycle Wii
- reconnect with an existing Wii bond
- clear Wii bond from Android and pair again
- background/foreground Android app
- lock/unlock phone
- Bluetooth off/on

Always export the WiiRemoteX hardware trace and ESP32 serial log for a failed case before changing implementation details.
