import tempfile
import unittest
from pathlib import Path

import bridge_smoke


class BridgeSmokeTest(unittest.TestCase):
    def test_encode_single_input_report(self):
        packets = bridge_smoke.encode_message(
            bridge_smoke.TYPE_INPUT_REPORT,
            sequence=0x1234,
            payload=bytes([0x30, 0x00, 0x08]),
        )
        self.assertEqual(1, len(packets))
        self.assertEqual(
            bytes([1, 1, 0x34, 0x12, 0, 1, 0x30, 0x00, 0x08]),
            packets[0],
        )

    def test_encode_fragmentation_stays_within_ble_packet_limit(self):
        packets = bridge_smoke.encode_message(
            bridge_smoke.TYPE_INPUT_REPORT,
            sequence=7,
            payload=bytes(range(64)),
        )
        self.assertGreater(len(packets), 1)
        self.assertTrue(all(len(packet) <= bridge_smoke.MAX_PACKET_SIZE for packet in packets))

    def test_probe_reassembles_out_of_order_status(self):
        evidence = bridge_smoke.Evidence(command="test")
        probe = bridge_smoke.BridgeProbe(evidence)
        payload = bytes([bridge_smoke.STATUS_BRIDGE_READY, bridge_smoke.PROTOCOL_VERSION, 0, 7, 1])
        packets = bridge_smoke.encode_message(
            bridge_smoke.TYPE_STATUS,
            sequence=5,
            payload=payload + bytes(range(30)),
        )
        for packet in reversed(packets):
            probe.notification(None, bytearray(packet))
        self.assertTrue(probe.ready.is_set())
        self.assertEqual("0.7.1", probe.firmware_version)
        self.assertEqual(0, evidence.malformed_packet_count)

    def test_probe_rejects_conflicting_duplicate_fragment(self):
        evidence = bridge_smoke.Evidence(command="test")
        probe = bridge_smoke.BridgeProbe(evidence)
        first = bytes([1, bridge_smoke.TYPE_STATUS, 1, 0, 0, 2]) + b"abc"
        conflicting = bytes([1, bridge_smoke.TYPE_STATUS, 1, 0, 0, 2]) + b"xyz"
        probe.notification(None, bytearray(first))
        probe.notification(None, bytearray(conflicting))
        self.assertEqual(1, evidence.malformed_packet_count)
        self.assertNotIn(1, probe.pending)

    def test_reassembly_window_is_bounded(self):
        evidence = bridge_smoke.Evidence(command="test")
        probe = bridge_smoke.BridgeProbe(evidence)
        for sequence in range(bridge_smoke.MAX_PENDING_MESSAGES + 3):
            packet = bytes([1, bridge_smoke.TYPE_STATUS, sequence, 0, 0, 2]) + b"x"
            probe.notification(None, bytearray(packet))
        self.assertLessEqual(len(probe.pending), bridge_smoke.MAX_PENDING_MESSAGES)
        self.assertGreaterEqual(evidence.malformed_packet_count, 3)

    def test_evidence_file_excludes_bluetooth_address(self):
        evidence = bridge_smoke.Evidence(command="probe", bridge_name="WiiRemoteX")
        evidence.result = "PASS"
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "evidence.json"
            evidence.write(path)
            text = path.read_text(encoding="utf-8")
        self.assertIn('"result": "PASS"', text)
        self.assertNotIn("address", text.lower())

    def test_oversized_message_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "maximum"):
            bridge_smoke.encode_message(
                bridge_smoke.TYPE_INPUT_REPORT,
                1,
                bytes(bridge_smoke.MAX_MESSAGE_SIZE + 1),
            )


if __name__ == "__main__":
    unittest.main()
