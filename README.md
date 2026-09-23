# WiiRemoteX

**WiiRemoteX** is an experimental Android project that aims to make an Android phone behave like a Wii Remote when talking to a **real Nintendo Wii** over Bluetooth HID.

> Status: **0.5.0 hardware-validation**. The software emulator is feature-rich enough for the next gate: measure Android Bluetooth HID behaviour against a physical Wii and a reference Wii Remote.

## Goal

```text
Android UI / sensors
        |
        v
WiimoteSessionEngine
        |
        v
Wiimote protocol encoder
        |
        v
Android Bluetooth HID Device
        |
        v
Nintendo Wii
```

The Wiimote protocol is intentionally independent from Android. If stock Android cannot reproduce every Bluetooth/SDP behavior expected by the Wii, the transport can later be replaced without rewriting the protocol engine.

## Milestone 0

The current PoC now wires Compose button events through the session engine into the Android HID transport, exposes Bluetooth registration/connection state, supports discoverability from the app, logs RX/TX reports, and answers Wii status request `0x15` with input report `0x20`.


- Android 9+ (`minSdk 28`)
- Wii Remote HID report descriptor
- Android `BluetoothHidDevice` registration
- input report `0x30` for core buttons
- host reports `0x11`, `0x12`, and `0x15`
- A/B/1/2/+/-/HOME/D-pad UI
- protocol/session unit tests
- diagnostics for the real-Wii experiment

The first success criterion is deliberately small: **press A on the phone and navigate the Wii Menu on a physical Wii**.

## Modules

- `:core:model` — Android-free domain state.
- `:core:protocol` — HID reports, bitmasks, host decoder and Wii HID descriptor.
- `:core:session` — state machine/reducer and transport port.
- `:platform:bluetooth` — Android `BluetoothHidDevice` adapter.
- `:feature:controller` — Compose controller UI.
- `:app` — Android entry point.

See `docs/architecture/ARCHITECTURE.md`.

## Current development state

### 0.5.0-hardware-validation

This milestone adds the instrumentation needed to validate the emulator empirically:

- complete Android HID control callbacks: GET_REPORT, SET_REPORT, SET_PROTOCOL and virtual-cable unplug
- standalone Wii output report `0x10` rumble
- persistent motion/gyro calibration profile
- structured `wiiremotex-hardware-trace-v1` JSON recorder
- RX/TX/HID-control trace snapshots with report and emulator state
- in-app JSON trace export and reset controls
- Linux/BlueZ + `btmon` validation procedure
- hardware compatibility matrix

See `docs/hardware/HARDWARE_VALIDATION.md` and `docs/hardware/COMPATIBILITY_MATRIX.md`.

### 0.4.0-alpha

Implemented in software:

- core buttons and dynamic Wii data report modes
- Android accelerometer mapped to Wiimote acceleration values
- Android gyroscope mapped to MotionPlus raw values
- virtual IR pointer with extended/basic IR payloads
- virtual Nunchuk stick + C/Z + accelerometer payload
- MotionPlus six-byte payload
- report modes 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37 and 0x3D
- host IR enable commands
- memory/register read/write command decoding
- input reports 0x21 and 0x22 for register traffic
- Nunchuk and MotionPlus identification registers
- foreground HID runtime, battery, rumble and diagnostics

Still hardware-gated:

- proving stock Android pairs successfully with a real Wii
- validating sensor axis/sign calibration against real games
- validating IR geometry against Wii cursor behavior
- MotionPlus calibration/pass-through edge cases
- Nunchuk/MotionPlus initialization compatibility across games
- hardware validation of interleaved IR 0x3E/0x3F timing and full-mode geometry

## Roadmap

```text
0.1.x  Bluetooth HID PoC
0.2.x  Motion / IR / Nunchuk / MotionPlus emulation
0.3.x  Hardware calibration + game compatibility
0.4.x  Protocol fidelity and extension hardening
0.5.x  Hardware validation + measured behavioural diff
0.6.x  Compatibility fixes + reconnect hardening
1.0    Polished Android product
```

## Build

JDK 17, AGP 9.4.0, Gradle 9.6.0, compileSdk 36.

```bash
gradle :core:protocol:test :core:session:test :app:assembleDebug
```

## Legal

Wii, Wii Remote and Nintendo are trademarks of Nintendo. WiiRemoteX is an independent research/open-source project and is not affiliated with or endorsed by Nintendo.
