package com.getcode.opencode.internal.network.extensions

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.currency.v1.OcpCurrencyService
import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.internal.extensions.toHash
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.network.api.intents.actions.ActionFeePayment
import com.getcode.opencode.internal.network.api.intents.actions.ActionOpenAccount
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicTransfer
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicWithdraw
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.financial.FeeType
import com.getcode.opencode.model.transactions.VerifiedExchangeState
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.opencode.solana.intents.actions.ActionType
import com.getcode.solana.keys.PublicKey

// protobuf-java encoding of the intent types in :services:opencode-intents. The Wire encoding of the
// same types lives in :services:opencode-client; both must produce the same bytes.

fun IntentType.metadata(): OcpTransactionService.Metadata = metadata.asProtobufMetadata()

fun IntentType.requestToSubmitSignatures(): OcpTransactionService.SubmitIntentRequest {
    return OcpTransactionService.SubmitIntentRequest.newBuilder()
        .setSubmitSignatures(
            OcpTransactionService.SubmitIntentRequest.SubmitSignatures.newBuilder()
                .addAllSignatures(signatures.map { it.bytes.toByteArray().asSignature() })
        )
        .build()
}

fun IntentType.requestToSubmitActions(owner: Ed25519.KeyPair, deviceToken: String? = null): OcpTransactionService.SubmitIntentRequest {
    val submitActionsBuilder = OcpTransactionService.SubmitIntentRequest.SubmitActions.newBuilder()
    submitActionsBuilder.owner = owner.asSolanaAccountId()
    submitActionsBuilder.id = id.asIntentId()
    submitActionsBuilder.metadata = metadata()
    submitActionsBuilder.addAllActions(actions.map { it.action() })

    submitActionsBuilder.signature = submitActionsBuilder.sign(owner)

    return OcpTransactionService.SubmitIntentRequest.newBuilder()
        .setSubmitActions(submitActionsBuilder)
        .build()
}

fun ActionType.action(): OcpTransactionService.Action = when (this) {
    is ActionPublicTransfer -> OcpTransactionService.Action.newBuilder()
        .setId(id)
        .setNoPrivacyTransfer(
            OcpTransactionService.NoPrivacyTransferAction.newBuilder()
                .setSource(source.asSolanaAccountId())
                .setDestination(destination.asSolanaAccountId())
                .setAuthority(signer.asSolanaAccountId())
                .setAmount(amount.quarks)
                .setMint(mint.asSolanaAccountId())
                .build()
        ).build()

    is ActionPublicWithdraw -> OcpTransactionService.Action.newBuilder()
        .setId(id)
        .setNoPrivacyWithdraw(
            OcpTransactionService.NoPrivacyWithdrawAction.newBuilder()
                .setSource(source.vaultPublicKey.asSolanaAccountId())
                .setDestination(destination.asSolanaAccountId())
                .setAuthority(signer.asSolanaAccountId())
                .setAmount(amount.quarks)
                .setShouldClose(true)
                .setIsAutoReturn(canAutoReturn)
                .setMint(mint.asSolanaAccountId())
                .build()
        )
        .build()

    is ActionFeePayment -> OcpTransactionService.Action.newBuilder()
        .setId(id)
        .setFeePayment(
            OcpTransactionService.FeePaymentAction.newBuilder()
                .setSource(source.vaultPublicKey.asSolanaAccountId())
                .setType(
                    when (fee.type) {
                        FeeType.CreateOnSendWithdrawal -> OcpTransactionService.FeePaymentAction.FeeType.CREATE_ON_SEND_WITHDRAWAL
                    }
                )
                .setAuthority(source.authority.keyPair.asSolanaAccountId())
                .setAmount(fee.fiat.quarks)
                .setMint(mint.asSolanaAccountId())
                .build()
        ).build()

    is ActionOpenAccount -> openAccountAction()

    else -> throw IllegalArgumentException("No protobuf encoding for ${this::class.simpleName}")
}

