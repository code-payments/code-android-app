import Testing
import Foundation
@testable import SharedCoreKit

/// Asserts the exact vectors `SolanaMessageVectorTest` and `CompactMessageVectorTest` assert in the
/// Kotlin `commonTest` suite (`libs/solana/encoding/src/commonTest/`) — matching guarantees Android
/// and iOS build byte-identical wire data from the same inputs. Fixtures are copies of
/// `test-vectors/solana_message.json` and `test-vectors/compact_message.json`, the same arrangement
/// `Fixtures.swift` describes for every other suite here.
@Suite("SharedSolanaEncoding")
struct SolanaEncodingTests {

    // MARK: - solana_message.json

    // The Kotlin test builds these messages field-by-field (`Instruction`/`AccountMeta`/`Message`
    // are deliberately not exported — see `SolanaEncoding.kt`), so this suite instead treats
    // `expectedMessage` as the wire bytes and drives them through `decodeMessage`/`encodeMessage`,
    // the only Solana-message entry points `SharedCoreKit` exposes.
    struct AccountEntry: Decodable { let seed: Int; let role: String }
    struct MessageVector: Decodable {
        let name: String
        let accounts: [AccountEntry]
        let blockhashSeed: Int
        let expectedMessage: String
    }
    struct MessageFixture: Decodable { let vectors: [MessageVector] }

    // Legacy message layout: header(3) + short-vec account count(1, true for every fixture here
    // since none reaches 128 accounts) + accounts(32 bytes each) + recentBlockhash(32).
    private static func blockhashOffset(accountCount: Int) -> Int { 3 + 1 + accountCount * 32 }

    @Test("decodeMessage round-trips the canonical legacy message vectors byte for byte")
    func decodeMessageMatchesCanonicalVectors() throws {
        let fixture = try Fixtures.load("solana_message", as: MessageFixture.self)
        #expect(!fixture.vectors.isEmpty, "no vectors loaded")

        for v in fixture.vectors {
            let expected = try #require(Data(hex: v.expectedMessage), "bad hex fixture for \(v.name)")
            let decoded = try #require(SharedSolanaEncoding.decodeMessage(expected), "decodeMessage returned nil for \(v.name)")
            #expect(decoded == expected, "round-trip mismatch for \(v.name)")
        }
    }

    @Test("encodeMessage rewrites recentBlockhash in place and nothing else")
    func encodeMessageSwapsBlockhash() throws {
        let fixture = try Fixtures.load("solana_message", as: MessageFixture.self)
        #expect(!fixture.vectors.isEmpty, "no vectors loaded")

        for v in fixture.vectors {
            let original = try #require(Data(hex: v.expectedMessage), "bad hex fixture for \(v.name)")
            let offset = Self.blockhashOffset(accountCount: v.accounts.count)

            let originalBlockhash = original.subdata(in: offset..<(offset + 32))
            let expectedOriginalBlockhash = Data(repeating: UInt8(truncatingIfNeeded: v.blockhashSeed), count: 32)
            #expect(originalBlockhash == expectedOriginalBlockhash, "blockhash offset computed wrong for \(v.name)")

            // Identity swap: putting the vector's own blockhash back must reproduce it exactly.
            let identity = try #require(SharedSolanaEncoding.encodeMessage(original, blockhash: originalBlockhash), "encodeMessage returned nil for \(v.name)")
            #expect(identity == original, "identity blockhash swap changed bytes for \(v.name)")

            // Real swap: a different blockhash lands at that offset, and nowhere else moves.
            let newBlockhash = Data(repeating: 0xAB, count: 32)
            let swapped = try #require(SharedSolanaEncoding.encodeMessage(original, blockhash: newBlockhash), "encodeMessage returned nil for \(v.name)")
            var expectedSwapped = original
            expectedSwapped.replaceSubrange(offset..<(offset + 32), with: newBlockhash)
            #expect(swapped == expectedSwapped, "blockhash swap touched bytes outside the blockhash window for \(v.name)")
        }
    }

    @Test("decodeMessage and encodeMessage reject malformed input")
    func rejectsMalformedMessages() {
        #expect(SharedSolanaEncoding.decodeMessage(Data()) == nil)
        #expect(SharedSolanaEncoding.encodeMessage(Data([0xFF, 0xFF, 0xFF]), blockhash: Data(repeating: 0, count: 32)) == nil)
        #expect(SharedSolanaEncoding.encodeMessage(Data(repeating: 0, count: 40), blockhash: Data(repeating: 0, count: 31)) == nil, "31-byte blockhash must be rejected")
    }

    // MARK: - compact_message.json

