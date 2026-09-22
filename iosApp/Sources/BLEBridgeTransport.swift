import CoreBluetooth
import Foundation

final class BLEBridgeTransport: NSObject, ObservableObject {
    enum State: Equatable {
        case bluetoothUnavailable
        case idle
        case scanning
        case connecting
        case connected
    }

    static let serviceUUID = CBUUID(string: "7C0A0001-6F4B-4A42-9D47-575258000001")
    static let phoneToBridgeUUID = CBUUID(string: "7C0A0002-6F4B-4A42-9D47-575258000001")
    static let bridgeToPhoneUUID = CBUUID(string: "7C0A0003-6F4B-4A42-9D47-575258000001")

    @Published private(set) var state: State = .idle
    @Published private(set) var bridgeName: String?

    var onPacket: ((Data) -> Void)?
    var onDiagnostic: ((String) -> Void)?

    private var centralManager: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var txCharacteristic: CBCharacteristic?
    private var rxCharacteristic: CBCharacteristic?

    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: .main)
    }

    func connect() {
        guard centralManager.state == .poweredOn else {
            state = .bluetoothUnavailable
            return
        }

        disconnect()
        state = .scanning
        onDiagnostic?("BLE scan started")
        centralManager.scanForPeripherals(
            withServices: [Self.serviceUUID],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: false]
        )
    }

    func disconnect() {
        centralManager?.stopScan()
        if let peripheral {
            centralManager?.cancelPeripheralConnection(peripheral)
        }

        self.peripheral = nil
        txCharacteristic = nil
        rxCharacteristic = nil
        bridgeName = nil

        if centralManager?.state == .poweredOn {
            state = .idle
        }
    }

    func send(_ packet: Data) {
        guard
            packet.count <= 20,
            let peripheral,
            let txCharacteristic
        else {
            onDiagnostic?("BLE TX dropped: bridge is not ready")
            return
        }

        let writeType: CBCharacteristicWriteType =
            txCharacteristic.properties.contains(.writeWithoutResponse) ? .withoutResponse : .withResponse

        peripheral.writeValue(packet, for: txCharacteristic, type: writeType)
    }
}

extension BLEBridgeTransport: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            if state == .bluetoothUnavailable {
                state = .idle
            }
            onDiagnostic?("Bluetooth powered on")

        default:
            state = .bluetoothUnavailable
            onDiagnostic?("Bluetooth unavailable: \(central.state.rawValue)")
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        guard self.peripheral == nil else { return }

        central.stopScan()
        self.peripheral = peripheral
        bridgeName =
            advertisementData[CBAdvertisementDataLocalNameKey] as? String ??
            peripheral.name ??
            "WiiRemoteX Bridge"

        state = .connecting
        onDiagnostic?("BLE bridge discovered: \(bridgeName ?? "unknown")")
        peripheral.delegate = self
        central.connect(peripheral)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        state = .connecting
        onDiagnostic?("BLE link connected; discovering bridge service")
        peripheral.discoverServices([Self.serviceUUID])
    }

    func centralManager(
        _ central: CBCentralManager,
        didFailToConnect peripheral: CBPeripheral,
        error: Error?
    ) {
        onDiagnostic?("BLE bridge connection failed: \(error?.localizedDescription ?? "unknown")")
        self.peripheral = nil
        state = .idle
    }

    func centralManager(
        _ central: CBCentralManager,
        didDisconnectPeripheral peripheral: CBPeripheral,
        timestamp: CFAbsoluteTime,
        isReconnecting: Bool,
        error: Error?
    ) {
        onDiagnostic?("BLE bridge disconnected: \(error?.localizedDescription ?? "normal")")
        self.peripheral = nil
        txCharacteristic = nil
        rxCharacteristic = nil
        state = .idle
    }
}

extension BLEBridgeTransport: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error {
            onDiagnostic?("Service discovery failed: \(error.localizedDescription)")
            return
        }

        guard
            let service = peripheral.services?.first(where: { $0.uuid == Self.serviceUUID })
        else {
            onDiagnostic?("WiiRemoteX bridge service not found")
            return
        }

        peripheral.discoverCharacteristics(
            [Self.phoneToBridgeUUID, Self.bridgeToPhoneUUID],
            for: service
        )
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didDiscoverCharacteristicsFor service: CBService,
        error: Error?
    ) {
        if let error {
            onDiagnostic?("Characteristic discovery failed: \(error.localizedDescription)")
            return
        }

        for characteristic in service.characteristics ?? [] {
            switch characteristic.uuid {
            case Self.phoneToBridgeUUID:
                txCharacteristic = characteristic
            case Self.bridgeToPhoneUUID:
                rxCharacteristic = characteristic
                peripheral.setNotifyValue(true, for: characteristic)
            default:
                break
            }
        }

        if txCharacteristic != nil, rxCharacteristic != nil {
            state = .connecting
            onDiagnostic?("Bridge characteristics ready; enabling notifications")
        }
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateNotificationStateFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        guard characteristic.uuid == Self.bridgeToPhoneUUID else { return }

        if let error {
            onDiagnostic?("BLE notification subscription failed: \(error.localizedDescription)")
            state = .idle
            return
        }

        if characteristic.isNotifying, txCharacteristic != nil {
            state = .connected
            onDiagnostic?("BLE bridge transport ready")
        }
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        if let error {
            onDiagnostic?("BLE RX failed: \(error.localizedDescription)")
            return
        }

        guard
            characteristic.uuid == Self.bridgeToPhoneUUID,
            let data = characteristic.value
        else {
            return
        }

        onPacket?(data)
    }
}
