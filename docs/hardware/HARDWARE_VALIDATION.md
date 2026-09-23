# WiiRemoteX 0.7 physical-hardware validation

The purpose of this phase is to replace protocol assumptions with measured behaviour on real hardware.

A physical Nintendo Wii has already established a connection with WiiRemoteX. That is **Gate 0** only: it proves reachability, not full Wii Remote compatibility.

## Evidence rules

Every physical test session should record:

- a run ID (`HW-###`)
- WiiRemoteX commit SHA
- Android/iOS build identifier
- phone model and OS/API
- selected transport route
- ESP32 firmware version when applicable
- Wii model, region and System Menu version
- game/title used for feature validation
- exported `wiiremotex-hardware-trace-v1` JSON
- HIL harness output when applicable
- pass/fail notes in `COMPATIBILITY_MATRIX.md`

Do not mark a gate PASS from a software unit test alone.

## Gate 0 — physical Wii connection

**Status: PASS (exact hardware metadata must be captured on the next run).**

Acceptance criteria:

- the selected transport starts
- the Wii establishes the Bluetooth/HID connection
- WiiRemoteX reaches a connected state

This gate has been achieved on physical Wii hardware.

## Gate 1 — core A-button round trip

Expected sequence:

```text
Wii -> 0x15 status request
WiiRemoteX -> 0x20 status
Wii -> 0x12 report mode selection
A down -> 0x30 00 08
A up   -> 0x30 00 00
Wii Menu visibly responds
```

Pass criteria:

- at least one Wii output report is captured
- report mode negotiation is visible in trace
- `0x30 00 08` is transmitted on A down
- `0x30 00 00` is transmitted on A up
- the Wii Menu visibly responds to the press

If the bytes are correct but the Wii does not react, classify the failure as timing/HID transport rather than button mapping.

## Gate 2 — complete core controls + Wii output path

Validate down/up for:

- D-pad
- A / B
- 1 / 2
- + / -
- HOME

Also verify:

- LED output reports update emulator state
- report `0x10` rumble toggles phone vibration
- status request `0x15` returns a valid battery byte
- report-mode changes through `0x12` persist correctly

## Gate 3 — accelerometer

Validate in a title that exposes tilt/motion clearly.

Acceptance criteria:

- resting values are stable around calibration zero-g values
- axis signs match physical movement
- X/Y/Z ranges do not clip during ordinary movement
- continuous report cadence remains stable while motion data changes

Record any axis inversion or scale error before tuning calibration constants.

## Gate 4 — IR

Validate both manual virtual pointer and motion pointer.

Acceptance criteria:

- Wii performs the IR initialization sequence
- Basic / Extended / Full payload modes are emitted when requested
- cursor appears on screen
- horizontal and vertical direction match phone movement
- recenter returns the pointer to a predictable center
- edge reach and jitter are acceptable

Do not tune pointer smoothing until transport/report cadence is known to be stable.

## Gate 5 — Nunchuk

Use a compatible title and capture the full initialization exchange.

Acceptance criteria:

- attach emits status `0x20`
- data reporting pauses until the host selects a new report mode
- `A400F0=55` initialization is accepted
- `A400FB=00` plaintext mode is accepted
- joystick X/Y directions match
- C and Z buttons match
- Nunchuk accelerometer data is plausible

## Gate 6 — MotionPlus

Use a MotionPlus-compatible title.

Acceptance criteria:

- MotionPlus register initialization completes
- activation mode is retained
- gyro axes/signs match movement
- slow/fast flags behave plausibly
- Nunchuk pass-through works when enabled
- no flag/high-bit corruption is visible in captures

## Gate 7 — disconnect, reconnect and bond persistence

Run each scenario at least three times:

1. stop/start WiiRemoteX transport
2. disable/re-enable phone Bluetooth
3. power-cycle ESP32 bridge
4. power-cycle Wii
5. move Wii/bridge briefly out of range and return
6. clear bond and pair again
7. restart the mobile app without clearing bond

Pass criteria:

- no stale `CONNECTED` UI state
- no manual process restart required for ordinary recovery
- no permanently wedged BLE write queue
- pairing can be retried after timeout/failure
- persisted bonds reconnect where the selected route supports persistence

## Failure classification

Classify the earliest failing layer:

1. Android/iOS permissions or radio availability
2. BLE discovery / GATT setup
3. ESP32 bridge protocol/version
4. Wii inquiry / discoverability
5. SDP or device identity
6. authentication / legacy PIN / bond
7. HID control/interrupt channel
8. Wii output-report handshake
9. WiiRemoteX input report encoding
10. report timing/jitter
11. sensor/IR/extension semantics

Do not tune later layers while an earlier layer is failing.

## Linux Bluetooth laboratory

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

Capture WiiRemoteX and then an original RVL-CNT-01 under equivalent conditions.

Compare in this order:

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

## ESP32 HIL

See `HIL.md` and `tools/hil/bridge_smoke.py` for the local BLE/ESP32 smoke gate.
