#!/usr/bin/env python3

import argparse
import asyncio
import json
import platform
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Optional

from bleak import BleakClient, BleakScanner

SERVICE_UUID = "7c0a0001-6f4b-4a42-9d47-575258000001"
PHONE_TO_BRIDGE_UUID = "7c0a0002-6f4b-4a42-9d47-575258000001"
BRIDGE_TO_PHONE_UUID = "7c0a0003-6f4b-4a42-9d47-575258000001"

PROTOCOL_VERSION = 1
HEADER_SIZE = 6
MAX_PACKET_SIZE = 20
MAX_FRAGMENT_PAYLOAD = MAX_PACKET_SIZE - HEADER_SIZE
MAX_MESSAGE_SIZE = 256
MAX_PENDING_MESSAGES = 16

TYPE_INPUT_REPORT = 0x01
TYPE_OUTPUT_REPORT = 0x02
TYPE_STATUS = 0x03
TYPE_CONTROL = 0x04

CONTROL_START_WII_PAIRING = 0x01
CONTROL_STOP_WII_PAIRING = 0x02
CONTROL_CLEAR_WII_BOND = 0x03

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


@dataclass
class Evidence:
    command: str
    started_unix_ms: int = field(default_factory=lambda: int(time.time() * 1000))
    host_os: str = field(default_factory=platform.platform)
    bridge_name: Optional[str] = None
    firmware_version: Optional[str] = None
    protocol_version: int = PROTOCOL_VERSION
    wii_states: list[str] = field(default_factory=list)
    output_report_count: int = 0
    malformed_packet_count: int = 0
    reconnect_cycles: int = 0
    input_reports_sent: int = 0
    result: str = "RUNNING"
    error: Optional[str] = None

    def write(self, path: Optional[Path]) -> None:
        if path is None:
            return
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(self.__dict__, indent=2, sort_keys=True) + "\n", encoding="utf-8")


class BridgeProbe:
    def __init__(self, evidence: Evidence) -> None:
        self.pending: Dict[int, PendingMessage] = {}
        self.ready = asyncio.Event()
        self.wii_connected = asyncio.Event()
        self.output_report = asyncio.Event()
        self.bridge_error: Optional[int] = None
        self.firmware_version: Optional[str] = None
        self.evidence = evidence

    def malformed(self, message: str) -> None:
        self.evidence.malformed_packet_count += 1
        print(f"IGNORED {message}")

    def notification(self, _sender, data: bytearray) -> None:
        packet = bytes(data)
        if len(packet) < HEADER_SIZE or len(packet) > MAX_PACKET_SIZE:
            self.malformed(f"malformed packet length={len(packet)}")
            return
        if packet[0] != PROTOCOL_VERSION:
            self.malformed(f"incompatible packet protocol={packet[0]}")
            return

        message_type = packet[1]
        sequence = packet[2] | (packet[3] << 8)
        fragment_index = packet[4]
        fragment_count = packet[5]
        if fragment_count == 0 or fragment_index >= fragment_count:
            self.malformed("impossible fragment metadata")
            return

        if sequence not in self.pending and len(self.pending) >= MAX_PENDING_MESSAGES:
            oldest = next(iter(self.pending))
            self.pending.pop(oldest, None)
            self.malformed(f"reassembly window full; evicted seq={oldest}")

        pending = self.pending.setdefault(
            sequence,
            PendingMessage(message_type=message_type, fragment_count=fragment_count),
        )
        if pending.message_type != message_type or pending.fragment_count != fragment_count:
            self.pending.pop(sequence, None)
            self.malformed(f"conflicting sequence metadata seq={sequence}")
            return

        existing = pending.fragments.get(fragment_index)
        fragment = packet[HEADER_SIZE:]
        if existing is not None and existing != fragment:
            self.pending.pop(sequence, None)
            self.malformed(f"conflicting duplicate fragment seq={sequence} index={fragment_index}")
            return

        pending.fragments[fragment_index] = fragment
        if len(pending.fragments) != pending.fragment_count:
            return

        payload = b"".join(pending.fragments[index] for index in range(fragment_count))
        self.pending.pop(sequence, None)
        if len(payload) > MAX_MESSAGE_SIZE:
            self.malformed(f"reassembled payload too large: {len(payload)}")
            return
        self.handle_message(message_type, payload)

    def handle_message(self, message_type: int, payload: bytes) -> None:
        if message_type == TYPE_OUTPUT_REPORT:
            self.evidence.output_report_count += 1
            self.output_report.set()
            if payload:
                print(f"OUTPUT_REPORT id=0x{payload[0]:02X} payload={payload[1:].hex(' ').upper()}")
            return

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
                self.evidence.firmware_version = self.firmware_version
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
            name = names.get(value, f"UNKNOWN({value})")
            self.evidence.wii_states.append(name)
            print(f"WII_CONNECTION {name}")
            if value == WII_CONNECTED:
                self.wii_connected.set()
            return

        if status == STATUS_ERROR:
            self.bridge_error = value
            print(f"BRIDGE_ERROR code=0x{value:02X}", file=sys.stderr)


