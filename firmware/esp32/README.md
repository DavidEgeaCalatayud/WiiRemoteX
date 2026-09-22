# WiiRemoteX ESP32 bridge

This firmware is the transport layer for the iOS version of WiiRemoteX.

```text
iPhone
  SwiftUI + CoreMotion
  WiiRemoteShared / WiimoteSessionEngine
        |
        | BLE GATT (WiiRemoteX bridge protocol)
        v
ESP32
  custom BLE GATT server
  bridge framing/reassembly
  Bluetooth Classic HID device
        |
        v
Nintendo Wii
```

The ESP32 does **not** implement Wii Remote state, IR, Nunchuk or MotionPlus logic. Those remain in the shared Kotlin engine. The firmware forwards input reports to the Wii and forwards Wii output reports back to the phone.

The phone-to-bridge path uses ordered CoreBluetooth writes. The bridge-to-phone path uses queued **GATT indications** so host commands and bridge status messages are confirmed before the next fragment is transmitted.

## Required hardware

Use an **original ESP32 with Bluetooth Classic + BLE dual-mode support**, for example an ESP32-DevKitC / ESP32-WROOM based board.

Do not buy an ESP32-S3/C3/C6 board for this bridge: the Wii-facing side requires Bluetooth Classic HID.

## BLE service

The iPhone discovers:

- Service: `7C0A0001-6F4B-4A42-9D47-575258000001`
- Phone -> bridge: `7C0A0002-6F4B-4A42-9D47-575258000001`
- Bridge -> phone: `7C0A0003-6F4B-4A42-9D47-575258000001`

Packets are capped at 20 bytes. The shared bridge protocol fragments larger Wii messages using a six-byte header. Once iOS subscribes to the bridge-to-phone characteristic, the ESP32 sends a `BRIDGE_READY` status containing the protocol version before Wii pairing is enabled in the UI:

```text
version
message_type
sequence_lo
sequence_hi
fragment_index
fragment_count
payload...
```

This makes the protocol independent of BLE MTU negotiation.

## Build

Install ESP-IDF, then:

```bash
cd firmware/esp32
idf.py set-target esp32
idf.py build
```

Flash:

```bash
idf.py -p <SERIAL_PORT> flash monitor
```

## First validation

1. Power the ESP32.
2. Open WiiRemoteX on iPhone.
3. Tap **Connect ESP32**.
4. Confirm `iPhone ↔ ESP32 = Connected` and `Bridge protocol = Ready v1`.
5. Tap **Pair Wii**.
6. Start Wii controller synchronization.
7. Watch serial logs for the Classic HID connection.
8. Press **A** in the iOS UI.
9. Expected shared-engine report: `0x30 00 08`.
10. Release A: `0x30 00 00`.

The Wii hardware gate is still experimental. SDP/Class-of-Device/discovery details may need to be changed after comparing a real RVL-CNT-01 against the bridge with `btmon`.
