import Combine
import Foundation
import UIKit
import WiiRemoteShared

@MainActor
final class WiiRemoteViewModel: ObservableObject {
    @Published private(set) var bridgeState = "Idle"
    @Published private(set) var bridgeProtocolState = "Waiting"
    @Published private(set) var bridgeFirmwareVersion = "Unknown"
    @Published private(set) var canPairWii = false
    @Published private(set) var canStopWiiPairing = false
    @Published private(set) var wiiState = "Disconnected"
    @Published private(set) var reportMode = "0x30"
    @Published private(set) var diagnostics: [String] = []

    @Published var irEnabled = false
    @Published var motionPointerEnabled = false
    @Published var nunchukEnabled = false
    @Published var motionPlusEnabled = false
    @Published var nunchukX: Double = 0
    @Published var nunchukY: Double = 0

    private let engine = IosWiimoteEngine()
    private let bridge = BLEBridgeTransport()
    private let motion = MotionInput()
    private let traceRecorder = IOSHardwareTraceRecorder()
    private var reportTimer: Timer?
    private var cPressed = false
    private var zPressed = false
    private var previousRumble = false
    private var previousBridgeErrorCode: Int32 = 0

    init() {
        bridge.onDiagnostic = { [weak self] message in
            self?.appendDiagnostic(message)
        }

        bridge.onPacket = { [weak self] data in
            self?.handleBridgePacket(data)
        }

        motion.onSample = { [weak self] sample in
            self?.handleMotion(sample)
        }

        bridge.$state
            .receive(on: DispatchQueue.main)
            .sink { [weak self] state in
                self?.bridgeState = Self.label(for: state)
                if state == .connected {
                    self?.startContinuousReports()
                } else {
                    self?.stopContinuousReports()
                    self?.engine.resetBridgeSession()
                }
                self?.refreshSharedState()
            }
            .store(in: &cancellables)

        restoreCalibration()
        UIDevice.current.isBatteryMonitoringEnabled = true
        updateBattery()
        motion.start()
        recordTrace(direction: "SYS", event: "iOS runtime started")
    }

    deinit {
        reportTimer?.invalidate()
        motion.stop()
    }

    private var cancellables = Set<AnyCancellable>()

    func connectBridge() {
        bridge.connect()
    }

    func disconnectBridge() {
        bridge.disconnect()
    }

    func startWiiPairing() {
        guard engine.bridgeProtocolCompatible else {
            appendDiagnostic("ESP32 bridge protocol is not ready")
            return
        }

        send(engine.startWiiPairingPackets())
        appendDiagnostic("Requested Wii pairing mode on ESP32")
    }

    func stopWiiPairing() {
        send(engine.stopWiiPairingPackets())
        appendDiagnostic("Requested Wii pairing stop on ESP32")
    }

    func clearWiiBond() {
        send(engine.clearWiiBondPackets())
        appendDiagnostic("Requested Wii bond reset on ESP32")
    }

    func button(_ name: String, pressed: Bool) {
        send(engine.buttonChanged(buttonName: name, pressed: pressed))
        refreshSharedState()
    }

    func setIrEnabled(_ enabled: Bool) {
        irEnabled = enabled
        send(engine.setIrEnabled(enabled: enabled))
        refreshSharedState()
    }

    func setIrPointer(x: Float, y: Float) {
        send(engine.setIrPointer(normalizedX: x, normalizedY: y, enabled: true))
    }

    func setMotionPointerEnabled(_ enabled: Bool) {
        motionPointerEnabled = enabled
        let sample = motion.latestSample
        send(
            engine.setMotionPointerEnabled(
                enabled: enabled,
                currentYawRadians: sample?.yawRadians ?? 0,
                currentPitchRadians: sample?.pitchRadians ?? 0
            )
        )
        refreshSharedState()
    }

    func recenterMotionPointer() {
        guard let sample = motion.latestSample else { return }

        engine.updateGyroBias(
            xRadPerSec: sample.gyroXRadPerSec,
            yRadPerSec: sample.gyroYRadPerSec,
            zRadPerSec: sample.gyroZRadPerSec
        )
        persistGyroBias(sample)

        send(
            engine.recenterMotionPointer(
                currentYawRadians: sample.yawRadians,
                currentPitchRadians: sample.pitchRadians
            )
        )

        appendDiagnostic("Motion pointer + gyro bias recentered")
    }

    func setNunchukEnabled(_ enabled: Bool) {
        nunchukEnabled = enabled
        send(engine.setNunchukEnabled(enabled: enabled))
        refreshSharedState()
    }

    func updateNunchukStick() {
        send(
            engine.setNunchukStick(
                normalizedX: Float(nunchukX),
                normalizedY: Float(nunchukY)
            )
        )
    }

    func nunchukButton(_ name: String, pressed: Bool) {
        if name == "C" {
            cPressed = pressed
        } else if name == "Z" {
            zPressed = pressed
        }

        send(engine.setNunchukButtons(cPressed: cPressed, zPressed: zPressed))
    }

    func setMotionPlusEnabled(_ enabled: Bool) {
        motionPlusEnabled = enabled
        send(engine.setMotionPlusEnabled(enabled: enabled))
        refreshSharedState()
    }

    private func handleMotion(_ sample: MotionSample) {
        send(
            engine.physicalMotionChanged(
                accelerationXG: sample.accelerationXG,
                accelerationYG: sample.accelerationYG,
                accelerationZG: sample.accelerationZG,
                gyroXRadPerSec: sample.gyroXRadPerSec,
                gyroYRadPerSec: sample.gyroYRadPerSec,
                gyroZRadPerSec: sample.gyroZRadPerSec
            )
        )

        if motionPointerEnabled {
            send(
                engine.orientationChanged(
                    yawRadians: sample.yawRadians,
                    pitchRadians: sample.pitchRadians
                )
            )
        }
    }