def encode_message(message_type: int, sequence: int, payload: bytes) -> list[bytes]:
    if not 0 <= sequence <= 0xFFFF:
        raise ValueError("sequence outside uint16")
    if len(payload) > MAX_MESSAGE_SIZE:
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


async def send_message(client: BleakClient, message_type: int, sequence: int, payload: bytes) -> int:
    for packet in encode_message(message_type, sequence, payload):
        await client.write_gatt_char(PHONE_TO_BRIDGE_UUID, packet, response=True)
    return (sequence + 1) & 0xFFFF


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


async def scan(timeout: float, evidence: Evidence) -> int:
    device = await find_bridge(timeout)
    evidence.bridge_name = device.name or "unknown"
    print(f"FOUND name={device.name or 'unknown'} address={device.address}")
    return 0


async def wait_ready(probe: BridgeProbe, timeout: float) -> None:
    try:
        await asyncio.wait_for(probe.ready.wait(), timeout=timeout)
    except asyncio.TimeoutError as error:
        raise RuntimeError("Timed out waiting for BRIDGE_READY") from error
    if probe.bridge_error is not None:
        raise RuntimeError(f"Bridge reported error 0x{probe.bridge_error:X}")


async def pair_wii(client: BleakClient, probe: BridgeProbe, sequence: int, timeout: float) -> int:
    sequence = await send_message(
        client,
        TYPE_CONTROL,
        sequence,
        bytes([CONTROL_START_WII_PAIRING]),
    )
    print("PAIRING_REQUEST_SENT — press the Wii red SYNC button now")
    try:
        await asyncio.wait_for(probe.wii_connected.wait(), timeout=timeout)
    except asyncio.TimeoutError as error:
        raise RuntimeError("Timed out waiting for physical Wii connection") from error
    if probe.bridge_error is not None:
        raise RuntimeError(f"Bridge reported error 0x{probe.bridge_error:X}")
    return sequence


async def send_a_smoke(
    client: BleakClient,
    evidence: Evidence,
    sequence: int,
    repeat: int,
    hold_seconds: float,
) -> int:
    neutral = bytes([0x30, 0x00, 0x00])
    a_down = bytes([0x30, 0x00, 0x08])
    sequence = await send_message(client, TYPE_INPUT_REPORT, sequence, neutral)
    evidence.input_reports_sent += 1
    for index in range(repeat):
        sequence = await send_message(client, TYPE_INPUT_REPORT, sequence, a_down)
        evidence.input_reports_sent += 1
        print(f"A_DOWN {index + 1}/{repeat} report=0x30 payload=00 08")
        await asyncio.sleep(hold_seconds)
        sequence = await send_message(client, TYPE_INPUT_REPORT, sequence, neutral)
        evidence.input_reports_sent += 1
        print(f"A_UP   {index + 1}/{repeat} report=0x30 payload=00 00")
        await asyncio.sleep(hold_seconds)
    print("INPUT_SMOKE_SENT — confirm the Wii Menu visibly reacted before marking Gate 1 PASS")
    return sequence


