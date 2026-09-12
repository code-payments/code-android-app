import Foundation
import SharedCore

/// Swift-idiomatic facade over Kotlin's Solana message/instruction/account type hierarchy in
/// `:libs:solana:encoding`, for callers that need to construct, inspect, and mutate a message —
/// not just round-trip its bytes (see `SolanaEncoding.swift` for that flat entry point).
///
/// Every type here is a plain Swift value type; none of them stores a live Kotlin object. Kotlin's
/// `AccountMeta`/`Instruction`/`CompiledInstruction`/`MessageHeader`/`LegacyMessage`/
/// `VersionedMessageV0` are all mutable reference types crossing the bridge, and Kotlin's `Message`
/// is a `sealed interface` (an Obj-C protocol) rather than a value type — holding on to any of them
/// directly would reintroduce reference semantics and non-exhaustive `switch` into Swift. Instead,
/// every Kotlin object is built fresh at the point it's needed (`encode()`, a constructor call) and
/// discarded immediately after; the `kotlin`/`init(_:)` pairs below are the only places that happen.
///
/// `PublicKey`/`Hash`/`Signature` all appear here as plain 32- or 64-byte `Data`, never as a
/// dedicated wrapper type: Kotlin's `Hash` is a `typealias` for `Key32` with no ObjC class of its
/// own, and modeling `PublicKey` as anything other than `Data` would just be a second wrapper for
/// FlipcashCore's existing one to unwrap. Constructing any of `Key32`/`PublicKey`/`Signature` from
/// raw bytes boxes every byte (`KotlinByteList+Bridge.swift`) because none of them has a
/// raw-`ByteArray` initializer — only `KeyType.byteArray` (reading one back out) is unboxed.

// MARK: - Header

public struct SharedSolanaMessageHeader: Equatable, Sendable {
    public var requiredSignatures: Int
    public var readOnlySigners: Int
    public var readOnly: Int

    public init(requiredSignatures: Int, readOnlySigners: Int, readOnly: Int) {
        self.requiredSignatures = requiredSignatures
        self.readOnlySigners = readOnlySigners
        self.readOnly = readOnly
    }
}

extension SharedSolanaMessageHeader {
    init(_ header: KotlinMessageHeader) {
        self.init(
            requiredSignatures: Int(header.requiredSignatures),
            readOnlySigners: Int(header.readOnlySigners),
            readOnly: Int(header.readOnly)
        )
    }

    var kotlin: KotlinMessageHeader {
        KotlinMessageHeader(
            requiredSignatures: Int32(requiredSignatures),
            readOnlySigners: Int32(readOnlySigners),
            readOnly: Int32(readOnly)
        )
    }
}

// MARK: - Version

/// Mirrors Kotlin's `MessageVersion` enum class — the one-byte version tag a `Message` carries.
public enum SharedSolanaMessageVersion: Sendable {
    case legacy
    case v0
}

// MARK: - Account meta

public struct SharedSolanaAccountMeta: Equatable, Sendable {
    public var publicKey: Data
    public var isSigner: Bool
    public var isWritable: Bool
    public var isPayer: Bool
    public var isProgram: Bool

    public init(publicKey: Data, isSigner: Bool, isWritable: Bool, isPayer: Bool, isProgram: Bool) {
        self.publicKey = publicKey
        self.isSigner = isSigner
        self.isWritable = isWritable
        self.isPayer = isPayer
        self.isProgram = isProgram
    }

    // Mirrors `AccountMeta.Companion`'s factories exactly (plain field assignment — nothing here
    // is a Kotlin call, since there is no algorithm to diverge on).
    public static func payer(publicKey: Data) -> SharedSolanaAccountMeta {
        SharedSolanaAccountMeta(publicKey: publicKey, isSigner: true, isWritable: true, isPayer: true, isProgram: false)
    }

    public static func writable(publicKey: Data, signer: Bool = false) -> SharedSolanaAccountMeta {
        SharedSolanaAccountMeta(publicKey: publicKey, isSigner: signer, isWritable: true, isPayer: false, isProgram: false)
    }

    public static func readonly(publicKey: Data, signer: Bool = false) -> SharedSolanaAccountMeta {
        SharedSolanaAccountMeta(publicKey: publicKey, isSigner: signer, isWritable: false, isPayer: false, isProgram: false)
    }

    public static func program(publicKey: Data) -> SharedSolanaAccountMeta {
        SharedSolanaAccountMeta(publicKey: publicKey, isSigner: false, isWritable: false, isPayer: false, isProgram: true)
    }
}

extension SharedSolanaAccountMeta {
    init(_ meta: KotlinAccountMeta) {
        self.init(
            publicKey: Data(meta.publicKey.byteArray),
            isSigner: meta.isSigner,
            isWritable: meta.isWritable,
            isPayer: meta.isPayer,
            isProgram: meta.isProgram
        )
    }

