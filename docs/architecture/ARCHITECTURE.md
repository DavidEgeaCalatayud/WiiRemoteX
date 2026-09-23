# WiiRemoteX architecture

## Objective

WiiRemoteX is a hardware-emulation system with **one Wii Remote state/protocol engine** and replaceable transports.

```text
Android frontend                         iOS frontend
Compose + Android sensors                SwiftUI + CoreMotion
        |                                        |
        +----------------+  +--------------------+
                         v  v
              Kotlin Multiplatform core
        model -> protocol -> WiimoteSessionEngine
                         |
                  Wii HID reports
                         |
           +-------------+----------------------+
           |                                    |
     Android direct                         ESP32 bridge
 AndroidHidTransport                Android BLE / iPhone BLE
 BluetoothHidDevice                           |
           |                          Bluetooth Classic HID
           +------------------+-----------------+
                              |
                         Nintendo Wii
```

The protocol core has no dependency on `android.*`, Swift/UIKit/CoreBluetooth, or ESP-IDF.

## Real Kotlin Multiplatform core

The protocol source of truth is physically organized as multiplatform source sets:

```text
core/
├── model/
│   └── src/commonMain
├── protocol/
│   ├── src/commonMain
│   └── src/commonTest
├── session/
│   ├── src/commonMain
│   └── src/commonTest
└── trace/
    └── src/commonMain
```

Each core module declares JVM and iOS targets. `:shared` no longer compiles another module's source directories by path; it depends on the core modules normally and only supplies the Swift-friendly facade.

```text
shared
  ├── depends on core:model
  ├── depends on core:protocol
  └── depends on core:session
```

This guarantees that Android and iOS execute the same `WiimoteSessionEngine`, report encoders, register/EEPROM implementation, Nunchuk/MotionPlus logic and bridge framing.

## Transport contract

The session layer emits `HidInputReport` through:

```kotlin
fun interface HidTransport {
    fun send(report: HidInputReport): Boolean
}
```

Android selects one implementation at runtime:

```text
TransportMode
├── DIRECT_ANDROID_HID
│      └── AndroidHidTransport
│             └── BluetoothHidDevice
│                    └── Wii
└── ESP32_BRIDGE
       └── AndroidEsp32BleTransport
              └── BLE
                    └── ESP32
                          └── Bluetooth Classic HID
                                └── Wii
```

Changing transport does not recreate or replace the Wii protocol implementation.

## Android direct path

```text
Compose / Android sensors
        |
WiimoteSessionEngine
        |
HidInputReport
        |
AndroidHidTransport
        |
BluetoothHidDevice
        |
Nintendo Wii
```

This is the shortest path and remains the preferred option when the Android Bluetooth stack is compatible with the Wii.

## Android ESP32 fallback

```text
Compose / Android sensors
        |
WiimoteSessionEngine
        |
HidInputReport
        |
BridgeFrameCodec
        |
AndroidEsp32BleTransport
        |
BLE GATT
        |
ESP32
        |
Bluetooth Classic HID
        |
Nintendo Wii
```

The fallback exists for devices/vendors where Android's public `BluetoothHidDevice` API cannot reproduce enough of the Wii Remote Bluetooth identity/handshake.

The Android BLE transport:

- scans by the WiiRemoteX service UUID
- discovers the two bridge characteristics
- enables GATT indications
- keeps an ordered TX queue with bounded retries
- fragments/reassembles with the same `BridgeFrameCodec` used by iOS
- forwards Wii `OUTPUT_REPORT` traffic into the existing `WiimoteSessionEngine`
- exposes bridge protocol version, firmware version, errors and Wii connection state

## iOS path

```text
SwiftUI / CoreMotion
        |
WiiRemoteShared.framework
        |
IosWiimoteEngine
        |
BridgeFrameCodec
        |
CoreBluetooth
        |
ESP32 BLE GATT service
        |
ESP32 Bluetooth Classic HID
        |
Nintendo Wii
```

The ESP32 is deliberately thin. It performs transport duties only:

- reassemble BLE frames from Android/iPhone
- forward Wii input reports over Classic HID
- forward Wii output reports back using confirmed GATT indications
- expose pairing/bond controls
- expose protocol + firmware version
- report Wii connection state

IR, Nunchuk, MotionPlus, EEPROM/register behavior, report modes and Wii state remain in the phone-side shared engine.

## Bridge protocol

Every BLE packet is at most 20 bytes:

```text
byte 0  version
byte 1  message type
byte 2  sequence low
byte 3  sequence high
byte 4  fragment index
byte 5  fragment count
byte 6+ payload (max 14 bytes)
```

Messages are capped at 256 bytes and use a bounded reassembler.

Message types:

- `0x01 INPUT_REPORT`: phone -> ESP32 -> Wii
- `0x02 OUTPUT_REPORT`: Wii -> ESP32 -> phone
- `0x03 STATUS`: bridge/Wii lifecycle
- `0x04 CONTROL`: pairing/bond operations

`BRIDGE_READY` currently contains:

```text
status_code
protocol_version
firmware_major
firmware_minor
firmware_patch
```

The payload of `INPUT_REPORT` / `OUTPUT_REPORT` begins with the Wii HID report ID followed by its payload.

## Button A paths

Direct Android:

```text
touch down
 -> WiimoteSessionEngine.setButton(A, true)
 -> report 0x30 / payload 00 08
 -> AndroidHidTransport
 -> Wii
```

Android fallback:

```text
touch down
 -> same WiimoteSessionEngine
 -> report 0x30 / payload 00 08
 -> BridgeFrameCodec
 -> Android BLE
 -> ESP32
 -> Classic HID
 -> Wii
```

iOS:

```text
touch down
 -> IosWiimoteEngine.buttonChanged("A", true)
 -> same WiimoteSessionEngine
 -> report 0x30 / payload 00 08
 -> BridgeFrameCodec
 -> CoreBluetooth
 -> ESP32
 -> Classic HID
 -> Wii
```

## Host/output path

Direct Android:

```text
Wii -> Android HID callback -> HostCommandDecoder -> WiimoteSessionEngine
```

Android/iOS bridge:

```text
Wii
 -> ESP32 ESP_HIDD_OUTPUT_EVENT
 -> bridge OUTPUT_REPORT
 -> GATT indication
 -> phone BridgeFrameReassembler
 -> HostCommandDecoder
 -> WiimoteSessionEngine
```

Responses generated by the session engine travel back through the inverse transport.

## Transport modules

```text
transports/
├── android-hid/   implemented
├── esp32-ble/     implemented for Android
└── linux-bluez/   deferred
```

The native iOS CoreBluetooth adapter currently remains inside `iosApp`, while the protocol/framing it uses is in `:core:protocol`.

## Validation philosophy

Protocol correctness and transport correctness are independent gates:

- common/JVM tests validate reports and state transitions
- Kotlin/Native CI validates the same core for iOS
- Android CI validates both Android transports compile into the app
- ESP-IDF CI validates the bridge and publishes flashable firmware artifacts
- physical-Wii traces remain the final Bluetooth/game compatibility authority

`LinuxBluezTransport` is intentionally deferred until after the mobile hardware gate.
