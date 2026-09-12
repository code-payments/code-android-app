import Foundation
import SharedCore

/// Solana message + transaction wire-format encode/decode, backed by the shared Kotlin
/// implementation in `:libs:solana:encoding`.
///
/// This wraps `SharedCore.SolanaEncoding` — a flat, `Data`-in/`Data`-out entry point deliberately
/// kept separate from the Kotlin `Message`/`SolanaTransaction`/`Instruction`/`AddressLookupTable`
/// hierarchy, since those names already exist as Swift types in
/// `FlipcashCore/Sources/FlipcashCore/Solana/` and Kotlin's `Message` is a `sealed interface`
/// (an Obj-C protocol, reference-typed) where Swift's is a value-type `enum`. Nothing here ever
/// holds a Kotlin `Message` — every call is bytes in, bytes out.
public enum SharedSolanaEncoding {

    /// Parses `bytes` as a Solana message — legacy or v0, decided by the version-prefix byte the
    /// wire format already carries — and immediately re-serializes what it parsed. A non-nil
    /// result proves `bytes` is a well-formed message this codec round-trips byte-for-byte; `nil`
    /// means `bytes` did not parse as either version.
    public static func decodeMessage(_ bytes: Data) -> Data? {
        guard let result = SolanaEncoding.shared.decodeMessage(bytes: bytes.kotlinByteArray) else { return nil }
        return Data(result)
    }

    /// Parses `bytes` as a message, replaces its `recentBlockhash` with `blockhash`, and
    /// re-serializes it — the one message mutation a caller needs without ever holding a Kotlin
    /// `Message`: refreshing the blockhash immediately before signing. Returns `nil` if `bytes`
    /// does not parse, or `blockhash` is not exactly 32 bytes.
    public static func encodeMessage(_ bytes: Data, blockhash: Data) -> Data? {
        guard let result = SolanaEncoding.shared.encodeMessage(bytes: bytes.kotlinByteArray, blockhash: blockhash.kotlinByteArray) else { return nil }
        return Data(result)
    }

    /// Parses `bytes` as a full transaction (signature(s) + message) and immediately
    /// re-serializes it. A non-nil result proves `bytes` is a well-formed transaction this codec
    /// round-trips byte-for-byte; `nil` means `bytes` did not parse.
    public static func decodeTransaction(_ bytes: Data) -> Data? {
        guard let result = SolanaEncoding.shared.decodeTransaction(bytes: bytes.kotlinByteArray) else { return nil }
        return Data(result)
    }

    /// Builds transaction wire bytes from an already-encoded `message` and `signatures` — each
    /// signature 64 bytes, concatenated in signer order. Validates `signatures`' length against
    /// the message's own required-signature count. Returns `nil` if `message` does not parse,
    /// `signatures` is not a multiple of 64 bytes, or the signature count does not match the
    /// message's header.
    public static func encodeTransaction(message: Data, signatures: Data) -> Data? {
        guard let result = SolanaEncoding.shared.encodeTransaction(message: message.kotlinByteArray, signatures: signatures.kotlinByteArray) else { return nil }
        return Data(result)
    }
}
