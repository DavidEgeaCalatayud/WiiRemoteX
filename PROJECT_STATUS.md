# WiiRemoteX — Project status

## Status: feature-complete alpha / maintenance mode

WiiRemoteX is considered **functionally complete enough to pause active development**. The project is not abandoned and is not being presented as a fully validated 1.0 release; instead, the current codebase is treated as a stable engineering milestone whose remaining work is primarily physical-hardware validation and compatibility refinement.

Current baseline: `0.7.1-alpha`.

## What is considered complete

The active implementation phase is considered closed for the following areas:

- shared Kotlin Multiplatform Wii protocol/session core
- Android direct Bluetooth HID transport
- Android BLE → ESP32 → Bluetooth Classic HID transport
- iOS BLE → ESP32 → Bluetooth Classic HID architecture
- core buttons and dynamic Wii report modes
- status, ACK, memory/register and EEPROM behavior
- accelerometer mapping
- virtual IR pointer and motion-pointer calibration
- virtual Nunchuk model and controls
- MotionPlus model, gyro mapping and Nunchuk pass-through foundations
- ESP32 firmware and Wii-side HID identity/pairing path
- transport timeouts, reconnect hardening and stale-callback protection
- hardware trace capture and replay tooling
- local hardware-in-the-loop tooling
- CI for Android, KMP/iOS, ESP32 and quality gates
- release packaging/signing infrastructure
- adaptive Android/iOS controller UX

A physical Nintendo Wii has successfully established a connection with WiiRemoteX. That milestone is verified.

## What remains intentionally deferred

The following items are **not blockers for considering the current engineering phase closed**, but remain useful future validation work:

1. prove the complete `A down → 0x30 00 08 → visible Wii Menu response` path on physical hardware
2. validate all core buttons and Wii output behavior end-to-end
3. validate accelerometer axis/sign/range behavior in real titles
4. validate IR geometry and calibration against the real Wii cursor
5. validate Nunchuk initialization and controls against compatible games
6. validate MotionPlus activation, gyro behavior and pass-through against real games
7. validate reconnect, power-cycle and bond persistence over repeated sessions
8. expand the compatibility matrix across additional Wii/phone combinations

These tests are tracked as hardware acceptance work rather than unfinished architecture.

## Optional future scope

The only feature work currently considered worth reopening the project for is improved accessory/game compatibility, especially **Wii Sports Resort**.

If the intended “bottom accessory” is the unit that plugs into the bottom of an original Wii Remote for Wii Sports Resort, that is **Wii MotionPlus**. WiiRemoteX already contains MotionPlus emulation foundations in software, so the preferred next step would be **game-driven validation and compatibility fixes**, not a new architecture.

If the intended accessory is the **Nunchuk** joystick, WiiRemoteX also already contains a virtual Nunchuk implementation. Again, future work should begin with real-game validation before adding more protocol code.

Potential future milestone:

```text
0.8.x — Wii Sports Resort / accessory compatibility pass
├── MotionPlus activation validation
├── yaw / roll / pitch calibration
├── slow/fast flags
├── Nunchuk pass-through where required
├── real-game traces
└── compatibility fixes only
```

## Maintenance policy

Until the project is intentionally reopened:

- no major architecture rewrites
- no speculative protocol additions without hardware evidence
- dependency/security/build fixes are acceptable
- documentation fixes are acceptable
- hardware-derived compatibility fixes are acceptable
- physical validation results may update the compatibility matrix without restarting feature development

## Reopening criteria

Active development should resume only if one of these occurs:

- a physical Wii test exposes a concrete protocol incompatibility
- Wii Sports Resort / MotionPlus testing identifies a reproducible missing behavior
- a new transport or supported platform becomes an explicit project goal
- the project is being prepared for a real `1.0` release

Until then, WiiRemoteX should be treated as a **completed engineering portfolio project with deferred hardware-validation work** rather than an indefinitely open prototype.
