import Foundation
import Testing
@testable import SharedCoreKit

/// The facade over `invite.json`. Sealing under the fixture's fixed nonces is internal to the Kotlin
/// and covered by its `InviteVectorTest`; here sealing is checked by round trip.
@Suite struct InviteCipherTests {

    /// Every section's fields, all optional: each vector uses a subset.
    struct Vector: Decodable {
        struct Envelope: Decodable {
            let nonce: String
            let ciphertext: String
            let signature: String?
        }

        let name: String
        let secret: String?
        let chatId: String?
        let inviteToken: String?
        let inviteId: String?
        let chatKey: String?
        let inviterSeed: String?
        let inviterPublicKey: String?
        let ownerSeed: String?
        let nonce: String?
        let ciphertext: String?
        let signature: String?
        let creatorPublicKey: String?
        let wrappedByIsCreator: Bool?
        let memberSeed: String?
        let keyEnvelope: Envelope?
        let secretEnvelope: Envelope?
        let failsAt: String?
        let fragment: String?
        let url: String?
        let route: String?

        func bytes(_ path: KeyPath<Vector, String?>) throws -> Data {
            let hex = try #require(self[keyPath: path], "\(name): missing field")
            return try #require(Data(hex: hex), "\(name): bad fixture hex")
        }
    }

    struct Fixture: Decodable {
        struct Rejects: Decodable {
            let keyEnvelope: [Vector]
            let secretEnvelope: [Vector]
            let bundles: [Vector]
        }

        let derivation: [Vector]
        let keyEnvelopes: [Vector]
        let secretEnvelopes: [Vector]
        let bundles: [Vector]
        let links: [Vector]
        let fragmentParse: [Vector]
        let routes: [Vector]
        let rejects: Rejects
    }

    let fixture: Fixture

    init() throws {
        fixture = try Fixtures.load("invite", as: Fixture.self)
    }

    @Test func derivesTokenAndId() throws {
        #expect(fixture.derivation.count == 8)
        for v in fixture.derivation {
            let secret = try v.bytes(\.secret)
            #expect(try SharedInviteCipher.inviteToken(secret: secret) == v.bytes(\.inviteToken), "\(v.name)")
            #expect(try SharedInviteCipher.inviteId(secret: secret) == v.bytes(\.inviteId), "\(v.name)")
        }
    }

    @Test func opensAndRoundTripsKeyEnvelopes() throws {
        for v in fixture.keyEnvelopes {
            let inviter = SharedEd25519.keyPair(seed: try v.bytes(\.inviterSeed))
            let envelope = SharedInviteCipher.KeyEnvelope(
                nonce: try v.bytes(\.nonce),
                ciphertext: try v.bytes(\.ciphertext),
                signature: try v.bytes(\.signature)
            )
            let opened = try SharedInviteCipher.openKeyEnvelope(
                secret: v.bytes(\.secret), chatId: v.bytes(\.chatId), inviterPublicKey: inviter.publicKey, envelope: envelope
            )
            #expect(opened == (try v.bytes(\.chatKey)), "\(v.name)")

            let sealed = try SharedInviteCipher.sealKeyEnvelope(
                inviter: inviter, secret: v.bytes(\.secret), chatId: v.bytes(\.chatId), chatKey: v.bytes(\.chatKey)
            )
            #expect(sealed.nonce != envelope.nonce, "\(v.name)")
            let reopened = try SharedInviteCipher.openKeyEnvelope(
                secret: v.bytes(\.secret), chatId: v.bytes(\.chatId), inviterPublicKey: inviter.publicKey, envelope: sealed
            )
            #expect(reopened == (try v.bytes(\.chatKey)), "\(v.name)")
        }
    }

    @Test func opensAndRoundTripsSecretEnvelopes() throws {
        for v in fixture.secretEnvelopes {
            let owner = SharedEd25519.keyPair(seed: try v.bytes(\.ownerSeed))
            let envelope = SharedInviteCipher.SecretEnvelope(nonce: try v.bytes(\.nonce), ciphertext: try v.bytes(\.ciphertext))
            let opened = try SharedInviteCipher.openSecretEnvelope(
                owner: owner, chatId: v.bytes(\.chatId), inviteId: v.bytes(\.inviteId), envelope: envelope
            )
            #expect(opened == (try v.bytes(\.secret)), "\(v.name)")

            let sealed = try SharedInviteCipher.sealSecretEnvelope(
                owner: owner, secret: v.bytes(\.secret), chatId: v.bytes(\.chatId), inviteId: v.bytes(\.inviteId)
            )
            let reopened = try SharedInviteCipher.openSecretEnvelope(
                owner: owner, chatId: v.bytes(\.chatId), inviteId: v.bytes(\.inviteId), envelope: sealed
            )
            #expect(reopened == (try v.bytes(\.secret)), "\(v.name)")
        }
    }