async def run_probe(
    command: str,
    scan_timeout: float,
    ready_timeout: float,
    pair_timeout: float,
    output_timeout: float,
    repeat: int,
    hold_seconds: float,
    evidence: Evidence,
) -> int:
    device = await find_bridge(scan_timeout)
    evidence.bridge_name = device.name or "unknown"
    print(f"CONNECTING name={device.name or 'unknown'} address={device.address}")

    probe = BridgeProbe(evidence)
    sequence = 1
    async with BleakClient(device) as client:
        print("BLE_CONNECTED")
        await client.start_notify(BRIDGE_TO_PHONE_UUID, probe.notification)
        await wait_ready(probe, ready_timeout)

        if command == "probe":
            print("PROBE_PASS")
            return 0

        sequence = await pair_wii(client, probe, sequence, pair_timeout)
        print("PAIR_PASS")
        if command == "pair":
            return 0

        if command == "report-smoke":
            try:
                await asyncio.wait_for(probe.output_report.wait(), timeout=output_timeout)
                print("HOST_OUTPUT_PATH_OBSERVED")
            except asyncio.TimeoutError:
                print("WARN no Wii OUTPUT_REPORT observed before input smoke", file=sys.stderr)
            await send_a_smoke(client, evidence, sequence, repeat, hold_seconds)
            print("REPORT_SMOKE_PASS transport_delivery=sent visual_confirmation=required")
            return 0

        raise ValueError(f"unsupported probe command: {command}")


async def run_reconnect(
    scan_timeout: float,
    ready_timeout: float,
    cycles: int,
    delay: float,
    evidence: Evidence,
) -> int:
    device = await find_bridge(scan_timeout)
    evidence.bridge_name = device.name or "unknown"
    for cycle in range(1, cycles + 1):
        probe = BridgeProbe(evidence)
        print(f"RECONNECT cycle={cycle}/{cycles} connecting")
        async with BleakClient(device) as client:
            await client.start_notify(BRIDGE_TO_PHONE_UUID, probe.notification)
            await wait_ready(probe, ready_timeout)
            print(f"RECONNECT cycle={cycle}/{cycles} ready")
        evidence.reconnect_cycles = cycle
        if cycle != cycles:
            await asyncio.sleep(delay)
    print(f"RECONNECT_PASS cycles={cycles}")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Local WiiRemoteX ESP32 BLE hardware-in-the-loop smoke test",
    )
    parser.add_argument(
        "command",
        choices=("scan", "probe", "pair", "report-smoke", "reconnect"),
    )
    parser.add_argument("--scan-timeout", type=float, default=10.0)
    parser.add_argument("--ready-timeout", type=float, default=10.0)
    parser.add_argument("--pair-timeout", type=float, default=60.0)
    parser.add_argument("--output-timeout", type=float, default=5.0)
    parser.add_argument("--repeat", type=int, default=3)
    parser.add_argument("--button-hold", type=float, default=0.15)
    parser.add_argument("--cycles", type=int, default=5)
    parser.add_argument("--cycle-delay", type=float, default=0.5)
    parser.add_argument("--evidence", type=Path)
    return parser.parse_args()


async def async_main(args: argparse.Namespace, evidence: Evidence) -> int:
    if args.command == "scan":
        return await scan(args.scan_timeout, evidence)
    if args.command == "reconnect":
        if args.cycles < 1:
            raise ValueError("--cycles must be >= 1")
        return await run_reconnect(
            scan_timeout=args.scan_timeout,
            ready_timeout=args.ready_timeout,
            cycles=args.cycles,
            delay=args.cycle_delay,
            evidence=evidence,
        )
    if args.repeat < 1:
        raise ValueError("--repeat must be >= 1")
    return await run_probe(
        command=args.command,
        scan_timeout=args.scan_timeout,
        ready_timeout=args.ready_timeout,
        pair_timeout=args.pair_timeout,
        output_timeout=args.output_timeout,
        repeat=args.repeat,
        hold_seconds=args.button_hold,
        evidence=evidence,
    )


def main() -> int:
    args = parse_args()
    evidence = Evidence(command=args.command)
    try:
        result = asyncio.run(async_main(args, evidence))
        evidence.result = "PASS"
        return result
    except KeyboardInterrupt:
        evidence.result = "INTERRUPTED"
        evidence.error = "KeyboardInterrupt"
        print("Interrupted", file=sys.stderr)
        return 130
    except Exception as error:
        evidence.result = "FAIL"
        evidence.error = str(error)
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    finally:
        evidence.write(args.evidence)


if __name__ == "__main__":
    raise SystemExit(main())
