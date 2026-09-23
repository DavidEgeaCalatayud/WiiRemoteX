# WiiRemoteX 0.5.0 hardware validation

The purpose of this milestone is to replace protocol assumptions with measured behaviour.

## Test A — Android phone against a physical Wii

Record the phone model, Android version, Wii model/region and Wii System Menu version.

Expected minimum sequence:

```text
REGISTER HID
DISCOVERABLE
WII CONNECTING
WII CONNECTED
Wii -> output reports
Phone -> status/data reports
A down -> 0x30 00 08
A up   -> 0x30 00 00
```

Export the in-app JSON trace after every connection attempt, including failed attempts.

### Pass gate

- HID application registration succeeds.
- Wii creates a Bluetooth connection.
- At least one host report is received.
- Wii accepts status/data reports.
- A down/up is observable in the Wii Menu.

### Failure classification

1. Android HID registration.
2. Discoverability / inquiry.
3. SDP or device identity.
4. HID control/interrupt channel.
5. Wii output-report handshake.
6. WiiRemoteX input report encoding/timing.

Do not tune IR or extensions until the failing layer is known.

## Test B — Linux Bluetooth laboratory

Use a Linux PC with BlueZ and a Bluetooth adapter that supports monitor capture.

Terminal 1:

```bash
sudo btmon -w wiiremotex.btsnoop
```

Terminal 2:

```bash
bluetoothctl
power on
scan on
```

Start WiiRemoteX HID + discoverability on Android and allow Linux to discover/connect to the device.

Capture:

- inquiry/discovery
- SDP attributes
- HID descriptor
- L2CAP control/interrupt traffic
- report IDs and payloads
- packet/report timing

For a reference capture repeat the same experiment with an original RVL-CNT-01 Wii Remote:

```bash
sudo btmon -w wiimote-original.btsnoop
```

## Behavioural diff

Compare the reference device and WiiRemoteX in this order:

| Layer | Original Wii Remote | WiiRemoteX | Result |
|---|---|---|---|
| Inquiry / discoverability | capture | capture | pending |
| Class of Device | capture | capture | pending |
| SDP HID record | capture | capture | pending |
| HID report descriptor | capture | capture | pending |
| HID control channel | capture | capture | pending |
| HID interrupt channel | capture | capture | pending |
| Output-report handshake | capture | JSON + btmon | pending |
| Input reports | capture | JSON + btmon | pending |
| Cadence / jitter | measure | measure | pending |

Keep raw captures out of source control if they contain Bluetooth addresses. Store a redacted summary in the compatibility matrix.
