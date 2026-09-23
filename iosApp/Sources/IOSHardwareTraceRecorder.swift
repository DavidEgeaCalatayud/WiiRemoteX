import Darwin
import Foundation
import UIKit

struct IOSHardwareTraceEvent: Codable {
    let timestampNs: UInt64
    let elapsedRealtimeNs: UInt64
    let direction: String
    let transport: String
    let event: String
    let reportId: Int?
    let reportIdHex: String?
    let payloadHex: String
    let connectionState: String
    let reportMode: String
    let extensionState: String
    let irState: String
    let motionPlusState: String

    enum CodingKeys: String, CodingKey {
        case timestampNs = "timestamp_ns"
        case elapsedRealtimeNs = "elapsed_realtime_ns"
        case direction
        case transport
        case event
        case reportId = "report_id"
        case reportIdHex = "report_id_hex"
        case payloadHex = "payload_hex"
        case connectionState = "connection_state"
        case reportMode = "report_mode"
        case extensionState = "extension_state"
        case irState = "ir_state"
        case motionPlusState = "motion_plus_state"
    }
}

struct IOSHardwareTraceEnvelope: Codable {
    struct Device: Codable {
        let platform: String
        let model: String
        let systemName: String
        let systemVersion: String

        enum CodingKeys: String, CodingKey {
            case platform
            case model
            case systemName = "system_name"
            case systemVersion = "system_version"
        }
    }

    let schema: String
    let device: Device
    let events: [IOSHardwareTraceEvent]
}

@MainActor
final class IOSHardwareTraceRecorder {
    private var events: [IOSHardwareTraceEvent] = []
    private let maximumEvents = 8_000

    func clear() {
        events.removeAll(keepingCapacity: true)
    }

    func record(
        direction: String,
        event: String,
        packet: Data? = nil,
        connectionState: String,
        reportMode: String,
        nunchukConnected: Bool,
        irEnabled: Bool,
        motionPlusPresent: Bool
    ) {
        let metadata = Self.bridgeMetadata(packet)

        events.append(
            IOSHardwareTraceEvent(
                timestampNs: UInt64(Date().timeIntervalSince1970 * 1_000_000_000),
                elapsedRealtimeNs: DispatchTime.now().uptimeNanoseconds,
                direction: direction,
                transport: "ios-corebluetooth-esp32",
                event: event,
                reportId: metadata.reportId,
                reportIdHex: metadata.reportId.map {
                    String(format: "0x%02X", $0)
                },
                payloadHex: metadata.payload.hexString,
                connectionState: connectionState,
                reportMode: reportMode,
                extensionState: "nunchuk.connected=\(nunchukConnected)",
                irState: "enabled=\(irEnabled)",
                motionPlusState: "present=\(motionPlusPresent)"
            )
        )

        if events.count > maximumEvents {
            events.removeFirst(events.count - maximumEvents)
        }
    }

    func json() -> String {
        let device = IOSHardwareTraceEnvelope.Device(
            platform: "iOS",
            model: Self.deviceModelIdentifier(),
            systemName: UIDevice.current.systemName,
            systemVersion: UIDevice.current.systemVersion
        )

        let envelope = IOSHardwareTraceEnvelope(
            schema: "wiiremotex-ios-hardware-trace-v1",
            device: device,
            events: events
        )

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]

        guard
            let data = try? encoder.encode(envelope),
            let json = String(data: data, encoding: .utf8)
        else {
            return #"{"schema":"wiiremotex-ios-hardware-trace-v1","events":[]}"#
        }

        return json
    }

    private static func bridgeMetadata(_ packet: Data?) -> (reportId: Int?, payload: Data) {
        guard let packet, packet.count >= 6 else {
            return (nil, packet ?? Data())
        }

        let bytes = [UInt8](packet)
        let messageType = bytes[1]
        let fragmentIndex = bytes[4]

        guard
            fragmentIndex == 0,
            (messageType == 0x01 || messageType == 0x02),
            bytes.count >= 7
        else {
            return (nil, Data(bytes.dropFirst(6)))
        }

        let reportId = Int(bytes[6])
        return (reportId, Data(bytes.dropFirst(7)))
    }

    private static func deviceModelIdentifier() -> String {
        var systemInfo = utsname()
        uname(&systemInfo)

        return withUnsafePointer(to: &systemInfo.machine) {
            $0.withMemoryRebound(to: CChar.self, capacity: 1) {
                String(cString: $0)
            }
        }
    }
}

private extension Data {
    var hexString: String {
        map { String(format: "%02X", $0) }.joined(separator: " ")
    }
}
