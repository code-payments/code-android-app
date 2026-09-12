import SharedCore

/// `Kotlin`-prefixed aliases for the `SharedCore` Solana message/instruction/account type
/// hierarchy that `SolanaMessage.swift` and `SolanaTransaction.swift` bridge. Both files already
/// define their own `Shared*`-prefixed public types (`SharedSolanaMessage`,
/// `SharedSolanaInstruction`, ...); qualifying every Kotlin-side reference as `SharedCore.X` reads,
/// at a glance, as if it were one of those — the aliases here exist purely so call sites can say
/// `KotlinMessage` and have it unambiguously mean "the Kotlin type", not "the Swift facade type".
///
/// `internal`, not `public`: these aliases are a readability aid for this package's own bridging
/// code. Nothing outside `SharedCoreKit` should see a raw Kotlin type — the whole point of the
/// `Shared*` facade is that callers never touch `SharedCore` directly.
internal typealias KotlinAccountMeta = SharedCore.AccountMeta
internal typealias KotlinAddressLookupTable = SharedCore.AddressLookupTable
internal typealias KotlinCompiledInstruction = SharedCore.CompiledInstruction
internal typealias KotlinInstruction = SharedCore.Instruction
internal typealias KotlinKey32 = SharedCore.Key32
internal typealias KotlinLegacyMessage = SharedCore.LegacyMessage
internal typealias KotlinMessage = SharedCore.Message
internal typealias KotlinMessageAddressLookupTable = SharedCore.MessageAddressLookupTable
internal typealias KotlinMessageCompanion = SharedCore.MessageCompanion
internal typealias KotlinMessageHeader = SharedCore.MessageHeader
internal typealias KotlinMessageLegacy = SharedCore.MessageLegacy
internal typealias KotlinMessageVersionedV0 = SharedCore.MessageVersionedV0
internal typealias KotlinPublicKey = SharedCore.PublicKey
internal typealias KotlinSignature = SharedCore.Signature
internal typealias KotlinSolanaTransaction = SharedCore.SolanaTransaction
internal typealias KotlinVersionedMessageV0 = SharedCore.VersionedMessageV0
