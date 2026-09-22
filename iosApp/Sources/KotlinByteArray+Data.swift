import Foundation
import WiiRemoteShared

extension KotlinByteArray {
    convenience init(data: Data) {
        self.init(size: Int32(data.count))
        for (index, byte) in data.enumerated() {
            set(index: Int32(index), value: Int8(bitPattern: byte))
        }
    }

    var data: Data {
        var bytes = [UInt8]()
        bytes.reserveCapacity(Int(size))

        for index in 0..<size {
            bytes.append(UInt8(bitPattern: get(index: index)))
        }

        return Data(bytes)
    }
}
