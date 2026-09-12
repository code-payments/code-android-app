import Foundation
import Testing
@testable import SharedCoreKit

@Suite struct Ed25519Tests {

    struct Fixture: Decodable {
        struct Vector: Decodable {
            let name: String
            let seed: String
            let message: String
            let publicKey: String
            let signature: String
        }

        let vectors: [Vector]
    }

    @Test func matchesTheCrossPlatformVectors() throws {
        let fixture = try Fixtures.load("ed25519", as: Fixture.self)
        #expect(fixture.vectors.count == 6)

        for vector in fixture.vectors {
            let seed = try #require(Data(hex: vector.seed), "\(vector.name): bad fixture hex")
            let message = try #require(Data(hex: vector.message), "\(vector.name): bad fixture hex")
            let expectedPublicKey = try #require(Data(hex: vector.publicKey))
            let expectedSignature = try #require(Data(hex: vector.signature))

            let keyPair = SharedEd25519.keyPair(seed: seed)
            #expect(keyPair.publicKey == expectedPublicKey, "\(vector.name): public key")

            let signature = SharedEd25519.sign(message: message, keyPair: keyPair)
            #expect(signature == expectedSignature, "\(vector.name): signature")

            #expect(
                SharedEd25519.verify(
                    signature: signature,
                    message: message,
                    publicKey: keyPair.publicKey
                ),
                "\(vector.name): verify"
            )
        }
    }

    @Test func rejectsATamperedSignature() throws {
        let fixture = try Fixtures.load("ed25519", as: Fixture.self)
        let vector = try #require(fixture.vectors.first { !$0.message.isEmpty })

        let message = try #require(Data(hex: vector.message))
        let publicKey = try #require(Data(hex: vector.publicKey))
        var signature = try #require(Data(hex: vector.signature))
        signature[0] ^= 0x01

        #expect(!SharedEd25519.verify(signature: signature, message: message, publicKey: publicKey))
    }

    @Test func generatedPublicKeysAreOnCurve() throws {
        let fixture = try Fixtures.load("ed25519", as: Fixture.self)

        for vector in fixture.vectors {
            let seed = try #require(Data(hex: vector.seed))
            #expect(SharedEd25519.isOnCurve(publicKey: SharedEd25519.keyPair(seed: seed).publicKey))
        }
    }

    /// The orlp convention, which callers swapping onto this need to know: the 64-byte private key
    /// is the clamped SHA-512 expansion of the seed, *not* `seed || publicKey`. Anything that
    /// expects to read the seed back out of it — the RFC 8032 layout, and what libsodium calls a
    /// secret key — gets the wrong 32 bytes.
    @Test func privateKeyIsTheClampedSha512Expansion() throws {
        let fixture = try Fixtures.load("ed25519", as: Fixture.self)
        let vector = try #require(fixture.vectors.first)
        let seed = try #require(Data(hex: vector.seed))

        var expected = SharedHash.sha512(seed)
        expected[0] &= 248
        expected[31] &= 63
        expected[31] |= 64

        let keyPair = SharedEd25519.keyPair(seed: seed)

        #expect(keyPair.privateKey.count == 64)
        #expect(keyPair.privateKey == expected)
        #expect(keyPair.privateKey.prefix(32) != seed)
    }
}
