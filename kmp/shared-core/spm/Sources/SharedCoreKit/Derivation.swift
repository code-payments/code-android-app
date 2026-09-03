import Foundation
import SharedCore

/// BIP-39 seed derivation + SLIP-0010 ed25519 key derivation, backed by the shared Kotlin
/// implementation in `:libs:encryption:mnemonic`.
public enum SharedDerivation {

    /// Derives a 64-byte PBKDF2-SHA512 seed from a BIP-39 mnemonic phrase.
    public static func seed(mnemonic: [String], passphrase: String = "") -> Data {
        Data(MnemonicCode.shared.toSeed(words: mnemonic, passphrase: passphrase))
    }

    /// Derives the raw 32-byte private key by walking `hardenedIndexes` (each already offset by
    /// 0x80000000) from `seed` per SLIP-0010.
    public static func derivedKey(seed: Data, hardenedIndexes: [Int64]) -> Data {
        Data(Derive.shared.derivedKey(seed: seed.kotlinByteArray, hardenedIndexes: hardenedIndexes.kotlinLongArray))
    }
}