    // `SharedHash.sha256` (from `Hashes.swift`) already crosses the bridge, so this replicates
    // `CompactMessageVectorTest`'s byte composition directly rather than adding new Kotlin surface
    // for it — the layout itself (field order, "transfer" domain, little-endian amount) is the thing
    // under test, and it needs no `Message`/`Instruction` construction on either side.
    struct CompactVector: Decodable {
        let name: String
        let domain: String
        let sourceSeed: Int
        let destinationSeed: Int
        let amount: String?
        let nonceSeed: Int
        let nonceValueSeed: Int
        let message: String
        let sha256: String
    }
    struct CompactFixture: Decodable { let vectors: [CompactVector] }

    private static func key(_ seed: Int) -> Data {
        Data(repeating: UInt8(truncatingIfNeeded: seed), count: 32)
    }

    private static func amountLE8(_ value: String) throws -> Data {
        let v = try #require(UInt64(value))
        return Data((0..<8).map { UInt8((v >> (8 * $0)) & 0xFF) })
    }

    @Test("compact message bytes and sha256 match the canonical vectors")
    func compactMessageMatchesCanonicalVectors() throws {
        let fixture = try Fixtures.load("compact_message", as: CompactFixture.self)
        #expect(!fixture.vectors.isEmpty, "no vectors loaded")

        for v in fixture.vectors {
            var message = Data()
            message.append(Data(v.domain.utf8))
            message.append(Self.key(v.sourceSeed))
            message.append(Self.key(v.destinationSeed))
            if let amount = v.amount {
                message.append(try Self.amountLE8(amount))
            }
            message.append(Self.key(v.nonceSeed))
            message.append(Self.key(v.nonceValueSeed))

            #expect(message.hexString == v.message, "message bytes mismatch for \(v.name)")
            #expect(SharedHash.sha256(message).hexString == v.sha256, "sha256 mismatch for \(v.name)")
        }
    }

    // MARK: - transaction encode/decode

    // No canonical cross-platform fixture covers full transactions (signature + message), so this
    // builds one from each solana_message.json vector: zero signatures, one per required signer,
    // per the wire format's own convention for an unsigned transaction (`Signature.zero` in Kotlin).
    @Test("encodeTransaction assembles, and decodeTransaction round-trips, a transaction built on a canonical message")
    func transactionRoundTrips() throws {
        let fixture = try Fixtures.load("solana_message", as: MessageFixture.self)
        #expect(!fixture.vectors.isEmpty, "no vectors loaded")

        for v in fixture.vectors {
            let message = try #require(Data(hex: v.expectedMessage), "bad hex fixture for \(v.name)")
            // The message's own header's first byte is `requiredSignatures` (`expectedHeader`'s
            // first byte, verified equal to this in the Kotlin fixture already).
            let requiredSignatures = Int(message[message.startIndex])
            #expect(requiredSignatures > 0, "expected at least one required signer for \(v.name)")

            let signatures = Data(repeating: 0, count: requiredSignatures * 64)
            // Transactions are short-vec(signature count) + signatures + message; every vector here
            // has fewer than 128 signers, so the short-vec length is the single count byte.
            var expectedTransaction = Data([UInt8(requiredSignatures)])
            expectedTransaction.append(signatures)
            expectedTransaction.append(message)

            let assembled = try #require(SharedSolanaEncoding.encodeTransaction(message: message, signatures: signatures), "encodeTransaction returned nil for \(v.name)")
            #expect(assembled == expectedTransaction, "encodeTransaction mismatch for \(v.name)")

            let decoded = try #require(SharedSolanaEncoding.decodeTransaction(assembled), "decodeTransaction returned nil for \(v.name)")
            #expect(decoded == assembled, "decodeTransaction round-trip mismatch for \(v.name)")
        }
    }

    @Test("encodeTransaction and decodeTransaction reject malformed input")
    func rejectsMalformedTransactions() throws {
        let fixture = try Fixtures.load("solana_message", as: MessageFixture.self)
        let vector = try #require(fixture.vectors.first)
        let message = try #require(Data(hex: vector.expectedMessage))
        let requiredSignatures = Int(message[message.startIndex])

        // Wrong signature count for this message's header.
        #expect(SharedSolanaEncoding.encodeTransaction(message: message, signatures: Data(repeating: 0, count: (requiredSignatures + 1) * 64)) == nil)
        // Signature payload not a multiple of 64 bytes.
        #expect(SharedSolanaEncoding.encodeTransaction(message: message, signatures: Data(repeating: 0, count: 10)) == nil)
        // Unparseable message.
        #expect(SharedSolanaEncoding.encodeTransaction(message: Data([0xFF]), signatures: Data()) == nil)
        // Zero-signature short-vec header with no message bytes behind it: parses as far as the
        // signature count, then fails to find a message.
        #expect(SharedSolanaEncoding.decodeTransaction(Data([0x00])) == nil)
    }
}
