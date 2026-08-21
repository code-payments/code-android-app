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
}
