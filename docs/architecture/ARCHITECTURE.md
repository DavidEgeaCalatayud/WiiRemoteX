# WiiRemoteX architecture

## Objective

WiiRemoteX is a hardware-emulation system with one Wii protocol engine and replaceable transports/frontends.

```text
Android frontend                         iOS frontend
Compose + Android sensors                SwiftUI + CoreMotion
        |                                        |
        +----------------+  +--------------------+
                         v  v
                   shared Wii core
        model -> protocol -> WiimoteSessionEngine
                         |
                  Wii HID reports
                         |
          +--------------+-------------------+
          |                                  |
  Android transport selector             iOS BLE
          |                                  |
     +----+---------+                        |
     |              |                        |
 Direct HID      ESP32 BLE                    |
     |              +------------+-----------+
     |                           |
BluetoothHidDevice              ESP32 bridge
     |                    BLE <-> Classic HID
     +----------------------+----+
                            |
                        Nintendo Wii
```

The Wii protocol core must never depend on `android.*`, Swift/UIKit or ESP-IDF.

## Source of truth

The Kotlin sources under:

- `:core:model`
- `:core:protocol`
- `:core:session`

remain the protocol source of truth. `:core:protocol` now also owns the versioned ESP32 bridge framing protocol so Android and iOS cannot silently diverge in packet limits, fragmentation or status/control semantics.

The `:shared` Kotlin Multiplatform module still compiles the core source directories for JVM and iOS. Its historical bridge API is now a compatibility facade over `:core:protocol`; it no longer contains a second framing implementation.

A later structural migration will convert `core:model`, `core:protocol` and `core:session` themselves into conventional Kotlin Multiplatform modules with `commonMain/commonTest`. That migration is intentionally separate from the transport hardware gate.

## Android paths

Android supports two runtime-selectable transports while keeping one `WiimoteSessionEngine`.

### Direct HID

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

This remains the shortest path and requires no external hardware.

### ESP32 fallback

```text
Compose / Android sensors
        |
WiimoteSessionEngine
        |
HidInputReport
        |
AndroidEsp32BleTransport
        |
common BridgeFrameCodec
        |
Android BLE GATT
        |
ESP32 BLE service
        |
ESP32 Bluetooth Classic HID
        |
Nintendo Wii
```

The fallback exists for Android devices whose vendor Bluetooth stack limits or breaks `BluetoothHidDevice` behaviour required by the Wii.

The Android runtime chooses exactly one transport:

```text
TransportMode
├── DIRECT_HID
└── ESP32_BRIDGE
```

Buttons, accelerometer, gyroscope, IR, Nunchuk and MotionPlus never branch on transport. They only mutate the shared session engine; `HidTransport.send()` is the replaceable boundary.

## iOS path

```text
SwiftUI / CoreMotion
        |
WiiRemoteShared.framework
        |
IosWiimoteEngine
        |
common bridge protocol facade
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
- forward Wii output reports back over confirmed BLE indications
- expose pairing/bond controls
- report bridge protocol version and Wii connection state

IR, Nunchuk, MotionPlus, EEPROM/register behaviour, report modes and Wii state stay in the shared engine.

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

Message types:

- `0x01 INPUT_REPORT`: phone -> ESP32 -> Wii
- `0x02 OUTPUT_REPORT`: Wii -> ESP32 -> phone
- `0x03 STATUS`: bridge/Wii lifecycle
- `0x04 CONTROL`: pairing/bond operations

The payload of INPUT_REPORT/OUTPUT_REPORT begins with the Wii HID report ID followed by its payload. Messages are capped at 256 bytes and use bounded reassembly.

`BRIDGE_READY` carries the bridge protocol version. Pairing controls should only be enabled after the phone confirms that this version matches `BridgeFrameCodec.VERSION`.

## Button A paths

Direct Android:

```text
touch down
 -> WiimoteSessionEngine.setButton(A, true)
 -> report 0x30 / payload 00 08
 -> Android Bluetooth HID
 -> Wii
```

Android via ESP32:

```text
touch down
 -> same WiimoteSessionEngine
 -> report 0x30 / payload 00 08
 -> AndroidEsp32BleTransport
 -> bridge INPUT_REPORT
 -> BLE
 -> ESP32
 -> Classic HID report 0x30 / payload 00 08
 -> Wii
```

iOS:

```text
touch down
 -> IosWiimoteEngine.buttonChanged("A", true)
 -> same WiimoteSessionEngine
 -> report 0x30 / payload 00 08
 -> bridge INPUT_REPORT
 -> CoreBluetooth
 -> ESP32
 -> Classic HID report 0x30 / payload 00 08
 -> Wii
```

## Host/output paths

Direct Android:

```text
Wii -> Android HID callback -> HostCommandDecoder -> WiimoteSessionEngine
```

Android/iOS via ESP32:

```text
Wii
 -> ESP32 ESP_HIDD_OUTPUT_EVENT
 -> bridge OUTPUT_REPORT
 -> confirmed BLE indication
 -> phone bridge transport
 -> HostCommandDecoder
 -> WiimoteSessionEngine
```

Responses generated by the session engine travel back through the inverse path.

## Transport implementations

```text
Wii report transport
  +-- AndroidHidTransport       implemented
  +-- AndroidEsp32BleTransport  implemented foundation
  +-- IosEsp32Transport         implemented foundation
  +-- Esp32ClassicHidTransport  implemented foundation
  +-- NativeBluezTransport      planned later / laboratory transport
```

`LinuxBluezTransport` is deliberately deferred because it is not required for the mobile V1.

## Target module layout

Current incremental direction:

```text
core/
├── model/
├── protocol/       # includes common bridge framing
└── session/

transports/
├── esp32-ble/      # Android BLE client
├── android-hid/    # future rename/move of platform:bluetooth
└── linux-bluez/    # later

frontends/
├── Android
└── iOS
```

After the physical transport gate, the core modules can move to conventional KMP source sets:

```text
core/model/src/commonMain
core/protocol/src/commonMain
core/session/src/commonMain
core/trace/src/commonMain
```

## Validation philosophy

Protocol correctness and transport correctness are separate gates. Unit tests prove state/report behaviour; Android CI proves both transport implementations compile into the app; KMP CI proves the shared engine compiles for iOS; ESP-IDF CI proves the bridge firmware builds; hardware captures prove actual Bluetooth behaviour.

A physical Wii remains the final compatibility authority.