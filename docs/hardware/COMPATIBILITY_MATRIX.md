# Hardware compatibility matrix

Status values: **PASS**, **PARTIAL**, **FAIL**, **NOT TESTED**.

| Android device | Android/API | Wii hardware | System Menu | HID register | Wii connects | Core buttons | Accel | IR | Nunchuk | MotionPlus | Trace |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Xiaomi Redmi Note 14 Pro+ 5G | TBD | TBD | TBD | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | pending |

## Per-test evidence

For each run record:

- WiiRemoteX commit SHA
- APK build/run identifier
- Android manufacturer/model and API
- Wii model/region and System Menu version
- result at each Bluetooth/HID gate
- exported `wiiremotex-hardware-trace-v1` JSON filename
- optional redacted `btmon` capture filename
- game used for sensor/extension validation
- notes on latency, jitter, axis/sign issues and reconnect behaviour
