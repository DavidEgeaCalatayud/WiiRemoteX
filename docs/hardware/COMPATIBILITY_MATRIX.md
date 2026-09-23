# Hardware compatibility matrix

This file is an evidence ledger, not a wishlist. A capability is marked **PASS** only when the corresponding physical-hardware gate has been executed and evidence has been retained.

Status values:

- **PASS** — reproduced on the listed hardware/route.
- **PARTIAL** — connection or a subset of the feature works, but the full gate is incomplete.
- **FAIL** — reproduced failure with evidence.
- **NOT TESTED** — no physical evidence yet.

## Validated runs

| Run | Phone / host | OS / API | Transport | Wii hardware | System Menu | Connect | Host RX | Core buttons | Accel | IR | Nunchuk | MotionPlus | Reconnect | Bond persist | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| HW-001 | metadata not recorded | metadata not recorded | metadata not recorded | physical Nintendo Wii, exact model TBD | TBD | **PASS** | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | physical connection milestone reported; capture exact metadata on next run |

The existing successful Wii connection is intentionally recorded conservatively. It proves the first connectivity gate, but it does **not** imply that button reports, output-report handling, sensors, extensions, reconnect or bond persistence already pass.

## Planned coverage matrix

Use this as the minimum cross-product for future test sessions. It is acceptable to leave rows untested until hardware is available.

| Client family | Route | Wii target | Purpose |
|---|---|---|---|
| Android reference device | Direct Android HID | Wii RVL-001 / available physical Wii | Validate direct `BluetoothHidDevice` path |
| Android reference device | ESP32 Bridge | Wii RVL-001 / available physical Wii | Validate BLE fallback + Classic HID bridge |
| iPhone reference device | ESP32 Bridge | Wii RVL-001 / available physical Wii | Validate KMP/iOS + CoreBluetooth route |
| Android secondary vendor | Direct / ESP32 | available physical Wii | Detect OEM Bluetooth-stack differences |
| Wii Family Edition, if available | supported route | RVL-101 | Detect console revision differences |
| Wii U vWii, if available | supported route | vWii | Detect host-stack differences |

## Feature gates per run

Record these in order so a later failure does not obscure an earlier transport problem:

1. BLE/HID transport starts.
2. Wii reaches `CONNECTED`.
3. At least one Wii output report is observed.
4. `A down` produces input report `0x30 00 08` and visibly moves/selects in Wii Menu.
5. All core buttons pass down/up validation.
6. LED commands are reflected in emulator state.
7. Rumble command is reflected on the phone.
8. Accelerometer axis/sign and resting calibration are plausible in a real title.
9. IR pointer geometry, range and recenter work in Wii Menu / compatible title.
10. Nunchuk init + controls pass in a compatible title.
11. MotionPlus init + gyro + pass-through pass in a compatible title.
12. Disconnect/reconnect succeeds without process restart.
13. Power-cycle reconnect succeeds.
14. Bond persistence succeeds after phone/ESP32/Wii restart where the route supports it.

For the ESP32 route, `tools/hil/bridge_smoke.py report-smoke` can automate the transport half of gate 4 and `reconnect --cycles N` can automate the BLE half of gate 12. The visible Wii Menu reaction and Wii-side persistence still require physical observation.

## Per-test evidence

For each run retain:

- run ID (`HW-###`)
- WiiRemoteX commit SHA
- APK build/run identifier or iOS build identifier
- ESP32 firmware version when applicable
- client manufacturer/model and OS/API
- selected transport route
- Wii model/region and System Menu version
- game/title used for sensor and extension validation
- result at each Bluetooth/HID gate
- exported `wiiremotex-hardware-trace-v1` JSON filename
- HIL harness output when ESP32 is used
- HIL evidence JSON (`--evidence artifacts/HW-###-*.json`)
- optional normalized reference comparison from `tools/trace/trace_replay.py`
- optional redacted `btmon` capture filename
- notes on latency, jitter, axis/sign issues, disconnects and reconnect behaviour

A recommended evidence directory for one run is:

```text
artifacts/HW-002/
├── hil-report-smoke.json
├── hil-reconnect.json
├── android-hardware-trace.json
├── trace-replay.txt
└── notes.md
```

A compatibility row must not infer `PASS` from implementation status or CI alone. CI proves software regression coverage; this ledger records observed hardware behaviour.

Raw radio captures containing Bluetooth addresses should not be committed without redaction.
