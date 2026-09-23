#!/usr/bin/env python3

import argparse
import asyncio
import sys
from dataclasses import dataclass, field
from typing import Dict, Optional

from bleak import BleakClient, BleakScanner

SERVICE_UUID = "7c0a0001-6f4b-4a42-9d47-575258000001"
PHONE_TO_BRIDGE_UUID = "7c0a0002-6f4b-4a42-9d47-575258000001"
BRIDGE_TO_PHONE_UUID = "7c0a0003-6f4b-4a42-9d47-575258000001"

PROTOCOL_VERSION = 1
HEADER_SIZE = 6
MAX_PACKET_SIZE = 20
MAX_FRAGMENT_PAYLOAD = MAX_PACKET_SIZE - HEADER_SIZE

TYPE_STATUS = 0x03
TYPE_CONTROL = 0x04
CONTROL_START_WII_PAIRING = 0x01

STATUS_WII_CONNECTION = 0x01
STATUS_BRIDGE_READY = 0x02
STATUS_ERROR = 0x7F

WII_DISCONNECTED = 0
WII_CONNECTING = 1
WII_CONNECTED = 2


@dataclass
class PendingMessage:
    message_type: int
    fragment_count: int
    fragments: Dict[int, bytes] = field(default_factory=dict)


class BridgeProbe:
    def __init__(self) -> None:
        self.pending: Dict[int, PendingMessage] = {}
        self.ready = asyncio.Event()
        self.wii_connected = asyncio.Event()
        self.bridge_error: Optional[int] = None
        self.firmware_version: Optional[str] = None

    def notification(self, _sender, data: bytearray) -> None:
        packet = bytes(data)
        if len(packet) < HEADER_SIZE or len(packet) > MAX_PACKET_SIZE:
            print(f"IGNORED malformed packet length={len(packet)}")
            return
        if packet[0] != PROTOCOL_VERSION:
            print(f"IGNORED incompatible packet protocol={packet[0]}")
            return

        message_type = packet[1]
        sequence = packet[2] | (packet[3] << 8)
        fragment_index = packet[4]
        fragment_count = packet[5]
        if fragment_count == 0 or fragment_index >= fragment_count:
            print("IGNORED impossible fragment metadata")
            return

        pending = self.pending.setdefault(
            sequence,
            PendingMessage(message_type=message_type, fragment_count=fragment_count),
        )
        if pending.message_type != message_type or pending.fragment_count != fragment_count:
            self.pending.pop(sequence, None)
            print(f"IGNORED conflicting sequence metadata seq={sequence}")
            return

        pending.fragments[fragment_index] = packet[HEADER_SIZE:]
        if len(pending.fragments) != pending.fragment_count:
            return

        payload = b"".join(pending.fragments[index] for index in range(fragment_count))
        self.pending.pop(sequence, None)
        self.handle_message(message_type, payload)

    def handle_message(self, message_type: int, payload: bytes) -> None:
        if message_type != TYPE_STATUS or len(payload) < 2:
            return

        status = payload[0]
        value = payload[1]
        if status == STATUS_BRIDGE_READY:
            if value != PROTOCOL_VERSION:
                self.bridge_error = 0x100 | value
                print(
                    f"ERROR bridge protocol={value}; expected={PROTOCOL_VERSION}",
                    file=sys.stderr,
                )
                self.ready.set()
                return

            if len(payload) >= 5:
                self.firmware_version = f"{payload[2]}.{payload[3]}.{payload[4]}"
            print(
                "BRIDGE_READY "
                f"protocol={value} firmware={self.firmware_version or 'unknown'}"
            )
            self.ready.set()
            return

        if status == STATUS_WII_CONNECTION:
            names = {
                WII_DISCONNECTED: "DISCONNECTED",
                WII_CONNECTING: "CONNECTING",
                WII_CONNECTED: "CONNECTED",
            }
            print(f"WII_CONNECTION {names.get(value, f'UNKNOWN({value})')}")
            if value == WII_CONNECTED:
                self.wii_connected.set()
            return

        if status == STATUS_ERROR:
            self.bridge_error = value
            print(f"BRIDGE_ERROR code=0x{value:02X}", file=sys.stderr)


