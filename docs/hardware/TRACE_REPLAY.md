# Protocol trace replay

WiiRemoteX hardware traces are useful for more than debugging. A trace from a known-good Wii Remote can be retained as a protocol reference and compared with a later WiiRemoteX run.

The tool is `tools/trace/trace_replay.py` and consumes the existing `wiiremotex-hardware-trace-v1` JSON shape documented in `TRACE_SCHEMA.md`.

## Why replay is normalized

Bluetooth callback timestamps are not deterministic across Android, iOS, ESP32, operating systems or radio conditions. A useful golden test therefore compares the stable protocol contract:

- direction (`RX`, `TX`, `HID_CONTROL`)
- report ID
- ordering
- optionally the exact payload

Wall-clock timestamps, device metadata and diagnostic log lines are deliberately excluded from the default comparison.

Continuous input reports may also be collapsed when identical frames repeat at the normal 100 Hz cadence.

## Validate a captured trace

```bash
python tools/trace/trace_replay.py validate traces/android-rvl001.json --require-handshake
```

`--require-handshake` verifies this ordered minimum conversation:

```text
RX 0x15  Wii asks for status
TX 0x20  WiiRemoteX replies with status
RX 0x12  Wii selects a report mode
TX 0x30-0x3F  continuous input report follows
```

A missing or out-of-order step exits non-zero.

## Compare WiiRemoteX against a reference

Report-level equivalence, allowing additional candidate traffic:

```bash
python tools/trace/trace_replay.py compare \
  traces/reference-original-wiimote.json \
  traces/wiiremotex-rvl001.json \
  --require-handshake \
  --collapse-continuous
```

Exact payload comparison:

```bash
python tools/trace/trace_replay.py compare \
  traces/reference-original-wiimote.json \
  traces/wiiremotex-rvl001.json \
  --payload-mode exact \
  --collapse-continuous
```

The reference sequence must appear in order inside the candidate. Extra candidate reports are permitted because a real implementation may emit additional continuous frames between host commands.

## Reference traces from an original Wii Remote

A raw Bluetooth sniffer capture is not committed directly as a golden trace. First translate the relevant HID interrupt/control exchange into the `wiiremotex-hardware-trace-v1` JSON schema. Keep only the protocol evidence needed for the scenario and redact radio addresses.

Recommended references:

1. boot/status/report-mode conversation
2. LEDs + rumble
3. report modes `0x30` through `0x37`
4. extension init + register reads/writes
5. Nunchuk conversation
6. MotionPlus activation/pass-through
7. IR enable/configuration

Reference files should be named by console/revision/scenario, for example:

```text
traces/reference/
├── rvl001-status-report30.json
├── rvl001-nunchuk-init.json
└── rvl001-motionplus-pass-through.json
```

Do not label a trace as an original-Wiimote reference unless its provenance is known and the capture was actually obtained from physical hardware.

## CI role

CI unit-tests the replay engine itself. Physical reference traces can later be added as regression fixtures, at which point a dedicated comparison step can make protocol equivalence a blocking gate without requiring a Wii on the GitHub runner.
