# WiiRemoteX hardware-in-the-loop validation

This procedure turns the ESP32 bridge into a repeatable local hardware gate. It is intentionally local: GitHub-hosted runners cannot access the developer's Bluetooth radio, ESP32 or physical Wii.

## What the HIL gate proves

The gate is split into measurable layers so a failure can be assigned to one subsystem:

1. **BLE discovery** — the host sees the WiiRemoteX service UUID.
2. **Bridge protocol** — `BRIDGE_READY` is received and protocol version matches.
3. **Control path** — `START_WII_PAIRING` is accepted by the ESP32.
4. **Wii Bluetooth** — bridge reports `CONNECTING` then `CONNECTED`.
5. **Host output path** — at least one Wii `OUTPUT_REPORT` reaches the phone/host side.
6. **Input transport path** — report `0x30 00 08` can be delivered through BLE → ESP32 → Classic HID.
7. **Recovery** — repeated BLE disconnect/reconnect cycles return to `BRIDGE_READY`.

The HIL `report-smoke` command proves transport delivery of the A-button frame. The physical **Gate 1** in `HARDWARE_VALIDATION.md` still requires a human to confirm that the Wii Menu visibly reacts; the local harness cannot observe the television screen.

## Local smoke harness

The executable harness lives in `tools/hil/bridge_smoke.py` and uses Bleak.

Create a virtual environment and install dependencies:

```bash
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\\Scripts\\activate
pip install -r tools/hil/requirements.txt
```

### BLE discovery

```bash
python tools/hil/bridge_smoke.py scan
```

### Bridge protocol only

```bash
python tools/hil/bridge_smoke.py probe
```

### Physical-Wii pairing

```bash
python tools/hil/bridge_smoke.py pair
```

Then press the Wii red SYNC button. A passing run must observe:

```text
BRIDGE_READY protocol=1
WII_CONNECTION CONNECTING
WII_CONNECTION CONNECTED
PAIR_PASS
```

### A-button transport smoke

```bash
python tools/hil/bridge_smoke.py report-smoke --repeat 3
```

After the bridge connects to the Wii, the harness emits:

```text
0x30 00 00
0x30 00 08   # A down
0x30 00 00   # A up
```

The default repeats A three times. `REPORT_SMOKE_PASS` means the frames were accepted for BLE transport; only mark hardware Gate 1 PASS after the Wii Menu visibly reacts.

### BLE reconnect soak

```bash
python tools/hil/bridge_smoke.py reconnect --cycles 10
```

Every cycle creates a fresh BLE connection, subscribes to indications and must receive `BRIDGE_READY`. This is useful for reproducing stale-GATT, callback-order and reconnect regressions without repeating Wii pairing.

## Evidence JSON

Any command can persist a machine-readable evidence file:

```bash
python tools/hil/bridge_smoke.py report-smoke \
  --evidence artifacts/HW-002-esp32-report-smoke.json
```

The evidence contains:

- command
- host OS
- bridge display name
- protocol / firmware version
- observed Wii connection states
- output report count
- malformed packet count
- reconnect cycle count
- input report count
- PASS / FAIL result and error text

Bluetooth addresses are deliberately not written to evidence JSON.

## CI role

GitHub Actions installs the HIL dependencies, compiles the harness and runs unit tests for:

- bridge framing
- fragmentation
- out-of-order reassembly
- conflicting duplicate fragments
- bounded reassembly state
- oversized messages
- evidence privacy

CI therefore validates the harness implementation even though it cannot execute the physical Bluetooth part.

## Evidence to retain

For each HIL execution record:

- WiiRemoteX commit SHA
- ESP32 firmware version
- host OS and Bluetooth adapter
- Wii model / region / System Menu version
- transport route
- harness output
- HIL evidence JSON
- app `wiiremotex-hardware-trace-v1` export when a phone is involved
- result in `COMPATIBILITY_MATRIX.md`

Do not commit raw Bluetooth addresses or unredacted radio captures.
