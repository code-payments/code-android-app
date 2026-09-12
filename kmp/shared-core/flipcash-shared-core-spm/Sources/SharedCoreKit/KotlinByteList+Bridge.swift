import Foundation
import SharedCore

/// Boxed-`List<Byte>` bridging, needed only by the Solana message/transaction type hierarchy.
///
/// Every other flat entry point in this package (`SolanaEncoding`, `Ed25519Kmp`, `Base58`, ...) takes
/// `ByteArray`, which bridges to `KotlinByteArray` — unboxed, one copy. The `Message`/`Instruction`/
/// `AccountMeta`/`Key32`/`PublicKey`/`Signature` family is the one part of the exported surface with
/// no such entry point: their constructors and `List<Byte>`-typed properties only exist as
/// `List<Byte>`, which crosses the bridge as `NSArray<KotlinByte *>` — one boxed `NSNumber` subclass
/// instance per byte. `KeyType.byteArray` is the one exception (an unboxed read-only property), so
/// reading a key back out is cheap; constructing one from raw bytes is not.
extension Data {

    /// Boxes every byte — the shape `Key32`/`PublicKey`/`Signature`/`AccountMeta`'s constructors
    /// require, since none of them has a raw-`ByteArray` overload.
    var kotlinByteList: [KotlinByte] {
        map { KotlinByte(value: Int8(bitPattern: $0)) }
    }

    /// Unboxes a `List<Byte>` result (e.g. `Instruction.data`, `CompiledInstruction.encode()`) back
    /// into `Data`.
    init(kotlinByteList array: [KotlinByte]) {
        self.init(array.map { UInt8(bitPattern: $0.int8Value) })
    }
}

extension Array where Element == UInt8 {

    /// Boxes index bytes (`CompiledInstruction.accountIndexes`, LUT `writableIndexes`/
    /// `readonlyIndexes`) for the same reason as `Data.kotlinByteList` — these are `List<Byte>` on
    /// the Kotlin side too, just not 32/64-byte keys.
    var kotlinByteList: [KotlinByte] {
        map { KotlinByte(value: Int8(bitPattern: $0)) }
    }

    init(kotlinByteList array: [KotlinByte]) {
        self = array.map { UInt8(bitPattern: $0.int8Value) }
    }
}
