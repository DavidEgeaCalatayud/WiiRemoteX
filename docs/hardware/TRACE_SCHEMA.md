# Hardware trace schema

Schema identifier: `wiiremotex-hardware-trace-v1`.

The trace is designed to correlate Android-side events with a BlueZ/`btmon` capture, compare WiiRemoteX against a known-good reference conversation, and preserve reproducible evidence from a physical Wii session.

## Root fields

| Field | Meaning |
|---|---|
| `schema` | trace schema identifier |
| `session_id` | random UUID generated for the current capture session |
| `event_count` | number of events exported; replay tooling verifies it matches the array length |
| `device` | Android manufacturer/model/release/API metadata |
| `events` | ordered trace event array |

`session_id`, `event_count` and per-event `sequence` were added compatibly to v1. Older v1 captures remain valid when those fields are absent.

## Event fields

| Field | Meaning |
|---|---|
| `sequence` | zero-based event position; replay tooling verifies there are no gaps/reordering |
| `timestamp_ns` | wall-clock epoch time represented in nanoseconds |
| `elapsed_realtime_ns` | monotonic Android clock for ordering and interval analysis |
| `direction` | `RX`, `TX`, `HID_CONTROL`, `SYS`, `HID` or `ERR` |
| `transport` | transport implementation used by the emulator |
| `event` | semantic event name/detail |
| `report_id` | numeric HID/Wii report ID, or null for lifecycle events |
| `report_id_hex` | human-readable hexadecimal report ID |
| `payload_hex` | report payload without the HID report ID |
| `connection_state` | WiiRemoteX HID runtime state |
| `report_mode` | active Wii data-report mode as an integer |
| `report_mode_hex` | active report mode in hexadecimal |
| `extension_state` | Nunchuk/extension handshake snapshot |
| `ir_state` | IR enabled/configured/mode snapshot |
| `motion_plus_state` | MotionPlus presence/init/activation/pass-through snapshot |

## Important sequence markers

A successful first physical-Wii experiment should make it possible to identify:

```text
SYS  Starting Android HID Device profile
SYS  HID application registered
SYS  Bluetooth discoverability requested...
SYS  Bluetooth discoverability granted...
SYS  Bluetooth connection state: CONNECTING
SYS  Bluetooth connection state: CONNECTED
RX   0x15 ...       # Wii status request
TX   0x20 ...       # status reply
RX   0x12 ...       # report-mode selection
TX   0x30 00 08     # A down when report mode is 0x30
TX   0x30 00 00     # A up
```

The ESP32 route uses different lifecycle messages but the Wii report conversation itself is intentionally the same.

## Replay validation

Use the repository tool to validate structural integrity plus the minimum Wii handshake:

```bash
python tools/trace/trace_replay.py validate capture.json --require-handshake
```

Compare against a known-good trace:

```bash
python tools/trace/trace_replay.py compare reference.json capture.json --collapse-continuous
```

Use `--payload-mode exact` when the scenario is deterministic enough for byte-for-byte payload comparison. See `TRACE_REPLAY.md` for reference-trace provenance and workflow.

## Timing analysis

Use `elapsed_realtime_ns` rather than the wall clock when calculating:

- report interval
- jitter
- host-response latency
- time between connection and first output report
- time between an RX command and its corresponding ACK/status/data response

For a report series:

```text
delta_ns = current.elapsed_realtime_ns - previous.elapsed_realtime_ns
delta_ms = delta_ns / 1_000_000.0
```

## Privacy

The JSON schema intentionally does not store remote Bluetooth MAC addresses. HIL evidence JSON follows the same rule. If raw `btmon` captures are retained, redact addresses before publishing them.