    var kotlin: KotlinAccountMeta {
        KotlinAccountMeta(
            publicKey: KotlinPublicKey(bytes: publicKey.kotlinByteList),
            isSigner: isSigner,
            isWritable: isWritable,
            isPayer: isPayer,
            isProgram: isProgram
        )
    }
}

// MARK: - Instructions

/// An uncompiled instruction: accounts referenced by full `AccountMeta`, not by index. This is what
/// a caller builds by hand and what `LegacyMessage.instructions` stores; `compile(messageAccounts:)`
/// turns it into a `CompiledInstruction` against a specific account ordering.
public struct SharedSolanaInstruction: Equatable, Sendable {
    public var program: Data
    public var accounts: [SharedSolanaAccountMeta]
    public var data: Data

    public init(program: Data, accounts: [SharedSolanaAccountMeta], data: Data) {
        self.program = program
        self.accounts = accounts
        self.data = data
    }

    /// Compiles this instruction against `messageAccounts` by replacing `program` and each
    /// account's key with its index into `messageAccounts`. `nil` if `program` or any of
    /// `accounts` is not present in `messageAccounts`.
    public func compile(messageAccounts: [Data]) -> SharedSolanaCompiledInstruction? {
        let accounts = messageAccounts.map { KotlinPublicKey(bytes: $0.kotlinByteList) }
        guard let result = kotlin.compile(messageAccounts: accounts) else { return nil }
        return SharedSolanaCompiledInstruction(result)
    }
}

extension SharedSolanaInstruction {
    init(_ instruction: KotlinInstruction) {
        self.init(
            program: Data(instruction.program.byteArray),
            accounts: instruction.accounts.map(SharedSolanaAccountMeta.init),
            data: Data(kotlinByteList: instruction.data)
        )
    }

    var kotlin: KotlinInstruction {
        KotlinInstruction(
            program: KotlinPublicKey(bytes: program.kotlinByteList),
            accounts: accounts.map { $0.kotlin },
            data: data.kotlinByteList
        )
    }
}

/// A compiled instruction: accounts referenced by index into the enclosing message's account list.
/// This is the only instruction shape `VersionedMessageV0` stores, and what `Message.instructions`
/// (the unified, cross-version view) always returns.
public struct SharedSolanaCompiledInstruction: Equatable, Sendable {
    public var programIndex: UInt8
    public var accountIndexes: [UInt8]
    public var data: Data

    public init(programIndex: UInt8, accountIndexes: [UInt8], data: Data) {
        self.programIndex = programIndex
        self.accountIndexes = accountIndexes
        self.data = data
    }

    /// Parses a single wire-format compiled instruction — `programIndex(1) + shortVec(accountIndexes) +
    /// shortVec(data)` — with no enclosing message. `nil` if `data` is short or malformed.
    public init?(data: Data) {
        guard let result = KotlinCompiledInstruction.companion.fromList(list: data.kotlinByteList) else { return nil }
        self.init(result)
    }

    public func encode() -> Data {
        Data(kotlinByteList: kotlin.encode())
    }

    /// Resolves indexes back to full account references against `accounts`. `nil` if `accounts` is
    /// too short for the indexes this instruction carries.
    public func decompile(accounts: [SharedSolanaAccountMeta]) -> SharedSolanaInstruction? {
        guard let result = kotlin.decompile(accounts: accounts.map { $0.kotlin }) else { return nil }
        return SharedSolanaInstruction(result)
    }
}

extension SharedSolanaCompiledInstruction {
    init(_ instruction: KotlinCompiledInstruction) {
        self.init(
            programIndex: UInt8(bitPattern: instruction.programIndex),
            accountIndexes: [UInt8](kotlinByteList: instruction.accountIndexes),
            data: Data(kotlinByteList: instruction.data)
        )
    }

    var kotlin: KotlinCompiledInstruction {
        KotlinCompiledInstruction(
            programIndex: Int8(bitPattern: programIndex),
            accountIndexes: accountIndexes.kotlinByteList,
            data: data.kotlinByteList
        )
    }
}

// MARK: - Address lookup tables

/// An entry in a `VersionedMessageV0`'s `addressLookupTables` — which on-chain table, and which of
/// its indexes this message loads as writable vs. read-only. Mirrors Kotlin's
/// `MessageAddressLookupTable` (internal name; FlipcashCore calls the equivalent
/// `MessageAddressTableLookup`).
public struct SharedSolanaMessageAddressTableLookup: Equatable, Sendable {
    public var publicKey: Data
    public var writableIndexes: [UInt8]
    public var readonlyIndexes: [UInt8]

