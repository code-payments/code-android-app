import Foundation
import SharedCore

/// Bitcoin/Solana Base58, computed by the shared Kotlin.
public enum Base58 {

    public static func encode(_ data: Data) -> String {
        SharedCore.Base58.shared.encode(input: data.kotlinByteArray)
    }

    /// Returns `nil` for input outside the alphabet. Kotlin throws there, but the throw carries
    /// nothing a caller can act on beyond "not Base58".
    public static func decode(_ string: String) -> Data? {
        guard let bytes = try? SharedCore.Base58.shared.decode(input: string) else { return nil }
        return Data(bytes)
    }
}