    @Test func opensBundles() throws {
        for v in fixture.bundles {
            #expect(try openBundle(v, inviteId: v.bytes(\.inviteId)) == v.bytes(\.chatKey), "\(v.name)")
        }
    }

    @Test func rejectsBundlesAtTheirStep() throws {
        // The reject cases carry no inviteId; every bundle in the fixture is for the same invite.
        let inviteId = try #require(fixture.bundles.first).bytes(\.inviteId)
        let steps: [String: SharedInviteCipher.BundleError] = [
            "wrappedBy": .wrappedBy, "signature": .signature, "secretOpen": .secretOpen, "keyOpen": .keyOpen,
        ]
        #expect(fixture.rejects.bundles.count == 3)
        for v in fixture.rejects.bundles {
            let expected = try #require(steps[v.failsAt ?? ""], "\(v.name): unknown step")
            #expect(throws: expected, "\(v.name)") { try openBundle(v, inviteId: inviteId) }
        }
    }

    @Test func rejectsKeyEnvelopes() throws {
        for v in fixture.rejects.keyEnvelope {
            let envelope = SharedInviteCipher.KeyEnvelope(
                nonce: try v.bytes(\.nonce), ciphertext: try v.bytes(\.ciphertext), signature: try v.bytes(\.signature)
            )
            #expect(throws: (any Error).self, "\(v.name)") {
                try SharedInviteCipher.openKeyEnvelope(
                    secret: v.bytes(\.secret), chatId: v.bytes(\.chatId),
                    inviterPublicKey: v.bytes(\.inviterPublicKey), envelope: envelope
                )
            }
        }
    }

    @Test func rejectsSecretEnvelopes() throws {
        for v in fixture.rejects.secretEnvelope {
            let owner = SharedEd25519.keyPair(seed: try v.bytes(\.ownerSeed))
            let envelope = SharedInviteCipher.SecretEnvelope(nonce: try v.bytes(\.nonce), ciphertext: try v.bytes(\.ciphertext))
            #expect(throws: (any Error).self, "\(v.name)") {
                try SharedInviteCipher.openSecretEnvelope(
                    owner: owner, chatId: v.bytes(\.chatId), inviteId: v.bytes(\.inviteId), envelope: envelope
                )
            }
        }
    }

    @Test func encodesLinks() throws {
        for v in fixture.links {
            #expect(try SharedInviteCipher.link(secret: v.bytes(\.secret)) == v.url, "\(v.name)")
        }
    }

    @Test func parsesFragments() throws {
        #expect(fixture.fragmentParse.count == 13)
        for v in fixture.fragmentParse {
            let parsed = SharedInviteCipher.secret(fragment: try #require(v.fragment))
            #expect(parsed == (v.secret == nil ? nil : try v.bytes(\.secret)), "\(v.name)")
        }
    }

    @Test func classifiesRoutes() throws {
        for v in fixture.routes {
            let route = SharedInviteCipher.route(url: try #require(v.url))
            switch v.route {
            case "invite": #expect(route == .invite(secret: try v.bytes(\.secret)), "\(v.name)")
            case "invalidInvite": #expect(route == .invalidInvite, "\(v.name)")
            case "chat": #expect(route == .chat, "\(v.name)")
            default: #expect(route == .other, "\(v.name)")
            }
        }
    }

    private func openBundle(_ v: Vector, inviteId: Data) throws -> Data {
        let key = try #require(v.keyEnvelope)
        let secret = try #require(v.secretEnvelope)
        let signature = try #require(key.signature)
        return try SharedInviteCipher.openKeyBundle(
            member: SharedEd25519.keyPair(seed: v.bytes(\.memberSeed)),
            creatorPublicKey: v.bytes(\.creatorPublicKey),
            wrappedByCreator: try #require(v.wrappedByIsCreator),
            chatId: v.bytes(\.chatId),
            inviteId: inviteId,
            keyEnvelope: .init(
                nonce: try #require(Data(hex: key.nonce)),
                ciphertext: try #require(Data(hex: key.ciphertext)),
                signature: try #require(Data(hex: signature))
            ),
            secretEnvelope: .init(
                nonce: try #require(Data(hex: secret.nonce)),
                ciphertext: try #require(Data(hex: secret.ciphertext))
            )
        )
    }
}