    public init(publicKey: Data, writableIndexes: [UInt8], readonlyIndexes: [UInt8]) {
        self.publicKey = publicKey
        self.writableIndexes = writableIndexes
        self.readonlyIndexes = readonlyIndexes
    }

    public func encode() -> Data {
        Data(kotlinByteList: kotlin.encode())
    }
}

extension SharedSolanaMessageAddressTableLookup {
    init(_ lookup: KotlinMessageAddressLookupTable) {
        self.init(
            publicKey: Data(lookup.publicKey.byteArray),
            writableIndexes: [UInt8](kotlinByteList: lookup.writableIndexes),
            readonlyIndexes: [UInt8](kotlinByteList: lookup.readonlyIndexes)
        )
    }

    var kotlin: KotlinMessageAddressLookupTable {
        KotlinMessageAddressLookupTable(
            publicKey: KotlinPublicKey(bytes: publicKey.kotlinByteList),
            writableIndexes: writableIndexes.kotlinByteList,
            readonlyIndexes: readonlyIndexes.kotlinByteList
        )
    }
}

/// A full on-chain address lookup table — input to V0 transaction construction
/// (`SharedSolanaTransaction.init(payer:recentBlockhash:addressLookupTables:instructions:)`), not
/// something a decoded message ever hands back (a decoded `VersionedMessageV0` only knows the
/// indexes it used — `addressTableLookups` — never the table's full address list). Mirrors Kotlin's
/// `com.getcode.opencode.model.transactions.AddressLookupTable`.
public struct SharedSolanaAddressLookupTable: Equatable, Sendable {
    public var publicKey: Data
    public var addresses: [Data]

    public init(publicKey: Data, addresses: [Data]) {
        self.publicKey = publicKey
        self.addresses = addresses
    }
}

extension SharedSolanaAddressLookupTable {
    var kotlin: KotlinAddressLookupTable {
        KotlinAddressLookupTable(
            publicKey: KotlinPublicKey(bytes: publicKey.kotlinByteList),
            addresses: addresses.map { KotlinPublicKey(bytes: $0.kotlinByteList) }
        )
    }
}

// MARK: - Legacy message

public struct SharedSolanaLegacyMessage: Equatable, Sendable {
    public var header: SharedSolanaMessageHeader
    public var accounts: [SharedSolanaAccountMeta]
    public var recentBlockhash: Data
    public var instructions: [SharedSolanaInstruction]

    public init(header: SharedSolanaMessageHeader, accounts: [SharedSolanaAccountMeta], recentBlockhash: Data, instructions: [SharedSolanaInstruction]) {
        self.header = header
        self.accounts = accounts
        self.recentBlockhash = recentBlockhash
        self.instructions = instructions
    }

    public func encode() -> Data {
        Data(kotlin.encode())
    }
}

extension SharedSolanaLegacyMessage {
    init(_ message: KotlinLegacyMessage) {
        self.init(
            header: SharedSolanaMessageHeader(message.header),
            accounts: message.accounts.map(SharedSolanaAccountMeta.init),
            recentBlockhash: Data(message.recentBlockhash.byteArray),
            instructions: message.instructions.map(SharedSolanaInstruction.init)
        )
    }

    var kotlin: KotlinLegacyMessage {
        KotlinLegacyMessage(
            header: header.kotlin,
            accounts: accounts.map { $0.kotlin },
            recentBlockhash: KotlinKey32(bytes: recentBlockhash.kotlinByteList),
            instructions: instructions.map { $0.kotlin }
        )
    }
}

// MARK: - Versioned V0 message

public struct SharedSolanaVersionedMessageV0: Equatable, Sendable {
    public var header: SharedSolanaMessageHeader
    public var staticAccountKeys: [Data]
    public var recentBlockhash: Data
    public var instructions: [SharedSolanaCompiledInstruction]
    public var addressLookupTables: [SharedSolanaMessageAddressTableLookup]

    public init(
        header: SharedSolanaMessageHeader,
        staticAccountKeys: [Data],
        recentBlockhash: Data,
        instructions: [SharedSolanaCompiledInstruction],
        addressLookupTables: [SharedSolanaMessageAddressTableLookup]
    ) {
        self.header = header
        self.staticAccountKeys = staticAccountKeys
        self.recentBlockhash = recentBlockhash
        self.instructions = instructions
        self.addressLookupTables = addressLookupTables
    }

    public func encode() -> Data {
        Data(kotlinByteList: kotlin.encode())
    }
}

