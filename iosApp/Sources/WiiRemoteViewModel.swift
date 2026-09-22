import Combine
import Foundation
import UIKit
import WiiRemoteShared

@MainActor
final class WiiRemoteViewModel: ObservableObject {
    @Published private(set) var bridgeState = "Idle"
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
    private var reportTimer: Timer?
    private var cPressed = false
    private var zPressed = false
    private var previousRumble = false

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

        UIDevice.current.isBatteryMonitoringEnabled = true
        updateBattery()
        motion.start()
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
        send(engine.startWiiPairingPackets())
        appendDiagnostic("Requested Wii pairing mode on ESP32")
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
        guard data.count <= 20 else {
            appendDiagnostic("Rejected BLE packet larger than protocol maximum")
            return
        }

        send(engine.acceptBridgePacket(packet: KotlinByteArray(data: data)))
        refreshSharedState()
    }

    private func send(_ frames: [KotlinByteArray]) {
        guard !frames.isEmpty else { return }
        frames.forEach { bridge.send($0.data) }
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

        wiiState = engine.wiiConnectionStateLabel

        let rumble = engine.rumbleEnabled
        if rumble != previousRumble {
            previousRumble = rumble
            if rumble {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            }
        }
    }

    private func appendDiagnostic(_ message: String) {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss.SSS"
        diagnostics.append("\(formatter.string(from: Date()))  \(message)")
        diagnostics = Array(diagnostics.suffix(40))
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
