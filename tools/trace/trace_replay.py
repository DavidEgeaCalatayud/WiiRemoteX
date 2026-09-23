#!/usr/bin/env python3

"""Validate and compare WiiRemoteX hardware traces.

The tool intentionally ignores wall-clock timing by default. Bluetooth scheduling,
Android/iOS callback timing and continuous report cadence make timestamps a poor
golden-test oracle. Protocol order and report identity are the stable contract.
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence

TRACE_SCHEMA_PREFIX = "wiiremotex-hardware-trace-v"
PROTOCOL_DIRECTIONS = {"RX", "TX", "HID_CONTROL"}
CONTINUOUS_REPORT_IDS = set(range(0x30, 0x40))


@dataclass(frozen=True)
class ProtocolEvent:
    direction: str
    report_id: int
    payload_hex: str
    event: str

    @property
    def short(self) -> str:
        payload = f" {self.payload_hex}" if self.payload_hex else ""
        return f"{self.direction} 0x{self.report_id:02X}{payload}"


def canonical_hex(value: object) -> str:
    if value is None:
        return ""
    if not isinstance(value, str):
        raise ValueError("payload_hex must be a string")
    compact = "".join(value.split()).upper()
    if len(compact) % 2 != 0:
        raise ValueError(f"payload_hex has odd length: {value!r}")
    try:
        bytes.fromhex(compact)
    except ValueError as error:
        raise ValueError(f"payload_hex is not hexadecimal: {value!r}") from error
    return " ".join(compact[index : index + 2] for index in range(0, len(compact), 2))


def load_trace(path: Path) -> dict:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"Unable to read trace {path}: {error}") from error
    validate_trace(data)
    return data


def validate_trace(trace: dict) -> None:
    if not isinstance(trace, dict):
        raise ValueError("trace root must be an object")
    schema = trace.get("schema")
    if not isinstance(schema, str) or not schema.startswith(TRACE_SCHEMA_PREFIX):
        raise ValueError(f"unsupported trace schema: {schema!r}")

    session_id = trace.get("session_id")
    if session_id is not None and (not isinstance(session_id, str) or not session_id.strip()):
        raise ValueError("session_id must be a non-empty string when present")

    events = trace.get("events")
    if not isinstance(events, list):
        raise ValueError("trace events must be an array")

    event_count = trace.get("event_count")
    if event_count is not None and event_count != len(events):
        raise ValueError(
            f"event_count mismatch: declared={event_count!r} actual={len(events)}"
        )

    previous_elapsed: int | None = None
    for index, event in enumerate(events):
        if not isinstance(event, dict):
            raise ValueError(f"events[{index}] must be an object")

        sequence = event.get("sequence")
        if sequence is not None and sequence != index:
            raise ValueError(
                f"events[{index}].sequence mismatch: expected={index} actual={sequence!r}"
            )

        direction = event.get("direction")
        if not isinstance(direction, str) or not direction:
            raise ValueError(f"events[{index}].direction must be a non-empty string")
        report_id = event.get("report_id")
        if report_id is not None and (not isinstance(report_id, int) or not 0 <= report_id <= 0xFF):
            raise ValueError(f"events[{index}].report_id must be null or uint8")
        canonical_hex(event.get("payload_hex", ""))

        elapsed = event.get("elapsed_realtime_ns")
        if elapsed is not None:
            if not isinstance(elapsed, int) or elapsed < 0:
                raise ValueError(f"events[{index}].elapsed_realtime_ns must be non-negative")
            if previous_elapsed is not None and elapsed < previous_elapsed:
                raise ValueError(
                    f"events[{index}].elapsed_realtime_ns moved backwards "
                    f"({elapsed} < {previous_elapsed})"
                )
            previous_elapsed = elapsed


def protocol_events(trace: dict, *, collapse_continuous: bool = False) -> list[ProtocolEvent]:
    result: list[ProtocolEvent] = []
    for raw in trace["events"]:
        report_id = raw.get("report_id")
        direction = raw.get("direction")
        if report_id is None or direction not in PROTOCOL_DIRECTIONS:
            continue
        event = ProtocolEvent(
            direction=direction,
            report_id=report_id,
            payload_hex=canonical_hex(raw.get("payload_hex", "")),
            event=str(raw.get("event", "")),
        )
        if (
            collapse_continuous
            and result
            and event.direction == "TX"
            and event.report_id in CONTINUOUS_REPORT_IDS
            and result[-1] == event
        ):
            continue
        result.append(event)
    return result


def event_matches(reference: ProtocolEvent, candidate: ProtocolEvent, payload_mode: str) -> bool:
    if reference.direction != candidate.direction or reference.report_id != candidate.report_id:
        return False
    if payload_mode == "exact":
        return reference.payload_hex == candidate.payload_hex
    return True


def compare_as_subsequence(
    reference: Sequence[ProtocolEvent],
    candidate: Sequence[ProtocolEvent],
    *,
    payload_mode: str,
) -> tuple[bool, int, ProtocolEvent | None]:
    candidate_index = 0
    for reference_index, expected in enumerate(reference):
        while candidate_index < len(candidate):
            actual = candidate[candidate_index]
            candidate_index += 1
            if event_matches(expected, actual, payload_mode):
                break
        else:
            return False, reference_index, expected
    return True, len(reference), None


def require_handshake(events: Sequence[ProtocolEvent]) -> None:
    required = [
        ("RX", 0x15, "Wii status request"),
        ("TX", 0x20, "Wii status reply"),
        ("RX", 0x12, "report mode selection"),
    ]
    cursor = 0
    for direction, report_id, label in required:
        while cursor < len(events):
            event = events[cursor]
            cursor += 1
            if event.direction == direction and event.report_id == report_id:
                break
        else:
            raise ValueError(f"missing required handshake event: {label} ({direction} 0x{report_id:02X})")

    if not any(
        event.direction == "TX" and event.report_id in CONTINUOUS_REPORT_IDS
        for event in events[cursor:]
    ):
        raise ValueError("missing continuous input report after report-mode selection")


def summarize(events: Iterable[ProtocolEvent]) -> str:
    materialized = list(events)
    counts = Counter((event.direction, event.report_id) for event in materialized)
    lines = [f"protocol_events={len(materialized)}"]
    for (direction, report_id), count in sorted(counts.items()):
        lines.append(f"{direction} 0x{report_id:02X}: {count}")
    return "\n".join(lines)


def command_validate(args: argparse.Namespace) -> int:
    trace = load_trace(args.trace)
    events = protocol_events(trace, collapse_continuous=args.collapse_continuous)
    if args.require_handshake:
        require_handshake(events)
    print(f"TRACE_VALID schema={trace['schema']} events={len(trace['events'])}")
    print(summarize(events))
    return 0


def command_compare(args: argparse.Namespace) -> int:
    reference = protocol_events(
        load_trace(args.reference),
        collapse_continuous=args.collapse_continuous,
    )
    candidate = protocol_events(
        load_trace(args.candidate),
        collapse_continuous=args.collapse_continuous,
    )
    if args.require_handshake:
        require_handshake(candidate)

    matched, index, missing = compare_as_subsequence(
        reference,
        candidate,
        payload_mode=args.payload_mode,
    )
    if not matched:
        assert missing is not None
        print(
            f"TRACE_MISMATCH reference_index={index} missing={missing.short}",
            file=sys.stderr,
        )
        return 2

    print(
        "TRACE_MATCH "
        f"reference_events={len(reference)} candidate_events={len(candidate)} "
        f"payload_mode={args.payload_mode}"
    )
    return 0


def command_summary(args: argparse.Namespace) -> int:
    trace = load_trace(args.trace)
    print(summarize(protocol_events(trace, collapse_continuous=args.collapse_continuous)))
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate/replay WiiRemoteX hardware traces")
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate = subparsers.add_parser("validate", help="validate schema and optional Wii handshake")
    validate.add_argument("trace", type=Path)
    validate.add_argument("--require-handshake", action="store_true")
    validate.add_argument("--collapse-continuous", action="store_true")
    validate.set_defaults(handler=command_validate)

    compare = subparsers.add_parser("compare", help="compare a reference trace against a candidate")
    compare.add_argument("reference", type=Path)
    compare.add_argument("candidate", type=Path)
    compare.add_argument("--payload-mode", choices=("report-id", "exact"), default="report-id")
    compare.add_argument("--require-handshake", action="store_true")
    compare.add_argument("--collapse-continuous", action="store_true")
    compare.set_defaults(handler=command_compare)

    summary = subparsers.add_parser("summary", help="print normalized protocol report counts")
    summary.add_argument("trace", type=Path)
    summary.add_argument("--collapse-continuous", action="store_true")
    summary.set_defaults(handler=command_summary)

    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        return args.handler(args)
    except ValueError as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