    private func handleBridgePacket(_ data: Data) {
        recordTrace(
            direction: "RX",
            event: bridgePacketEvent(data),
            packet: data
        )

        guard data.count <= 20 else {
            appendDiagnostic("Rejected BLE packet larger than protocol maximum")
            return
        }

        send(engine.acceptBridgePacket(packet: KotlinByteArray(data: data)))
        refreshSharedState()
    }

    private func send(_ frames: [KotlinByteArray]) {
        guard !frames.isEmpty else { return }

        frames.forEach { frame in
            let data = frame.data
            recordTrace(
                direction: "TX",
                event: bridgePacketEvent(data),
                packet: data
            )
            bridge.send(data)
        }
    }

    private func startContinuousReports() {
        stopContinuousReports()
        reportTimer = Timer.scheduledTimer(withTimeInterval: 0.01, repeats: true) {
            [weak self] _ in
            guard let self else { return }
            Task { @MainActor in
                self.send(self.engine.continuousTick())
            }
        }
    }

    private func stopContinuousReports() {
        reportTimer?.invalidate()
        reportTimer = nil
    }

    private func updateBattery() {
        let battery = UIDevice.current.batteryLevel
        let wiiByte =
            battery >= 0
                ? Int32((battery * 0xC8).rounded())
                : Int32(0xC0)
        engine.batteryLevelChanged(level: wiiByte)
    }

    private func refreshSharedState() {
        reportMode = String(format: "0x%02X", engine.reportMode)
        bridgeProtocolState = engine.bridgeProtocolStatusLabel
        bridgeFirmwareVersion =
            engine.bridgeFirmwareVersion.isEmpty
                ? "Unknown"
                : engine.bridgeFirmwareVersion
        canPairWii =
            engine.bridgeProtocolCompatible &&
            engine.wiiConnectionState == 0
        canStopWiiPairing =
            engine.bridgeProtocolCompatible &&
            engine.wiiConnectionState == 1
        wiiState = engine.wiiConnectionStateLabel

        let bridgeErrorCode = engine.bridgeErrorCode
        if bridgeErrorCode != previousBridgeErrorCode {
            previousBridgeErrorCode = bridgeErrorCode
            if bridgeErrorCode != 0 {
                appendDiagnostic(
                    String(format: "ESP32 bridge error 0x%02X", bridgeErrorCode)
                )
            }
        }

        let rumble = engine.rumbleEnabled
        if rumble != previousRumble {
            previousRumble = rumble
            if rumble {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            }
        }
    }

    func hardwareTraceJSON() -> String {
        traceRecorder.json()
    }

    func clearHardwareTrace() {
        traceRecorder.clear()
        appendDiagnostic("Hardware trace cleared")
        recordTrace(direction: "SYS", event: "hardware trace cleared")
    }

    private func restoreCalibration() {
        let defaults = UserDefaults.standard
        guard defaults.object(forKey: CalibrationKey.gyroBiasX) != nil else {
            return
        }

        engine.updateGyroBias(
            xRadPerSec: Float(defaults.double(forKey: CalibrationKey.gyroBiasX)),
            yRadPerSec: Float(defaults.double(forKey: CalibrationKey.gyroBiasY)),
            zRadPerSec: Float(defaults.double(forKey: CalibrationKey.gyroBiasZ))
        )
    }

    private func persistGyroBias(_ sample: MotionSample) {
        let defaults = UserDefaults.standard
        defaults.set(Double(sample.gyroXRadPerSec), forKey: CalibrationKey.gyroBiasX)
        defaults.set(Double(sample.gyroYRadPerSec), forKey: CalibrationKey.gyroBiasY)
        defaults.set(Double(sample.gyroZRadPerSec), forKey: CalibrationKey.gyroBiasZ)
    }

    private func recordTrace(
        direction: String,
        event: String,
        packet: Data? = nil
    ) {
        traceRecorder.record(
            direction: direction,
            event: event,
            packet: packet,
            connectionState: wiiState,
            reportMode: reportMode,
            nunchukConnected: engine.nunchukConnected,
            irEnabled: engine.infraredEnabled,
            motionPlusPresent: engine.motionPlusPresent
        )
    }

    private func bridgePacketEvent(_ data: Data) -> String {
        guard data.count >= 2 else { return "invalid_bridge_packet" }

        switch data[data.index(data.startIndex, offsetBy: 1)] {
        case 0x01: return "input_report_fragment"
        case 0x02: return "output_report_fragment"
        case 0x03: return "status_fragment"
        case 0x04: return "control_fragment"
        default: return "unknown_bridge_fragment"
        }
    }

    private func appendDiagnostic(_ message: String) {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss.SSS"
        diagnostics.append("\(formatter.string(from: Date()))  \(message)")
        diagnostics = Array(diagnostics.suffix(40))
    }

    private enum CalibrationKey {
        static let gyroBiasX = "wiiremotex.gyro_bias_x_rad_s"
        static let gyroBiasY = "wiiremotex.gyro_bias_y_rad_s"
        static let gyroBiasZ = "wiiremotex.gyro_bias_z_rad_s"
    }

    private static func label(for state: BLEBridgeTransport.State) -> String {
        switch state {
        case .bluetoothUnavailable: return "Bluetooth unavailable"
        case .idle: return "Idle"
        case .scanning: return "Scanning"
        case .connecting: return "Connecting"
        case .connected: return "Connected"
        }
    }
}
