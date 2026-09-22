# WiiRemoteX

**WiiRemoteX** is an experimental Android project that aims to make an Android phone behave like a Wii Remote when talking to a **real Nintendo Wii** over Bluetooth HID.

> Status: **Milestone 0 / Bluetooth proof of concept**. The core protocol model and Android HID-device scaffold are being built first; real-console pairing still has to be validated on hardware.

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

## Roadmap

```text
V0.1  Bluetooth HID + A/B on a real Wii
V0.2  All core buttons + robust reconnect
V0.3  Accelerometer
V0.4  Rumble + player LEDs + battery/status
V0.5  Virtual IR pointer (motion + touchpad)
V0.6  Virtual Nunchuk
V0.7  MotionPlus
V1.0  Polished Android product
```

## Build

JDK 17, AGP 9.4.0, Gradle 9.6.0, compileSdk 37.

```bash
gradle :core:protocol:test :core:session:test :app:assembleDebug
```

## Legal

Wii, Wii Remote and Nintendo are trademarks of Nintendo. WiiRemoteX is an independent research/open-source project and is not affiliated with or endorsed by Nintendo.
