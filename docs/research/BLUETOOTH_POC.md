# Milestone 0 — Android to real Wii HID PoC

## Research question

Can stock Android expose WiiRemoteX as a sufficiently Wii-compatible HID peripheral for a physical Wii to accept it as a Wii Remote?

## Known protocol facts

The original RVL-CNT-01 uses Bluetooth HID. Core data uses reports `0x30`–`0x3f`; report `0x30` contains two bytes of core-button state.

Initial host reports:

- `0x11` — player LEDs
- `0x12` — data reporting mode
- `0x15` — status request

## Android side

API 28+ exposes `BluetoothHidDevice`, custom HID SDP registration and `sendReport()`. The unresolved question is whether the public stack gives enough control over the complete Bluetooth identity/pairing behavior expected by the Wii.

## Hardware checklist

- [ ] Install debug APK on physical Android 9+ device.
- [ ] Grant `BLUETOOTH_CONNECT`.
- [ ] Start HID Device profile.
- [ ] Confirm `registerApp()` callback reports registered.
- [ ] Put Wii into controller sync mode.
- [ ] Observe whether Wii initiates a connection.
- [ ] Capture the first host output report.
- [ ] Send neutral report ID `0x30` / payload `00 00`.
- [ ] Send A pressed as report ID `0x30` / payload `00 08`.
- [ ] Confirm A navigates the real Wii Menu.

## Decision tree

```text
registerApp succeeds?
  +-- no  -> descriptor / Android API issue
  +-- yes -> Wii connects?
              +-- no  -> SDP/discoverability/pairing/identity limitation
              +-- yes -> host reports arrive?
                           +-- no  -> channel/handshake incompatibility
                           +-- yes -> report 0x30 controls Wii?
                                      +-- no  -> encoding/timing
                                      +-- yes -> Milestone 0 proven
```

## References

- https://wiibrew.org/wiki/Wiimote
- https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice
- https://developer.android.com/reference/android/bluetooth/BluetoothHidDeviceAppSdpSettings
- https://github.com/xwiimote/xwiimote/blob/master/doc/PROTOCOL
