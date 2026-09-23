# WiiRemoteX hardware-in-the-loop validation

This directory-level procedure turns the ESP32 bridge into a repeatable local hardware gate. It is intentionally local: GitHub-hosted runners cannot access the developer's Bluetooth radio, ESP32 or physical Wii.

## What the HIL gate proves

The gate is split into measurable layers so a failure can be assigned to one subsystem:

1. **BLE discovery** — the host sees the WiiRemoteX service UUID.
2. **Bridge protocol** — `BRIDGE_READY` is received and protocol version matches.
3. **Control path** — `START_WII_PAIRING` is accepted by the ESP32.
4. **Wii Bluetooth** — bridge reports `CONNECTING` then `CONNECTED`.
5. **Host output path** — at least one Wii `OUTPUT_REPORT` reaches the phone/host side.
6. **Input path** — report `0x30 00 08` is transported without framing errors.
7. **Recovery** — disconnect/reconnect and bridge reset leave the protocol usable.

## Local smoke harness

The executable harness lives in `tools/hil/bridge_smoke.py` and uses Bleak.

Create a virtual environment and install dependencies:

```bash
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\\Scripts\\activate
pip install -r tools/hil/requirements.txt
```

List compatible BLE devices:

```bash
python tools/hil/bridge_smoke.py scan
```

Validate the phone-side BLE protocol without starting Wii pairing:

```bash
python tools/hil/bridge_smoke.py probe
```

Run the physical-Wii pairing gate:

```bash
python tools/hil/bridge_smoke.py pair
```

Then press the Wii red SYNC button. A passing run must observe:

```text
BRIDGE_READY protocol=1
WII_CONNECTION CONNECTING
WII_CONNECTION CONNECTED
```

The harness exits non-zero on timeout, incompatible protocol version, bridge error, malformed indication or missing Wii connection.

## Evidence to retain

For each HIL execution record:

- WiiRemoteX commit SHA
- ESP32 firmware version
- host OS and Bluetooth adapter
- Wii model / region / System Menu version
- transport route
- harness output
- app `wiiremotex-hardware-trace-v1` export when a phone is involved
- result in `COMPATIBILITY_MATRIX.md`

Do not commit raw Bluetooth addresses or unredacted radio captures.
