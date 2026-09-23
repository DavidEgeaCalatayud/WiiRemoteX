# WiiRemoteX

**WiiRemoteX** is an experimental multiplatform Wii Remote emulation system built around one Kotlin Multiplatform protocol/session core. Android can talk to a physical Nintendo Wii directly through Bluetooth HID or through an ESP32 bridge; iPhone uses the same ESP32 BLE → Bluetooth Classic HID bridge.

> ✅ **Verified hardware milestone:** WiiRemoteX has successfully established a connection with a real Nintendo Wii.
>
> 🧪 **Current gate:** prove the complete `A down → report 0x30 00 08 → Wii Menu response` round trip, then validate the remaining controls, sensors, extensions, reconnect and bond persistence on physical hardware.

## Architecture

```text
                         WiimoteSessionEngine
                                 │
                         Wii HID protocol
                                 │
               ┌─────────────────┼─────────────────┐
               │                                   │
        Android Direct                    ESP32 BLE bridge
               │                                   │
      BluetoothHidDevice             ┌─────────────┴─────────────┐
               │                     │                           │
               │                  Android                      iPhone
               │                     │                           │
               │                     └──────── BLE ──────────────┘
               │                                   │
               │                                 ESP32
               │                                   │
               │                         Bluetooth Classic HID
               └──────────────────┬────────────────┘
                                  ▼
                            Nintendo Wii
```

The Wii protocol is transport-independent. Buttons, report modes, accelerometer, IR, Nunchuk, MotionPlus, memory/register state, rumble and status handling live in the shared `WiimoteSessionEngine`; transport implementations only move encoded reports between that engine and the console.

Android exposes two selectable routes:

```text
Transport
├── Direct HID       Android BluetoothHidDevice → Wii
└── ESP32 Bridge     Android BLE → ESP32 → Bluetooth Classic HID → Wii
```

The ESP32 is intentionally a transport bridge rather than a second Wii Remote implementation.

## Hardware status

### Verified

- ✅ a physical Nintendo Wii has established a connection with WiiRemoteX
- ✅ Android/JVM protocol and session tests run in CI
- ✅ Kotlin Multiplatform shared tests run in CI
- ✅ iOS framework and simulator app build in CI
- ✅ ESP32 firmware builds in CI
- ✅ the local ESP32 HIL harness is syntax-validated in CI

The exact model/route metadata from the first successful Wii connection was not retained, so it is recorded conservatively as `HW-001` in the compatibility ledger and must be completed during the next physical test session.

### Next physical gates

1. **Core A-button:** `0x15 → 0x20 → 0x12 → 0x30 00 08`, with visible Wii Menu response.
2. **Core controls/output path:** D-pad, A/B, 1/2, +/−, HOME, LEDs, rumble, battery and report-mode changes.
3. **Accelerometer:** axes, signs, zero-g values, range and cadence in a real title.
4. **IR:** initialization, Basic/Extended/Full modes, cursor geometry, sensitivity and recenter.
5. **Nunchuk:** extension initialization, stick, C/Z and accelerometer.
6. **MotionPlus:** initialization, gyro axes/flags and Nunchuk pass-through.
7. **Recovery:** disconnect/reconnect, Bluetooth toggle, ESP32/Wii power-cycle and bond persistence.

See [`docs/hardware/HARDWARE_VALIDATION.md`](docs/hardware/HARDWARE_VALIDATION.md) and [`docs/hardware/COMPATIBILITY_MATRIX.md`](docs/hardware/COMPATIBILITY_MATRIX.md).

## Protocol validation

The core now contains reference conversation tests that exercise a complete host/session flow instead of isolated encoders only:

```text
Wii -> 0x15 status request
WiiRemoteX -> 0x20 status
Wii -> 0x12 report-mode selection
WiiRemoteX -> continuous input reports
Wii -> extension register writes/reads
WiiRemoteX -> 0x22 ACK / 0x21 read responses
```

The reference A-button path explicitly asserts:

```text
A down -> report 0x30 payload 00 08
A up   -> report 0x30 payload 00 00
```

The BLE bridge protocol also has deterministic property-style robustness coverage:

- 1,000 randomized payload round trips across all bridge message types
- shuffled multi-fragment delivery
- 5,000 malformed random packets that must not escape the decoder
- duplicate fragments
- conflicting sequence metadata
- truncated headers
- impossible fragment indexes
- oversized packets/messages
- protocol-version validation

## Hardware-in-the-loop

`tools/hil/bridge_smoke.py` provides a local BLE smoke harness for an ESP32 connected to real hardware.

```bash
python -m venv .venv
source .venv/bin/activate       # Windows: .venv\Scripts\activate
pip install -r tools/hil/requirements.txt

python tools/hil/bridge_smoke.py scan
python tools/hil/bridge_smoke.py probe
python tools/hil/bridge_smoke.py pair
```

The pairing gate expects:

```text
BRIDGE_READY protocol=1
WII_CONNECTION CONNECTING
WII_CONNECTION CONNECTED
```

GitHub-hosted runners cannot access the developer's Bluetooth radio, ESP32 or Wii, so physical HIL remains a local acceptance gate. CI validates that the harness itself remains syntactically valid.

See [`docs/hardware/HIL.md`](docs/hardware/HIL.md).

## Wii protocol features

Implemented in software:

