# WiiRemoteX architecture

## Objective

WiiRemoteX is treated as a small hardware-emulation system, not as a conventional controller UI.

```text
Android / UI
    |
    v
Application / session
    |
    v
Protocol core
    |
    v
Domain model
```

The protocol core must never depend on `android.*`.

## Current modules

```text
:app
 |
 +--> :feature:controller ----> :core:model

:platform:bluetooth ----> :core:session ----> :core:protocol ----> :core:model
                                      \--------------------------> :core:model
```

### core:model

Pure state: buttons, player LEDs, rumble, report mode, continuous-reporting flag and battery level.

### core:protocol

Wire-format concerns: report `0x30`, button bitmasks, initial host command decoding and the HID report descriptor.

### core:session

Reducer/state machine. UI events and host reports become a new state plus explicit effects.

### platform:bluetooth

Android-only `BluetoothHidDevice` adapter: permissions, HID profile lifecycle, SDP registration, connection callbacks and report transport.

### feature:controller

Compose UI. It emits press/release events and knows nothing about Bluetooth packet encoding.

## Planned ports

After Milestone 0 proves the transport:

```text
MotionSource
PointerSource
HapticPort
ProtocolLogger
WiiExtension
```

Possible transport implementations:

```text
HidTransport
  +-- AndroidHidTransport
  +-- RootHidTransport
  +-- NativeBluezTransport
  +-- Esp32BridgeTransport
```

## Button A path

```text
touch down
   |
   v
Controller UI
   |
   v
WiimoteSessionEngine.setButton(A, true)
   |
   +--> state.pressedButtons += A
   |
   +--> CoreButtonsReportEncoder
             |
             v
       report 0x30
       payload 00 08
             |
             v
        HidTransport
             |
             v
      Nintendo Wii
```

## Host path

```text
Nintendo Wii
   |
   v
BluetoothHidDevice.Callback.onInterruptData()
   |
   v
HostCommandDecoder
   |
   +--> 0x11 LEDs / rumble
   +--> 0x12 report mode
   +--> 0x15 status request
   |
   v
WiimoteSessionEngine
```

## Why Milestone 0 comes first

The highest-risk assumption is whether Android's public Bluetooth HID Device stack can advertise an identity/SDP combination that a physical Wii accepts. IR, Nunchuk, MotionPlus and polish stay deferred until the Wii accepts report `0x30` from a phone.