// Kept as it was before the move: inside `apply`, `id` reads the builder's own id, so the action id
// is never set and encodes as 0. Open-account actions are always first in their intent, so 0 is
// also their position.
private fun ActionOpenAccount.openAccountAction(): OcpTransactionService.Action {
    return OcpTransactionService.Action.newBuilder()
        .apply trx@{
            val index = this@openAccountAction.index
            val owner = this@openAccountAction.owner
            this.id = id
            this.setOpenAccount(OcpTransactionService.OpenAccountAction.newBuilder()
                .setIndex(index)
                .setOwner(owner.authorityPublicKey.asSolanaAccountId())
                .setAccountType(accountType.getAccountType())
                .setAuthority(authority.authorityPublicKey.asSolanaAccountId())
                .setToken(token.vaultPublicKey.asSolanaAccountId())
                .setMint(mint.asSolanaAccountId())
                .apply {
                    setAuthoritySignature(sign(this@openAccountAction.authority.authority.keyPair))
                }
            )
        }
        .build()
}

fun ServerParameter.Companion.newInstance(proto: OcpTransactionService.ServerParameter): ServerParameter {
    return ServerParameter(
        actionId = proto.actionId,
        parameter = ServerParameter.Parameter.newInstance(proto),
        configs = proto.noncesList.map {
            ServerParameter.Config(
                nonce = it.nonce.value.toByteArray().toPublicKey(),
                blockhash = it.blockhash.value.toByteArray().toHash()
            )
        }
    )
}

fun ServerParameter.Parameter.Companion.newInstance(proto: OcpTransactionService.ServerParameter): ServerParameter.Parameter? {
    return when (proto.typeCase) {
        OcpTransactionService.ServerParameter.TypeCase.FEE_PAYMENT -> {
            val param = proto.feePayment

            // PublicKey will be `nil` for .thirdParty fee payments
            val optionalDestination = PublicKey(
                param.destination.value.toByteArray().toList()
            )
            ServerParameter.Parameter.FeePayment(optionalDestination)
        }
        OcpTransactionService.ServerParameter.TypeCase.OPEN_ACCOUNT,
        OcpTransactionService.ServerParameter.TypeCase.NO_PRIVACY_WITHDRAW,
        OcpTransactionService.ServerParameter.TypeCase.TYPE_NOT_SET,
        OcpTransactionService.ServerParameter.TypeCase.NO_PRIVACY_TRANSFER -> null
        else -> null
    }
}

fun AccountType.getAccountType(): Model.AccountType {
    return when (this) {
        AccountType.Primary -> Model.AccountType.PRIMARY
        AccountType.RemoteSend -> Model.AccountType.REMOTE_SEND_GIFT_CARD
        AccountType.Swap -> Model.AccountType.SWAP
        AccountType.AssociatedToken -> Model.AccountType.ASSOCIATED_TOKEN_ACCOUNT
        AccountType.Pool -> Model.AccountType.POOL
        AccountType.Unknown -> Model.AccountType.UNKNOWN
    }
}

fun AccountType.Companion.newInstance(accountType: Model.AccountType): AccountType? {
    return when (accountType) {
        Model.AccountType.PRIMARY -> AccountType.Primary
        Model.AccountType.UNKNOWN -> null
        Model.AccountType.REMOTE_SEND_GIFT_CARD -> AccountType.RemoteSend
        Model.AccountType.UNRECOGNIZED -> null
        Model.AccountType.SWAP -> AccountType.Swap
        Model.AccountType.ASSOCIATED_TOKEN_ACCOUNT -> AccountType.AssociatedToken
        Model.AccountType.POOL -> AccountType.Pool
    }
}

/** The signed rate as a protobuf-java message; parsed from [VerifiedExchangeState.encodedRate] unless it is already a [VerifiedState]. */
val VerifiedExchangeState.rateProto: OcpCurrencyService.VerifiedCoreMintFiatExchangeRate
    get() = (this as? VerifiedState)?.rateProto
        ?: OcpCurrencyService.VerifiedCoreMintFiatExchangeRate.parseFrom(encodedRate)

/** The signed reserve state as a protobuf-java message, or null when there is none. */
val VerifiedExchangeState.reserveProto: OcpCurrencyService.VerifiedLaunchpadCurrencyReserveState?
    get() = if (this is VerifiedState) reserveProto
    else encodedReserve?.let { OcpCurrencyService.VerifiedLaunchpadCurrencyReserveState.parseFrom(it) }

/** This state as a [VerifiedState], parsing the encoded messages when it is not one already. */
fun VerifiedExchangeState.asVerifiedState(): VerifiedState =
    this as? VerifiedState ?: VerifiedState(rateProto = rateProto, reserveProto = reserveProto)