- core buttons and dynamic Wii data-report modes
- report modes `0x30–0x37`, `0x3D`, `0x3E`, `0x3F`
- status `0x20`, memory read `0x21` and ACK `0x22`
- host commands including rumble, LEDs, report mode and status requests
- virtual writable Wii Remote EEPROM and register banks
- Android/iOS accelerometer mapping
- MotionPlus gyro mapping
- virtual IR pointer with Basic / Extended / Full payloads
- virtual Nunchuk stick, C/Z and accelerometer payload
- Nunchuk initialization/register state
- MotionPlus initialization, slow/fast state and Nunchuk pass-through
- persistent gyro calibration and pointer sensitivity
- structured `wiiremotex-hardware-trace-v1` diagnostics

Physical compatibility for the feature set is intentionally tracked separately from software implementation status.

## ESP32 bridge

Android and iOS use the same versioned BLE bridge protocol:

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

Current bridge properties:

- protocol version `1`
- maximum BLE packet size `20` bytes
- six-byte version/type/sequence/fragment header
- bounded multi-fragment reassembly
- `INPUT_REPORT`, `OUTPUT_REPORT`, `STATUS`, `CONTROL`
- `BRIDGE_READY` compatibility handshake + firmware version
- Pair Wii / Stop pairing / Clear bond controls
- ordered Android BLE TX queue with retry/backpressure
- explicit scan/connect/discovery/indication timeouts
- bounded Android BLE reconnect attempts
- stale-GATT callback rejection during recovery
- ESP32 → phone confirmed indication queue
- Wii-side Bluetooth Classic HID identity/pairing implementation

## Product UX

The 0.7 line adds product-facing controls on top of the engineering diagnostics:

- connection assistant for Direct HID / ESP32 routes
- explicit bridge protocol + firmware status
- human-readable recovery hints
- Motion Pointer calibration/recenter flow
- persistent pointer sensitivity (`0.5×–2.0×`)
- iOS responsive portrait/landscape layout
- Android scroll-safe controller layout
- diagnostics and structured trace sharing

The UI is still considered pre-1.0 and will continue to be refined after the physical protocol gates are complete.

## Modules

```text
core/
├── model/       Kotlin Multiplatform domain state
├── protocol/    Wii reports, descriptor, memory/registers, bridge framing
├── session/     WiimoteSessionEngine state machine
└── trace/       shared hardware trace schema

transports/
├── android-hid/ Android BluetoothHidDevice → Wii
└── esp32-ble/   Android BLE → ESP32

platform/
└── sensors/     Android motion/orientation + calibration

app/             Android runtime/frontend
feature/         Android Compose controller UI
shared/          Swift-facing KMP facade
iosApp/          native SwiftUI + CoreMotion + CoreBluetooth frontend
firmware/esp32/  BLE ↔ Bluetooth Classic HID bridge
tools/hil/       local hardware smoke harness
docs/            architecture, research, validation and release procedures
```

`LinuxBluezTransport` remains intentionally deferred; Linux/BlueZ is currently used as a Bluetooth analysis/reference-capture laboratory rather than a V1 user transport.

## Robustness and quality

GitHub Actions validate four independent areas:

- **Android CI** — core/session tests + debug APK
- **Multiplatform CI** — shared JVM tests + iOS Kotlin/Native framework + simulator app
- **ESP32 CI** — native ESP-IDF firmware build + flashable artifacts
- **Quality** — Detekt, Android Lint and HIL harness validation as blocking checks; ktlint, SwiftLint and clang-format currently report pre-existing style debt while the repository is normalized incrementally

Style debt is deliberately visible rather than hidden, but it is not allowed to block functional/hardware hardening until a dedicated repository-wide formatting pass is performed.

## Release engineering

The Android app is versioned as the `0.7.0-alpha` line. A tag-driven release workflow can build:

```text
WiiRemoteX-vX.Y.Z/
├── android/
│   └── WiiRemoteX-vX.Y.Z.apk
├── esp32/
│   ├── wiiremotex_esp32_bridge.bin
│   ├── bootloader.bin
│   ├── partition-table.bin
│   ├── flash_args
│   └── flasher_args.json
├── FLASHING.md
└── SHA256SUMS.txt
```

Release APK signing is supplied through GitHub Actions secrets; signing material is never committed. Release packages include SHA-256 checksums and ESP32 flashing metadata.

See [`docs/release/RELEASING.md`](docs/release/RELEASING.md) and [`firmware/esp32/FLASHING.md`](firmware/esp32/FLASHING.md).

## Build

Requirements: JDK 17, AGP 9.4.0, Gradle 9.6.0, compileSdk 36.

```bash
gradle :core:protocol:jvmTest \
       :core:session:jvmTest \
       :core:trace:jvmTest \
       :shared:jvmTest \
       :app:assembleDebug
```

## Roadmap

```text
0.1.x  Bluetooth HID PoC
0.2.x  Motion / IR / Nunchuk / MotionPlus emulation
0.3.x  Hardware calibration + game compatibility
0.4.x  Protocol fidelity and extension hardening
0.5.x  Hardware instrumentation + replaceable transports
0.6.x  ESP32/iOS bridge foundation + KMP cleanup
0.7.x  Physical validation, reconnect hardening, HIL, release + UX
0.8.x  Compatibility fixes driven by real games/hardware + distribution polish
1.0    Polished, evidence-backed multiplatform product
```

## Legal

Wii, Wii Remote and Nintendo are trademarks of Nintendo. WiiRemoteX is an independent research/open-source project and is not affiliated with or endorsed by Nintendo.
