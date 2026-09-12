import Testing
import Foundation
@testable import SharedCoreKit

@Suite("SharedDerivation")
struct DerivationTests {

    struct Vector: Decodable {
        let name, mnemonic, passphrase, path, derivedKey, publicKey: String
    }
    struct Fixture: Decodable { let vectors: [Vector] }

    @Test("derivation matches the canonical SLIP-10 vectors")
    func derivationMatchesCanonicalVectors() throws {
        let fixture = try Fixtures.load("slip10", as: Fixture.self)
        #expect(!fixture.vectors.isEmpty)

        for v in fixture.vectors {
            let words = v.mnemonic.split(separator: " ").map(String.init)
            let seed = SharedDerivation.seed(mnemonic: words, passphrase: v.passphrase)

            let hardenedIndexes = try v.path
                .split(separator: "/")
                .dropFirst() // leading "m"
                .map { component -> Int64 in
                    let hardened = component.hasSuffix("'")
                    let digits = hardened ? String(component.dropLast()) : String(component)
                    let value = try #require(Int64(digits))
                    return 0x8000_0000 + value
                }

            let derivedKey = SharedDerivation.derivedKey(seed: seed, hardenedIndexes: hardenedIndexes)
            #expect(derivedKey.hexString == v.derivedKey, "derivedKey mismatch for \(v.name)")

            let keyPair = SharedEd25519.keyPair(seed: derivedKey)
            #expect(keyPair.publicKey.hexString == v.publicKey, "publicKey mismatch for \(v.name)")
        }
    }
}
