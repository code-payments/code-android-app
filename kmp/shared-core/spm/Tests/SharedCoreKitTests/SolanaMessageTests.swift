import Testing
import Foundation
@testable import SharedCoreKit

/// Exercises the `SharedSolanaMessage`/`SharedSolanaTransaction` type-hierarchy facade
/// (`SolanaMessage.swift`, `SolanaTransaction.swift`) against the same `solana_message.json`
/// vectors `SolanaEncodingTests` drives through the flat byte-in/byte-out entry points. Where that
/// suite only proves the codec round-trips bytes, this one proves the constructed *types* — headers,
/// accounts, instructions — match what the fixture describes, and that building a transaction from
/// those same fields (payer/writable/readonly/readonly-signer/readonly-program roles) reproduces the
/// fixture's `expectedMessage` byte for byte.
@Suite("SharedSolanaMessage")
struct SolanaMessageTests {

    struct AccountEntry: Decodable { let seed: Int; let role: String }
    struct InstructionEntry: Decodable { let programSeed: Int; let accountSeeds: [Int]; let data: String }
    struct MessageVector: Decodable {
        let name: String
        let accounts: [AccountEntry]
        let blockhashSeed: Int
        let instructions: [InstructionEntry]
        let expectedHeader: String
        let expectedMessage: String
    }
    struct MessageFixture: Decodable { let vectors: [MessageVector] }

    private static func key(_ seed: Int) -> Data {
        Data(repeating: UInt8(truncatingIfNeeded: seed), count: 32)
    }

    /// One `SharedSolanaAccountMeta` per fixture role. `readonly-program` accounts are not part of
    /// any instruction's `accounts` array in the fixture (they're only ever the instruction's
    /// `program`), but they still belong to the message's own account list — mirroring the Kotlin
    /// `commonTest` vectors this file matches, whose `accounts` array includes the program account
    /// alongside payer/writable/readonly entries.
    private static func accountMeta(_ entry: AccountEntry) throws -> SharedSolanaAccountMeta {
        let publicKey = key(entry.seed)
        switch entry.role {
        case "payer": return .payer(publicKey: publicKey)
        case "writable": return .writable(publicKey: publicKey)
        case "readonly": return .readonly(publicKey: publicKey)
        case "readonly-signer": return .readonly(publicKey: publicKey, signer: true)
        case "readonly-program": return .program(publicKey: publicKey)
        default:
            Issue.record("unknown role \(entry.role)")
            return .readonly(publicKey: publicKey)
        }
    }

    @Test("SharedSolanaMessage decodes the canonical legacy vectors into matching structural fields")
    func decodeMatchesStructuralFields() throws {
        let fixture = try Fixtures.load("solana_message", as: MessageFixture.self)
        #expect(!fixture.vectors.isEmpty, "no vectors loaded")

        for v in fixture.vectors {
            let bytes = try #require(Data(hex: v.expectedMessage), "bad hex fixture for \(v.name)")
            let message = try #require(SharedSolanaMessage(data: bytes), "SharedSolanaMessage(data:) returned nil for \(v.name)")

            guard case .legacy(let legacy) = message else {
                Issue.record("expected .legacy for \(v.name)")
                continue
            }

            #expect(message.version == .legacy, "version mismatch for \(v.name)")

            let expectedHeader = try #require(Data(hex: v.expectedHeader), "bad header hex for \(v.name)")
            #expect(legacy.header.requiredSignatures == Int(expectedHeader[0]), "requiredSignatures mismatch for \(v.name)")
            #expect(legacy.header.readOnlySigners == Int(expectedHeader[1]), "readOnlySigners mismatch for \(v.name)")
            #expect(legacy.header.readOnly == Int(expectedHeader[2]), "readOnly mismatch for \(v.name)")

            let expectedAccountKeys = v.accounts.map { Self.key($0.seed) }
            #expect(message.accountKeys == expectedAccountKeys, "accountKeys mismatch for \(v.name)")

            let expectedBlockhash = Data(repeating: UInt8(truncatingIfNeeded: v.blockhashSeed), count: 32)
            #expect(message.recentBlockhash == expectedBlockhash, "recentBlockhash mismatch for \(v.name)")

            #expect(message.instructions.count == v.instructions.count, "instruction count mismatch for \(v.name)")
            for (compiled, entry) in zip(message.instructions, v.instructions) {
                let programIndex = expectedAccountKeys.firstIndex(of: Self.key(entry.programSeed))
                #expect(programIndex != nil, "program seed \(entry.programSeed) not in account list for \(v.name)")
                #expect(Int(compiled.programIndex) == programIndex, "programIndex mismatch for \(v.name)")

                let expectedIndexes = entry.accountSeeds.map { seed in
                    UInt8(expectedAccountKeys.firstIndex(of: Self.key(seed))!)
                }
                #expect(compiled.accountIndexes == expectedIndexes, "accountIndexes mismatch for \(v.name)")

                let expectedData = try #require(Data(hex: entry.data), "bad instruction data hex for \(v.name)")
                #expect(compiled.data == expectedData, "instruction data mismatch for \(v.name)")
            }

            #expect(message.addressTableLookups.isEmpty, "legacy message must have no address table lookups for \(v.name)")

            // Re-encoding what was just decoded must reproduce the original bytes exactly.
            #expect(message.encode() == bytes, "re-encode mismatch for \(v.name)")
            #expect(legacy.encode() == bytes, "SharedSolanaLegacyMessage.encode() mismatch for \(v.name)")
        }
    }

