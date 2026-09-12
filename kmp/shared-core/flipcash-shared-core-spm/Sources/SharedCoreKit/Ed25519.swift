import Foundation
import SharedCore

/// Ed25519 signing, computed by the shared Kotlin over the vendored orlp/ed25519 C.
public enum SharedEd25519 {

    /// Sizes follow the orlp convention the C library uses: the private key is `seed || publicKey`.
    public struct KeyPair: Equatable {
        public let publicKey: Data
        public let privateKey: Data

        public init(publicKey: Data, privateKey: Data) {
            self.publicKey = publicKey
            self.privateKey = privateKey
        }
    }

    public static func keyPair(seed: Data) -> KeyPair {
        let pair = Ed25519Kmp.shared.createKeyPair(seed: seed.kotlinByteArray)
        return KeyPair(publicKey: Data(pair.publicKey), privateKey: Data(pair.privateKey))
    }

    public static func sign(message: Data, keyPair: KeyPair) -> Data {
        Data(Ed25519Kmp.shared.sign(
            message: message.kotlinByteArray,
            publicKey: keyPair.publicKey.kotlinByteArray,
            privateKey: keyPair.privateKey.kotlinByteArray
        ))
    }

    public static func verify(signature: Data, message: Data, publicKey: Data) -> Bool {
        Ed25519Kmp.shared.verify(
            signature: signature.kotlinByteArray,
            message: message.kotlinByteArray,
            publicKey: publicKey.kotlinByteArray
        )
    }

    public static func isOnCurve(publicKey: Data) -> Bool {
        Ed25519Kmp.shared.onCurve(publicKey: publicKey.kotlinByteArray)
    }
}
