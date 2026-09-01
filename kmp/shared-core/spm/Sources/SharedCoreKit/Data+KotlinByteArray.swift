import Foundation
import SharedCore

extension Data {

    /// Kotlin's `ByteArray` has no `Data` bridge of its own, so every exported function taking
    /// bytes needs this copy.
    var kotlinByteArray: KotlinByteArray {
        let array = KotlinByteArray(size: Int32(count))
        for (offset, byte) in enumerated() {
            array.set(index: Int32(offset), value: Int8(bitPattern: byte))
        }
        return array
    }

    /// The reverse copy, for the exported functions that hand bytes back. Kotlin bytes are signed,
    /// so anything above 0x7F comes across negative and has to be reinterpreted rather than
    /// converted.
    init(_ array: KotlinByteArray) {
        var bytes = [UInt8]()
        bytes.reserveCapacity(Int(array.size))
        for index in 0..<array.size {
            bytes.append(UInt8(bitPattern: array.get(index: index)))
        }
        self.init(bytes)
    }
}