    @Test("constructing a SharedSolanaTransaction from fixture account roles reproduces the canonical wire bytes")
    func constructionMatchesCanonicalVectors() throws {
        let fixture = try Fixtures.load("solana_message", as: MessageFixture.self)
        #expect(!fixture.vectors.isEmpty, "no vectors loaded")

        for v in fixture.vectors {
            let expected = try #require(Data(hex: v.expectedMessage), "bad hex fixture for \(v.name)")

            let payerEntry = try #require(v.accounts.first { $0.role == "payer" }, "no payer in \(v.name)")
            let metaBySeed = Dictionary(uniqueKeysWithValues: try v.accounts.map { ($0.seed, try Self.accountMeta($0)) })

            let instructions: [SharedSolanaInstruction] = try v.instructions.map { entry in
                let program = try #require(metaBySeed[entry.programSeed], "no account for program seed \(entry.programSeed) in \(v.name)").publicKey
                let accounts = try entry.accountSeeds.map { seed in
                    try #require(metaBySeed[seed], "no account for seed \(seed) in \(v.name)")
                }
                let data = try #require(Data(hex: entry.data), "bad instruction data hex for \(v.name)")
                return SharedSolanaInstruction(program: program, accounts: accounts, data: data)
            }

            let recentBlockhash = Data(repeating: UInt8(truncatingIfNeeded: v.blockhashSeed), count: 32)
            let transaction = SharedSolanaTransaction(
                payer: Self.key(payerEntry.seed),
                recentBlockhash: recentBlockhash,
                instructions: instructions
            )

            #expect(transaction.message.encode() == expected, "constructed message mismatch for \(v.name)")
            #expect(transaction.message.header == SharedSolanaMessageHeader(
                requiredSignatures: Int(try #require(Data(hex: v.expectedHeader))[0]),
                readOnlySigners: Int(try #require(Data(hex: v.expectedHeader))[1]),
                readOnly: Int(try #require(Data(hex: v.expectedHeader))[2])
            ), "header mismatch for \(v.name)")

            // Unsigned by convention: one all-zero placeholder signature per required signer.
            #expect(transaction.signatures.count == transaction.message.header.requiredSignatures, "signature count mismatch for \(v.name)")
            #expect(transaction.signatures.allSatisfy { $0 == Data(repeating: 0, count: 64) }, "expected placeholder signatures for \(v.name)")
        }
    }

    @Test("SharedSolanaTransaction round-trips through init(data:) and encode()")
    func transactionRoundTrips() throws {
        let fixture = try Fixtures.load("solana_message", as: MessageFixture.self)
        #expect(!fixture.vectors.isEmpty, "no vectors loaded")

        for v in fixture.vectors {
            let message = try #require(Data(hex: v.expectedMessage), "bad hex fixture for \(v.name)")
            let requiredSignatures = Int(message[message.startIndex])
            let signatures = Data(repeating: 0, count: requiredSignatures * 64)

            var wire = Data([UInt8(requiredSignatures)])
            wire.append(signatures)
            wire.append(message)

            let transaction = try #require(SharedSolanaTransaction(data: wire), "SharedSolanaTransaction(data:) returned nil for \(v.name)")
            #expect(transaction.signatures.count == requiredSignatures, "signature count mismatch for \(v.name)")
            #expect(transaction.encode() == wire, "encode() round-trip mismatch for \(v.name)")
            #expect(transaction.recentBlockhash == Data(repeating: UInt8(truncatingIfNeeded: v.blockhashSeed), count: 32), "recentBlockhash mismatch for \(v.name)")

            if requiredSignatures > 0 {
                #expect(transaction.identifier == transaction.signatures[0], "identifier mismatch for \(v.name)")
            }
        }
    }

    @Test("SharedSolanaMessage rejects malformed input")
    func rejectsMalformedMessages() {
        #expect(SharedSolanaMessage(data: Data()) == nil)
        #expect(SharedSolanaMessage(data: Data([0xFF, 0xFF, 0xFF])) == nil)
    }

    @Test("SharedSolanaTransaction rejects malformed input")
    func rejectsMalformedTransactions() {
        #expect(SharedSolanaTransaction(data: Data()) == nil)
        #expect(SharedSolanaTransaction(data: Data([0x00])) == nil)
    }
}
