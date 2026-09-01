import Foundation
import SharedCore

/// The hash and MAC primitives, computed by the shared Kotlin.
///
/// Namespaced rather than free functions so the call site says where the answer came from while
/// callers still hold their own `Sha256`/`Hmac` types during the swap.
public enum SharedHash {

    public static func sha256(_ data: Data) -> Data {
        Data(Sha256Hash.companion.hash(input: data.kotlinByteArray))
    }

    /// SHA-256(SHA-256(data)), the double hash Solana addresses and Bitcoin-derived formats use.
    public static func sha256Twice(_ data: Data) -> Data {
        Data(Sha256Hash.companion.hashTwice(input: data.kotlinByteArray))
    }

    public static func sha512(_ data: Data) -> Data {
        Data(Sha512.shared.hash(input: data.kotlinByteArray))
    }

    public static func hmacSHA256(key: Data, message: Data) -> Data {
        Data(Hmac.shared.hmac(
            algorithm: "HmacSHA256",
            key: key.kotlinByteArray,
            message: message.kotlinByteArray
        ))
    }

    public static func hmacSHA512(key: Data, message: Data) -> Data {
        Data(Hmac.shared.hmac(
            algorithm: "HmacSHA512",
            key: key.kotlinByteArray,
            message: message.kotlinByteArray
        ))
    }

    /// PBKDF2-HMAC-SHA512. Takes strings rather than bytes because that is the shape BIP-39 needs
    /// and the shape the Kotlin exports.
    public static func pbkdf2SHA512(
        password: String,
        salt: String,
        iterations: Int,
        keyLength: Int
    ) -> Data {
        Data(PBKDF2SHA512.shared.derive(
            P: password,
            S: salt,
            c: Int32(iterations),
            dkLen: Int32(keyLength)
        ))
    }
}
