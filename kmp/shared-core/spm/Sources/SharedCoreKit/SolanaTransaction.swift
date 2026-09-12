import Foundation
import SharedCore

/// A signed (or partially-signed) Solana transaction: a `SharedSolanaMessage` plus its signatures,
/// in signer order. Backed by Kotlin's `SolanaTransaction`.
///
/// Construction deliberately does not reimplement Kotlin's canonical account-sort/dedup and,
/// for v0, address-lookup-table index-grouping — `SolanaTransaction.Companion.newInstance` /
/// `.newV0Instance` already do that correctly, and are exported. The two `init(payer:...)`
/// overloads below call straight into them so this facade cannot drift from that algorithm.
public struct SharedSolanaTransaction: Equatable, Sendable {
    public var message: SharedSolanaMessage
    public var signatures: [Data]

    public init(message: SharedSolanaMessage, signatures: [Data]) {
        self.message = message
        self.signatures = signatures
    }

    /// The transaction's own identifying signature — the first signer's signature, once present.
    public var identifier: Data {
        Data(kotlin.identifier.byteArray)
    }

    public var recentBlockhash: Data {
        get { message.recentBlockhash }
        set { message.recentBlockhash = newValue }
    }

    public func encode() -> Data {
        Data(kotlinByteList: kotlin.encode())
    }

    /// Parses `data` as a full transaction (signatures + message). `nil` if `data` does not parse
    /// as either message version, its signature count doesn't match the message header, or `data`
    /// is empty or otherwise truncated — `SolanaTransaction.fromList` and the `ShortVec.decodeLen`
    /// it's built on
    /// (`libs/solana/encoding/.../internal/solana/ShortVec.kt`) return `null` for all of those
    /// instead of throwing, so no guard is needed on the Swift side.
    public init?(data: Data) {
        guard let result = KotlinSolanaTransaction.companion.fromList(list: data.kotlinByteList) else { return nil }
        self.init(result)
    }

    /// Builds an unsigned legacy transaction from `instructions`, applying Kotlin's canonical
    /// account sort (payer first, programs last, signers before non-signers, writable before
    /// read-only, lexicographic tie-break) and de-duplication. `signatures` starts as one
    /// all-zero placeholder per required signer, matching Kotlin's factory.
    public init(payer: Data, recentBlockhash: Data?, instructions: SharedSolanaInstruction...) {
        self.init(payer: payer, recentBlockhash: recentBlockhash, instructions: instructions)
    }

    public init(payer: Data, recentBlockhash: Data?, instructions: [SharedSolanaInstruction]) {
        let result = KotlinSolanaTransaction.companion.doNewInstance(
            payer: KotlinPublicKey(bytes: payer.kotlinByteList),
            recentBlockhash: recentBlockhash.map { KotlinKey32(bytes: $0.kotlinByteList) },
            instructions: instructions.map { $0.kotlin }
        )
        self.init(result)
    }

    /// Builds an unsigned v0 transaction, resolving `instructions`' accounts against
    /// `addressLookupTables` where possible (accounts not covered by any table stay static).
    /// Kotlin's factory does the table/index-grouping; this only marshals inputs and outputs.
    public init(payer: Data, recentBlockhash: Data?, addressLookupTables: [SharedSolanaAddressLookupTable], instructions: SharedSolanaInstruction...) {
        self.init(payer: payer, recentBlockhash: recentBlockhash, addressLookupTables: addressLookupTables, instructions: instructions)
    }

    public init(payer: Data, recentBlockhash: Data?, addressLookupTables: [SharedSolanaAddressLookupTable], instructions: [SharedSolanaInstruction]) {
        let result = KotlinSolanaTransaction.companion.doNewV0Instance(
            payer: KotlinPublicKey(bytes: payer.kotlinByteList),
            recentBlockhash: recentBlockhash.map { KotlinKey32(bytes: $0.kotlinByteList) },
            addressLookupTables: addressLookupTables.map { $0.kotlin },
            instructions: instructions.map { $0.kotlin }
        )
        self.init(result)
    }
}

extension SharedSolanaTransaction {
    init(_ transaction: KotlinSolanaTransaction) {
        let message: SharedSolanaMessage
        switch transaction.message {
        case let legacy as KotlinMessageLegacy:
            message = .legacy(SharedSolanaLegacyMessage(legacy.message))
        case let v0 as KotlinMessageVersionedV0:
            message = .versionedV0(SharedSolanaVersionedMessageV0(v0.message))
        default:
            preconditionFailure("KotlinMessage has only Legacy and VersionedV0 cases")
        }
        self.init(
            message: message,
            signatures: transaction.signatures.map { Data($0.byteArray) }
        )
    }

    var kotlin: KotlinSolanaTransaction {
        let kotlinMessage: KotlinMessage
        switch message {
        case .legacy(let m):
            kotlinMessage = KotlinMessageLegacy(message: m.kotlin)
        case .versionedV0(let m):
            kotlinMessage = KotlinMessageVersionedV0(message: m.kotlin)
        }
        return KotlinSolanaTransaction(
            message: kotlinMessage,
            signatures: signatures.map { KotlinSignature(bytes: $0.kotlinByteList) }
        )
    }
}