def encode_message(message_type: int, sequence: int, payload: bytes) -> list[bytes]:
    if not 0 <= sequence <= 0xFFFF:
        raise ValueError("sequence outside uint16")
    if len(payload) > 256:
        raise ValueError("payload exceeds bridge protocol maximum")

    count = max(1, (len(payload) + MAX_FRAGMENT_PAYLOAD - 1) // MAX_FRAGMENT_PAYLOAD)
    packets = []
    for index in range(count):
        start = index * MAX_FRAGMENT_PAYLOAD
        fragment = payload[start : start + MAX_FRAGMENT_PAYLOAD]
        packets.append(
            bytes(
                [
                    PROTOCOL_VERSION,
                    message_type,
                    sequence & 0xFF,
                    (sequence >> 8) & 0xFF,
                    index,
                    count,
                ]
            )
            + fragment
        )
    return packets


async def find_bridge(timeout: float):
    print(f"Scanning for WiiRemoteX ESP32 bridge ({timeout:.0f}s)...")
    device = await BleakScanner.find_device_by_filter(
        lambda _device, advertisement: SERVICE_UUID.lower()
        in {uuid.lower() for uuid in advertisement.service_uuids},
        timeout=timeout,
    )
    if device is None:
        raise RuntimeError("WiiRemoteX ESP32 bridge not found")
    return device


async def scan(timeout: float) -> int:
    device = await find_bridge(timeout)
    print(f"FOUND name={device.name or 'unknown'} address={device.address}")
    return 0


async def run_probe(pair: bool, scan_timeout: float, ready_timeout: float, pair_timeout: float) -> int:
    device = await find_bridge(scan_timeout)
    print(f"CONNECTING name={device.name or 'unknown'} address={device.address}")

    probe = BridgeProbe()
    async with BleakClient(device) as client:
        print("BLE_CONNECTED")
        await client.start_notify(BRIDGE_TO_PHONE_UUID, probe.notification)

        try:
            await asyncio.wait_for(probe.ready.wait(), timeout=ready_timeout)
        except asyncio.TimeoutError as error:
            raise RuntimeError("Timed out waiting for BRIDGE_READY") from error

        if probe.bridge_error is not None:
            raise RuntimeError(f"Bridge reported error 0x{probe.bridge_error:X}")

        if not pair:
            print("PROBE_PASS")
            return 0

        packet = encode_message(
            TYPE_CONTROL,
            sequence=1,
            payload=bytes([CONTROL_START_WII_PAIRING]),
        )[0]
        await client.write_gatt_char(PHONE_TO_BRIDGE_UUID, packet, response=True)
        print("PAIRING_REQUEST_SENT — press the Wii red SYNC button now")

        try:
            await asyncio.wait_for(probe.wii_connected.wait(), timeout=pair_timeout)
        except asyncio.TimeoutError as error:
            raise RuntimeError("Timed out waiting for physical Wii connection") from error

        if probe.bridge_error is not None:
            raise RuntimeError(f"Bridge reported error 0x{probe.bridge_error:X}")

        print("PAIR_PASS")
        return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Local WiiRemoteX ESP32 BLE hardware-in-the-loop smoke test",
    )
    parser.add_argument("command", choices=("scan", "probe", "pair"))
    parser.add_argument("--scan-timeout", type=float, default=10.0)
    parser.add_argument("--ready-timeout", type=float, default=10.0)
    parser.add_argument("--pair-timeout", type=float, default=60.0)
    return parser.parse_args()


async def async_main() -> int:
    args = parse_args()
    if args.command == "scan":
        return await scan(args.scan_timeout)
    return await run_probe(
        pair=args.command == "pair",
        scan_timeout=args.scan_timeout,
        ready_timeout=args.ready_timeout,
        pair_timeout=args.pair_timeout,
    )


def main() -> int:
    try:
        return asyncio.run(async_main())
    except KeyboardInterrupt:
        print("Interrupted", file=sys.stderr)
        return 130
    except Exception as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
