import Foundation
import SharedCore

extension Data {

    /// Kotlin's `ByteArray` has no `Data` bridge of its own, so every exported function taking
    /// bytes needs this copy. It goes through `NSData` because the only element-wise alternative,
    /// `KotlinByteArray.set(index:value:)`, costs one Objective-C message send per byte.
    var kotlinByteArray: KotlinByteArray {
        SharedBytes.shared.byteArray(data: self)
    }

    /// The reverse copy, for the exported functions that hand bytes back. Kotlin bytes are signed,
    /// but a bulk copy moves the bit patterns unchanged, so nothing has to be reinterpreted here.
    init(_ array: KotlinByteArray) {
        self = SharedBytes.shared.data(bytes: array)
    }
}
