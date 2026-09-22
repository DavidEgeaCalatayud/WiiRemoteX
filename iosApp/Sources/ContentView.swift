import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var model: WiiRemoteViewModel

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    connectionCard
                    remoteCard
                    irCard
                    nunchukCard
                    motionPlusCard
                    diagnosticsCard
                }
                .padding()
            }
            .navigationTitle("WiiRemoteX")
        }
    }

    private var connectionCard: some View {
        GroupBox("Bridge") {
            VStack(alignment: .leading, spacing: 10) {
                LabeledContent("iPhone ↔ ESP32", value: model.bridgeState)
                LabeledContent("Bridge protocol", value: model.bridgeProtocolState)
                LabeledContent("Firmware", value: model.bridgeFirmwareVersion)
                LabeledContent("ESP32 ↔ Wii", value: model.wiiState)
                LabeledContent("Report mode", value: model.reportMode)

                HStack {
                    Button("Connect ESP32") {
                        model.connectBridge()
                    }
                    .buttonStyle(.borderedProminent)

                    Button("Disconnect") {
                        model.disconnectBridge()
                    }
                    .buttonStyle(.bordered)
                }

                HStack {
                    Button("Pair Wii") {
                        model.startWiiPairing()
                    }
                    .disabled(!model.canPairWii)

                    Button("Stop pairing") {
                        model.stopWiiPairing()
                    }
                    .disabled(!model.canStopWiiPairing)
                }

                Button("Clear Wii bond", role: .destructive) {
                    model.clearWiiBond()
                }

                Text("The iPhone runs the Wii protocol engine. The ESP32 only bridges BLE to Bluetooth Classic HID.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var remoteCard: some View {
        GroupBox("Wii Remote") {
            VStack(spacing: 18) {
                VStack(spacing: 4) {
                    HoldButton("▲", button: "UP")
                    HStack(spacing: 4) {
                        HoldButton("◀", button: "LEFT")
                        HoldButton("●", button: "HOME")
                        HoldButton("▶", button: "RIGHT")
                    }
                    HoldButton("▼", button: "DOWN")
                }

                HStack(spacing: 30) {
                    HoldButton("B", button: "B", size: 64)
                    HoldButton("A", button: "A", size: 82)
                }

                HStack(spacing: 16) {
                    HoldButton("−", button: "MINUS")
                    HoldButton("HOME", button: "HOME", size: 72)
                    HoldButton("+", button: "PLUS")
                }

                HStack(spacing: 24) {
                    HoldButton("1", button: "ONE")
                    HoldButton("2", button: "TWO")
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
    }

    private var irCard: some View {
        GroupBox("Virtual IR") {
            VStack(spacing: 12) {
                Toggle(
                    "IR enabled",
                    isOn: Binding(
                        get: { model.irEnabled },
                        set: model.setIrEnabled
                    )
                )

                Toggle(
                    "Motion pointer",
                    isOn: Binding(
                        get: { model.motionPointerEnabled },
                        set: model.setMotionPointerEnabled
                    )
                )

                Button("Recenter motion + gyro") {
                    model.recenterMotionPointer()
                }

                GeometryReader { geometry in
                    RoundedRectangle(cornerRadius: 16)
                        .fill(.secondary.opacity(0.12))
                        .overlay {
                            Text("Drag to point")
                                .foregroundStyle(.secondary)
                        }
                        .contentShape(Rectangle())
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { value in
                                    let width = max(geometry.size.width, 1)
                                    let height = max(geometry.size.height, 1)
                                    model.setIrPointer(
                                        x: Float(value.location.x / width),
                                        y: Float(value.location.y / height)
                                    )
                                }
                        )
                }
                .frame(height: 180)
            }
        }
    }

    private var nunchukCard: some View {
        GroupBox("Nunchuk") {
            VStack(spacing: 12) {
                Toggle(
                    "Connected",
                    isOn: Binding(
                        get: { model.nunchukEnabled },
                        set: model.setNunchukEnabled
                    )
                )

                VStack {
                    Text("Stick X")
                    Slider(
                        value: $model.nunchukX,
                        in: -1...1,
                        onEditingChanged: { _ in model.updateNunchukStick() }
                    )
                    .onChange(of: model.nunchukX) {
                        model.updateNunchukStick()
                    }

                    Text("Stick Y")
                    Slider(
                        value: $model.nunchukY,
                        in: -1...1,
                        onEditingChanged: { _ in model.updateNunchukStick() }
                    )
                    .onChange(of: model.nunchukY) {
                        model.updateNunchukStick()
                    }
                }

                HStack(spacing: 24) {
                    NunchukHoldButton("C", name: "C")
                    NunchukHoldButton("Z", name: "Z")
                }
            }
        }
    }

    private var motionPlusCard: some View {
        GroupBox("MotionPlus") {
            Toggle(
                "Present",
                isOn: Binding(
                    get: { model.motionPlusEnabled },
                    set: model.setMotionPlusEnabled
                )
            )
        }
    }

    private var diagnosticsCard: some View {
        GroupBox("Diagnostics") {
            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    ShareLink(
                        item: model.hardwareTraceJSON(),
                        subject: Text("WiiRemoteX iOS hardware trace"),
                        message: Text("Structured WiiRemoteX hardware trace JSON")
                    ) {
                        Label("Share JSON trace", systemImage: "square.and.arrow.up")
                    }

                    Button("Clear trace") {
                        model.clearHardwareTrace()
                    }
                }

                if model.diagnostics.isEmpty {
                    Text("No events yet")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(Array(model.diagnostics.suffix(12).enumerated()), id: \.offset) { _, line in
                        Text(line)
                            .font(.system(.caption, design: .monospaced))
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        }
    }
}

private struct HoldButton: View {
    @EnvironmentObject private var model: WiiRemoteViewModel

    let title: String
    let button: String
    let size: CGFloat

    init(_ title: String, button: String, size: CGFloat = 54) {
        self.title = title
        self.button = button
        self.size = size
    }

    var body: some View {
        Text(title)
            .font(.headline)
            .frame(width: size, height: size)
            .background(.secondary.opacity(0.14), in: Circle())
            .contentShape(Circle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in model.button(button, pressed: true) }
                    .onEnded { _ in model.button(button, pressed: false) }
            )
    }
}

private struct NunchukHoldButton: View {
    @EnvironmentObject private var model: WiiRemoteViewModel

    let title: String
    let name: String

    init(_ title: String, name: String) {
        self.title = title
        self.name = name
    }

    var body: some View {
        Text(title)
            .font(.headline)
            .frame(width: 64, height: 52)
            .background(.secondary.opacity(0.14), in: RoundedRectangle(cornerRadius: 16))
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in model.nunchukButton(name, pressed: true) }
                    .onEnded { _ in model.nunchukButton(name, pressed: false) }
            )
    }
}
