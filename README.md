# WiiRemoteX

**WiiRemoteX** is an experimental multiplatform Wii Remote emulation project. One protocol/session engine can drive a **real Nintendo Wii** through replaceable transports: Android can use Bluetooth HID directly or fall back to an ESP32 bridge, while iPhone uses that same ESP32 BLE bridge.

> Status: **0.5.x hardware-validation + multiplatform foundation**. Android, the shared Kotlin engine, iOS frontend and ESP32 bridge are developed independently around the same Wii protocol model.

## Goal

```text
                       WiimoteSessionEngine
                               │
                        Wii HID reports
                               │
             ┌─────────────────┼─────────────────┐
             │                 │                 │
      Android Direct      Android BLE        iPhone BLE
       HID transport      fallback              │
             │                 │                │
             │                 └──────┬─────────┘
             │                        │
             │                      ESP32
             │               Bluetooth Classic HID
             └─────────────────┬──────┘
                               ▼
                         Nintendo Wii
```

The Wii protocol is intentionally transport-independent. Buttons, sensors, IR, Nunchuk and MotionPlus always update the same `WiimoteSessionEngine`; only the final delivery layer changes.

On Android the user can choose:

```text
Transport
├── Direct HID       Android BluetoothHidDevice → Wii
└── ESP32 Bridge     Android BLE → ESP32 → Bluetooth Classic HID → Wii
```

The ESP32 fallback is useful when an Android vendor or Bluetooth stack cannot expose enough HID-device behaviour for a physical Wii.

## Milestone 0

The PoC wires controller and sensor events through the shared session engine into a selectable transport, exposes registration/connection state, logs RX/TX reports, and answers Wii status request `0x15` with input report `0x20`.

- Android 9+ (`minSdk 28`)
- Wii Remote HID report descriptor
- Android `BluetoothHidDevice` direct transport
- Android BLE → ESP32 fallback transport
- iPhone CoreBluetooth → ESP32 transport
- input report `0x30` for core buttons
- host reports `0x11`, `0x12`, and `0x15`
- A/B/1/2/+/-/HOME/D-pad UI
- protocol/session unit tests
- structured diagnostics for physical-Wii validation

The first hardware success criterion remains deliberately small: **press A on the phone and navigate the Wii Menu on a physical Wii**.

## Modules

- `:core:model` — Android-free Wii Remote domain state.
- `:core:protocol` — HID reports, host decoder, Wii HID descriptor and the common ESP32 bridge framing protocol.
- `:core:session` — `WiimoteSessionEngine`, reducer/state machine and transport port.
- `:platform:bluetooth` — direct Android `BluetoothHidDevice` adapter.
- `:platform:sensors` — Android motion/orientation source and calibration persistence.
- `:transports:esp32-ble` — Android BLE transport for the ESP32 fallback.
- `:feature:controller` — Compose controller UI.
- `:app` — Android frontend/runtime and transport selector.
- `:shared` — Kotlin Multiplatform/Swift facade over the common Wii core; its bridge compatibility facade delegates to `:core:protocol`.
- `iosApp/` — native SwiftUI + CoreMotion frontend and CoreBluetooth ESP32 transport.
- `firmware/esp32/` — BLE ↔ Bluetooth Classic HID bridge firmware; no Wii protocol state lives here.

See `docs/architecture/ARCHITECTURE.md`.

## ESP32 bridge protocol

Android and iOS use the same versioned bridge protocol and the same ESP32 firmware:

```text
phone
  │ BLE GATT
  │ INPUT_REPORT / CONTROL
  ▼
ESP32
  │ Bluetooth Classic HID
  ▼
Wii

Wii
  │ OUTPUT_REPORT
  ▼
ESP32
  │ confirmed BLE indication
  ▼
phone → WiimoteSessionEngine
```

Packets are capped at 20 bytes and fragmented with a six-byte version/type/sequence/fragment header. `BRIDGE_READY` negotiates the protocol version before Wii pairing controls are enabled.

## Current development state

### 0.5.x hardware-validation

Implemented in software:

- replaceable Android transports: Direct HID / ESP32 Bridge
- Android BLE discovery, GATT connection, indication subscription and ordered TX queue
- common Android/iOS ESP32 bridge framing in `:core:protocol`
- ESP32 pairing/bond controls and `DISCONNECTED / CONNECTING / CONNECTED` state
- complete Android HID control callbacks: GET_REPORT, SET_REPORT, SET_PROTOCOL and virtual-cable unplug
- standalone Wii output report `0x10` rumble
- persistent motion/gyro calibration profile
- structured `wiiremotex-hardware-trace-v1` JSON recorder
- RX/TX/HID-control trace snapshots with report and emulator state
- in-app JSON trace export and reset controls
- Linux/BlueZ + `btmon` validation procedure
- hardware compatibility matrix

See `docs/hardware/HARDWARE_VALIDATION.md` and `docs/hardware/COMPATIBILITY_MATRIX.md`.

### Wii protocol features

Implemented in software:

- core buttons and dynamic Wii data report modes
- accelerometer mapped to Wiimote acceleration values
- gyroscope mapped to MotionPlus raw values
- virtual IR pointer with Basic / Extended / Full payloads
- virtual Nunchuk stick + C/Z + accelerometer payload
- MotionPlus six-byte payload and Nunchuk pass-through
- report modes 0x30–0x37, 0x3D and interleaved 0x3E/0x3F
- host IR enable commands
- memory/register read/write decoding
- input reports 0x21 and 0x22 for register traffic
- Nunchuk and MotionPlus identification/register state
- foreground runtime, battery, rumble and diagnostics

Still hardware-gated:

- proving direct Android HID against a physical Wii
- proving Android/iPhone → ESP32 → Wii pairing and HID channels
- validating sensor axis/sign calibration against real games
- validating IR geometry against Wii cursor behavior
- MotionPlus calibration/pass-through edge cases
- Nunchuk/MotionPlus initialization compatibility across games
- report cadence, reconnect and bond persistence under real hardware

## Architecture direction

The bridge protocol is now owned by the common protocol layer rather than by the iOS facade. The next structural cleanup, after the transport hardware gate, is to make the three core modules native Kotlin Multiplatform modules with conventional source sets instead of having `:shared` include their JVM source directories.

Target structure:

```text
core/
├── model/       commonMain + commonTest
├── protocol/    commonMain + commonTest
├── session/     commonMain + commonTest
└── trace/       commonMain

transports/
├── android-hid/
├── esp32-ble/
└── linux-bluez/     # later
```

`LinuxBluezTransport` is intentionally postponed; it is not required for the mobile V1.

## Roadmap

```text
0.1.x  Bluetooth HID PoC
0.2.x  Motion / IR / Nunchuk / MotionPlus emulation
0.3.x  Hardware calibration + game compatibility
0.4.x  Protocol fidelity and extension hardening
0.5.x  Hardware validation + replaceable Android transports
0.6.x  Compatibility fixes + reconnect hardening
0.7.x  iOS + ESP32 hardware validation
0.8.x  Distribution, calibration wizard and product UX
1.0    Polished multiplatform product
```

## Build

JDK 17, AGP 9.4.0, Gradle 9.6.0, compileSdk 36.

```bash
gradle :core:protocol:test :core:session:test :shared:jvmTest :app:assembleDebug
```

## Legal

Wii, Wii Remote and Nintendo are trademarks of Nintendo. WiiRemoteX is an independent research/open-source project and is not affiliated with or endorsed by Nintendo.
