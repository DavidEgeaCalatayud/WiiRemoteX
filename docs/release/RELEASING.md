# WiiRemoteX release process

WiiRemoteX releases package the mobile application and ESP32 bridge firmware as one versioned hardware/software set.

## Versioning

The Android app version, release tag and release notes should refer to the same compatibility milestone.

For the current line:

```text
app version: 0.7.0-alpha
release tag: v0.7.0-alpha.N (pre-release) or v0.7.0 (stable milestone)
ESP32 firmware: reported independently by BRIDGE_READY
bridge protocol: version 1
```

Changing the BLE bridge wire format requires a bridge protocol version bump; changing only app/firmware implementation does not.

## Android signing secrets

The tag release workflow expects these repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64` — base64 encoded Android keystore
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The workflow materializes the keystore only inside the runner and exports:

- `WIIREMOTEX_KEYSTORE_PATH`
- `WIIREMOTEX_KEYSTORE_PASSWORD`
- `WIIREMOTEX_KEY_ALIAS`
- `WIIREMOTEX_KEY_PASSWORD`

Do not commit the keystore or signing passwords.

## Pre-release gate

Before creating a tag, require:

1. Android CI green.
2. Multiplatform CI green.
3. ESP32 CI green.
4. Quality workflow green.
5. physical Wii Gate 0 recorded.
6. any additional hardware claims in release notes recorded in `COMPATIBILITY_MATRIX.md`.

A release must never claim IR/Nunchuk/MotionPlus compatibility based only on unit tests.

## Release package

The tag workflow builds a package shaped like:

```text
WiiRemoteX-vX.Y.Z/
├── android/
│   └── WiiRemoteX-vX.Y.Z.apk
├── esp32/
│   ├── wiiremotex_esp32_bridge.bin
│   ├── bootloader.bin
│   ├── partition-table.bin
│   ├── flash_args
│   └── flasher_args.json
├── FLASHING.md
└── SHA256SUMS.txt
```

The workflow publishes the ZIP and individual APK/firmware files to the GitHub Release and generates SHA-256 checksums.

## Creating a release

After the branch has been merged and required hardware gates are recorded:

```bash
git switch main
git pull
git tag v0.7.0
git push origin v0.7.0
```

The `Release` GitHub Actions workflow then builds, signs, packages and publishes the artifacts.

For an alpha or release candidate, use a prerelease tag and mark the GitHub Release as prerelease after publication if required by the release policy.
