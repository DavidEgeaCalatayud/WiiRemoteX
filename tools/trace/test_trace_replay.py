import json
import tempfile
import unittest
from pathlib import Path

import trace_replay


def event(direction, report_id, payload="", elapsed=0, name="interrupt_report"):
    return {
        "timestamp_ns": elapsed,
        "elapsed_realtime_ns": elapsed,
        "direction": direction,
        "transport": "test",
        "event": name,
        "report_id": report_id,
        "payload_hex": payload,
        "connection_state": "CONNECTED",
        "report_mode": 0x30,
        "extension_state": "NONE",
        "ir_state": "OFF",
        "motion_plus_state": "OFF",
    }


def trace(events):
    return {
        "schema": "wiiremotex-hardware-trace-v1",
        "device": {"manufacturer": "test", "model": "test", "api": 1},
        "events": events,
    }


class TraceReplayTest(unittest.TestCase):
    def test_handshake_accepts_known_wii_conversation(self):
        data = trace(
            [
                event("RX", 0x15, elapsed=1),
                event("TX", 0x20, "00 00", elapsed=2),
                event("RX", 0x12, "00 30", elapsed=3),
                event("TX", 0x30, "00 00", elapsed=4),
                event("TX", 0x30, "00 08", elapsed=5),
            ]
        )
        events = trace_replay.protocol_events(data)
        trace_replay.require_handshake(events)

    def test_handshake_rejects_missing_continuous_report(self):
        data = trace(
            [
                event("RX", 0x15, elapsed=1),
                event("TX", 0x20, elapsed=2),
                event("RX", 0x12, elapsed=3),
            ]
        )
        with self.assertRaisesRegex(ValueError, "continuous input report"):
            trace_replay.require_handshake(trace_replay.protocol_events(data))

    def test_compare_allows_extra_candidate_events(self):
        reference = trace_replay.protocol_events(
            trace(
                [
                    event("RX", 0x15, elapsed=1),
                    event("TX", 0x20, elapsed=2),
                    event("RX", 0x12, elapsed=3),
                    event("TX", 0x30, "00 08", elapsed=4),
                ]
            )
        )
        candidate = trace_replay.protocol_events(
            trace(
                [
                    event("RX", 0x15, elapsed=1),
                    event("TX", 0x20, elapsed=2),
                    event("TX", 0x30, "00 00", elapsed=3),
                    event("RX", 0x12, elapsed=4),
                    event("TX", 0x30, "00 08", elapsed=5),
                    event("TX", 0x30, "00 00", elapsed=6),
                ]
            )
        )
        matched, _, missing = trace_replay.compare_as_subsequence(
            reference,
            candidate,
            payload_mode="exact",
        )
        self.assertTrue(matched)
        self.assertIsNone(missing)

    def test_exact_payload_detects_button_mismatch(self):
        reference = [trace_replay.ProtocolEvent("TX", 0x30, "00 08", "send_report")]
        candidate = [trace_replay.ProtocolEvent("TX", 0x30, "00 00", "send_report")]
        matched, index, missing = trace_replay.compare_as_subsequence(
            reference,
            candidate,
            payload_mode="exact",
        )
        self.assertFalse(matched)
        self.assertEqual(0, index)
        self.assertEqual(reference[0], missing)

    def test_collapse_continuous_only_collapses_exact_duplicates(self):
        data = trace(
            [
                event("TX", 0x30, "00 00", elapsed=1),
                event("TX", 0x30, "00 00", elapsed=2),
                event("TX", 0x30, "00 08", elapsed=3),
            ]
        )
        events = trace_replay.protocol_events(data, collapse_continuous=True)
        self.assertEqual(2, len(events))
        self.assertEqual("00 08", events[-1].payload_hex)

    def test_validation_rejects_time_travel(self):
        data = trace(
            [
                event("RX", 0x15, elapsed=2),
                event("TX", 0x20, elapsed=1),
            ]
        )
        with self.assertRaisesRegex(ValueError, "moved backwards"):
            trace_replay.validate_trace(data)

    def test_cli_load_roundtrip(self):
        data = trace([event("RX", 0x15, "01 02", elapsed=1)])
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "trace.json"
            path.write_text(json.dumps(data), encoding="utf-8")
            loaded = trace_replay.load_trace(path)
        self.assertEqual(data["schema"], loaded["schema"])


if __name__ == "__main__":
    unittest.main()