extension SharedSolanaVersionedMessageV0 {
    init(_ message: KotlinVersionedMessageV0) {
        self.init(
            header: SharedSolanaMessageHeader(message.header),
            staticAccountKeys: message.staticAccountKeys.map { Data($0.byteArray) },
            recentBlockhash: Data(message.recentBlockhash.byteArray),
            instructions: message.instructions.map(SharedSolanaCompiledInstruction.init),
            addressLookupTables: message.addressLookupTables.map(SharedSolanaMessageAddressTableLookup.init)
        )
    }

    var kotlin: KotlinVersionedMessageV0 {
        KotlinVersionedMessageV0(
            header: header.kotlin,
            staticAccountKeys: staticAccountKeys.map { KotlinPublicKey(bytes: $0.kotlinByteList) },
            recentBlockhash: KotlinKey32(bytes: recentBlockhash.kotlinByteList),
            instructions: instructions.map { $0.kotlin },
            addressLookupTables: addressLookupTables.map { $0.kotlin }
        )
    }
}

// MARK: - Message

/// A Solana message, legacy or versioned-V0. This is the Swift value-type replacement for Kotlin's
/// `Message` `sealed interface` (an Obj-C protocol — reference-typed, no exhaustive `switch`):
/// exactly the two cases Kotlin's `sealed interface` permits, so a `switch` over this enum is
/// exhaustive the same way a `when` over the Kotlin type is.
///
/// `recentBlockhash`'s setter reassigns `self` with a copy carrying the new value, where Kotlin's
/// setter mutates the wrapped message object in place. Both are observably equivalent for every
/// caller that only ever holds one reference to the message (the overwhelmingly common case —
/// refreshing a blockhash immediately before signing), but two Kotlin references to the same
/// `Message` would observe each other's mutation where two Swift copies of this enum would not.
/// That is the one place this type cannot losslessly mirror the Kotlin protocol's reference
/// semantics — a deliberate trade for the value semantics constraint #3 asks for.
public enum SharedSolanaMessage: Equatable, Sendable {
    case legacy(SharedSolanaLegacyMessage)
    case versionedV0(SharedSolanaVersionedMessageV0)

    public var version: SharedSolanaMessageVersion {
        switch self {
        case .legacy: return .legacy
        case .versionedV0: return .v0
        }
    }

    public var header: SharedSolanaMessageHeader {
        switch self {
        case .legacy(let message): return message.header
        case .versionedV0(let message): return message.header
        }
    }

    public var accountKeys: [Data] {
        switch self {
        case .legacy(let message): return message.accounts.map(\.publicKey)
        case .versionedV0(let message): return message.staticAccountKeys
        }
    }

    public var recentBlockhash: Data {
        get {
            switch self {
            case .legacy(let message): return message.recentBlockhash
            case .versionedV0(let message): return message.recentBlockhash
            }
        }
        set {
            switch self {
            case .legacy(var message):
                message.recentBlockhash = newValue
                self = .legacy(message)
            case .versionedV0(var message):
                message.recentBlockhash = newValue
                self = .versionedV0(message)
            }
        }
    }

    /// Compiled instructions, uniform across both message kinds — mirrors the Kotlin protocol's own
    /// computed property, which recompiles a `Legacy` message's instructions against `accountKeys`
    /// on every access rather than caching them.
    public var instructions: [SharedSolanaCompiledInstruction] {
        switch self {
        case .legacy(let message):
            // `accounts` above is this message's own full account list, and `message.instructions`
            // are this same message's instructions, so every program/account an instruction
            // references is always present in `accounts` — `compile` returning `nil` here would
            // mean this message's own invariant was violated elsewhere.
            let accounts = message.accounts.map(\.publicKey)
            return message.instructions.map { instruction in
                guard let compiled = instruction.compile(messageAccounts: accounts) else {
                    fatalError("instruction references an account missing from this message")
                }
                return compiled
            }
        case .versionedV0(let message):
            return message.instructions
        }
    }

    /// Empty for `.legacy` — address lookup tables are a V0-only concept.
    public var addressTableLookups: [SharedSolanaMessageAddressTableLookup] {
        switch self {
        case .legacy: return []
        case .versionedV0(let message): return message.addressLookupTables
        }
    }

    public func encode() -> Data {
        switch self {
        case .legacy(let message): return message.encode()
        case .versionedV0(let message): return message.encode()
        }
    }

    /// Parses `data` as a message — legacy or v0, decided by the version-prefix byte. `nil` if
    /// `data` matches neither wire format.
    public init?(data: Data) {
        guard let result = KotlinMessageCompanion.shared.doNewInstance(data: data.kotlinByteList) else { return nil }
        switch result {
        case let legacy as KotlinMessageLegacy:
            self = .legacy(SharedSolanaLegacyMessage(legacy.message))
        case let v0 as KotlinMessageVersionedV0:
            self = .versionedV0(SharedSolanaVersionedMessageV0(v0.message))
        default:
            return nil
        }
    }
}
