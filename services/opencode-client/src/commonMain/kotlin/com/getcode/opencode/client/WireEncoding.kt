package com.getcode.opencode.client

import com.getcode.opencode.transport.encodeCanonical
import com.codeinc.opencode.wire.account.v1.TokenAccountInfo
import com.codeinc.opencode.wire.common.v1.AccountType as WireAccountType
import com.codeinc.opencode.wire.common.v1.IntentId
import com.codeinc.opencode.wire.common.v1.Signature as WireSignature
import com.codeinc.opencode.wire.common.v1.SolanaAccountId
import com.codeinc.opencode.wire.currency.v1.StreamLiveMintDataResponse
import com.codeinc.opencode.wire.currency.v1.VerifiedCoreMintFiatExchangeRate
import com.codeinc.opencode.wire.currency.v1.VerifiedLaunchpadCurrencyReserveState
import com.codeinc.opencode.wire.transaction.v1.Action
import com.codeinc.opencode.wire.transaction.v1.AppMetadata
import com.codeinc.opencode.wire.transaction.v1.ExchangeData as WireExchangeData
import com.codeinc.opencode.wire.transaction.v1.FeePaymentAction
import com.codeinc.opencode.wire.transaction.v1.Metadata
import com.codeinc.opencode.wire.transaction.v1.NoPrivacyTransferAction
import com.codeinc.opencode.wire.transaction.v1.NoPrivacyWithdrawAction
import com.codeinc.opencode.wire.transaction.v1.OpenAccountAction
import com.codeinc.opencode.wire.transaction.v1.OpenAccountsMetadata
import com.codeinc.opencode.wire.transaction.v1.PublicDistributionMetadata
import com.codeinc.opencode.wire.transaction.v1.ReceivePaymentsPubliclyMetadata
import com.codeinc.opencode.wire.transaction.v1.SendPublicMultiPaymentMetadata
import com.codeinc.opencode.wire.transaction.v1.SendPublicPaymentMetadata
import com.codeinc.opencode.wire.transaction.v1.ServerParameter as WireServerParameter
import com.codeinc.opencode.wire.transaction.v1.SubmitIntentRequest
import com.codeinc.opencode.wire.transaction.v1.VerifiedExchangeData
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.internal.extensions.toHash
import com.getcode.opencode.internal.extensions.toMint
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.extensions.toSignature
import com.getcode.opencode.internal.network.api.intents.actions.ActionFeePayment
import com.getcode.opencode.internal.network.api.intents.actions.ActionOpenAccount
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicTransfer
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicWithdraw
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.LaunchpadReserveStateSnapshot
import com.getcode.opencode.model.financial.LiveMintDataResponse
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.VerifiedResponseData
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.model.financial.FeeType
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.model.transactions.VerifiedExchangeState
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.opencode.solana.intents.actions.ActionType
import com.getcode.solana.keys.PublicKey
import com.squareup.wire.Message
import okio.ByteString
import okio.ByteString.Companion.toByteString
import kotlin.time.Instant

// Wire encoding of the domain types, mirroring the protobuf-java encoding in :services:opencode
// (IntentProtobuf.kt, LocalToProtobuf.kt, AccountProtobuf.kt). ClientParityTest pins both to the same
// bytes and the same decoded values.

internal fun PublicKey.accountId() = SolanaAccountId(value_ = byteArray.toByteString())

internal fun Ed25519.KeyPair.accountId() = SolanaAccountId(value_ = publicKeyBytes.toByteString())

internal fun Message<*, *>.signedBy(signer: Ed25519.KeyPair) =
    WireSignature(value_ = Ed25519.sign(encodeCanonical(), signer).toByteString())

internal fun ByteString.toKey(): PublicKey = toByteArray().toPublicKey()

internal fun Long.secondsToInstant(): Instant = Instant.fromEpochMilliseconds(this * 1_000L)

// region SubmitIntent

internal fun IntentType.submitActions(owner: Ed25519.KeyPair): SubmitIntentRequest {
    val unsigned = SubmitIntentRequest.SubmitActions(
        id = IntentId(value_ = id.byteArray.toByteString()),
        owner = owner.accountId(),
        metadata = metadata.toWire(),
        actions = actions.map { it.toWire() },
    )
    return SubmitIntentRequest(submit_actions = unsigned.copy(signature = unsigned.signedBy(owner)))
}

internal fun IntentType.submitSignatures(): SubmitIntentRequest = SubmitIntentRequest(
    submit_signatures = SubmitIntentRequest.SubmitSignatures(
        signatures = signatures.map { WireSignature(value_ = it.bytes.toByteArray().toByteString()) },
    ),
)

internal fun ActionType.toWire(): Action = when (this) {
    is ActionPublicTransfer -> Action(
        id = id,
        no_privacy_transfer = NoPrivacyTransferAction(
            authority = signer.accountId(),
            source = source.accountId(),
            destination = destination.accountId(),
            amount = amount.quarks,
            mint = mint.accountId(),
        ),
    )

    is ActionPublicWithdraw -> Action(
        id = id,
        no_privacy_withdraw = NoPrivacyWithdrawAction(
            authority = signer.accountId(),
            source = source.vaultPublicKey.accountId(),
            destination = destination.accountId(),
            amount = amount.quarks,
            should_close = true,
            is_auto_return = canAutoReturn,
            mint = mint.accountId(),
        ),
    )

    is ActionFeePayment -> Action(
        id = id,
        fee_payment = FeePaymentAction(
            type = when (fee.type) {
                FeeType.CreateOnSendWithdrawal -> FeePaymentAction.FeeType.CREATE_ON_SEND_WITHDRAWAL
            },
            authority = source.authority.keyPair.accountId(),
            source = source.vaultPublicKey.accountId(),
            amount = fee.fiat.quarks,
            mint = mint.accountId(),
        ),
    )

    // Android never sets the open-account action id (see IntentProtobuf.kt), so it encodes as 0.
    is ActionOpenAccount -> {
        val unsigned = OpenAccountAction(
            account_type = accountType.toWire(),
            owner = owner.authorityPublicKey.accountId(),
            index = index,
            authority = authority.authorityPublicKey.accountId(),
            token = token.vaultPublicKey.accountId(),
            mint = mint.accountId(),
        )
        Action(
            id = 0,
            open_account = unsigned.copy(authority_signature = unsigned.signedBy(authority.authority.keyPair)),
        )
    }

    else -> throw IllegalArgumentException("No Wire encoding for ${this::class.simpleName}")
}

internal fun AccountType.toWire(): WireAccountType = when (this) {
    AccountType.Primary -> WireAccountType.PRIMARY
    AccountType.RemoteSend -> WireAccountType.REMOTE_SEND_GIFT_CARD
    AccountType.Swap -> WireAccountType.SWAP
    AccountType.AssociatedToken -> WireAccountType.ASSOCIATED_TOKEN_ACCOUNT
    AccountType.Pool -> WireAccountType.POOL
    AccountType.Unknown -> WireAccountType.UNKNOWN
}

internal fun WireAccountType.toDomain(): AccountType? = when (this) {
    WireAccountType.PRIMARY -> AccountType.Primary
    WireAccountType.REMOTE_SEND_GIFT_CARD -> AccountType.RemoteSend
    WireAccountType.SWAP -> AccountType.Swap
    WireAccountType.ASSOCIATED_TOKEN_ACCOUNT -> AccountType.AssociatedToken
    WireAccountType.POOL -> AccountType.Pool
    WireAccountType.UNKNOWN -> null
}

internal fun TransactionMetadata.toWire(): Metadata = when (this) {
    is TransactionMetadata.OpenAccount -> Metadata(
        open_accounts = OpenAccountsMetadata(
            account_set = when (type) {
                AccountType.Pool -> OpenAccountsMetadata.AccountSet.POOL
                AccountType.Primary -> OpenAccountsMetadata.AccountSet.USER
                // protobuf-java refuses UNRECOGNIZED here too.
                else -> throw IllegalArgumentException("No account set for $type")
            },
            mint = mint.accountId(),
        ),
    )

    // Exchange data is not sent on incoming transactions.
    is TransactionMetadata.ReceivePublicPayment -> Metadata(
        receive_payments_publicly = ReceivePaymentsPubliclyMetadata(
            source = source.accountId(),
            quarks = quarks,
            is_indirect_send = isIndirect,
            mint = mint.accountId(),
        ),
    )

    is TransactionMetadata.SendPublicPayment -> Metadata(
        send_public_payment = SendPublicPaymentMetadata(
            source = source.accountId(),
            destination = destination.accountId(),
            destination_owner = destinationOwner?.accountId(),
            client_exchange_data = verifiedExchangeData?.toWire(),
            is_withdrawal = isWithdrawal,
            is_indirect_send = isIndirect,
            mint = exchangeData.mint.accountId(),
        ),
        app_metadata = appMetadata?.let { AppMetadata(value_ = it.toByteString()) },
    )

    is TransactionMetadata.SendPublicMultiPayment -> Metadata(
        send_public_multi_payment = SendPublicMultiPaymentMetadata(
            source = source.accountId(),
            mint = mint.accountId(),
            payments = payments.map { payment ->
                SendPublicMultiPaymentMetadata.Payment(
                    destination = payment.destination.accountId(),
                    destination_owner = payment.destinationOwner?.accountId(),
                    client_exchange_data = payment.verifiedExchangeData?.toWire(),
                )
            },
        ),
        app_metadata = appMetadata?.let { AppMetadata(value_ = it.toByteString()) },
    )

    is TransactionMetadata.PublicDistribution -> Metadata(
        public_distribution = PublicDistributionMetadata(
            source = source.accountId(),
            mint = mint.accountId(),
            distributions = distributions.map {
                PublicDistributionMetadata.Distribution(
                    destination = it.destination.accountId(),
                    quarks = it.amount.quarks,
                )
            },
        ),
    )

    TransactionMetadata.Unknown -> Metadata()
}

internal fun ExchangeData.Verified.toWire() = VerifiedExchangeData(
    mint = mint.accountId(),
    quarks = quarks,
    native_amount = nativeAmount,
    core_mint_fiat_exchange_rate = VerifiedCoreMintFiatExchangeRate.ADAPTER.decode(verifiedState.encodedRate),
    launchpad_currency_reserve_state = verifiedState.encodedReserve?.let {
        VerifiedLaunchpadCurrencyReserveState.ADAPTER.decode(it)
    },
)

internal fun WireServerParameter.toDomain() = ServerParameter(
    actionId = action_id,
    // The destination is empty for third-party fee payments, as on Android.
    parameter = fee_payment?.let {
        ServerParameter.Parameter.FeePayment(it.destination?.value_?.toKey() ?: PublicKey(emptyList()))
    },
    configs = nonces.map {
        ServerParameter.Config(
            nonce = requireNotNull(it.nonce).value_.toKey(),
            blockhash = requireNotNull(it.blockhash).value_.toByteArray().toHash(),
        )
    },
)

// endregion

// region Responses

/** A server-signed rate, kept with its encoded bytes so it can be re-embedded in SubmitIntent. */
internal class WireVerifiedState(
    rate: VerifiedCoreMintFiatExchangeRate,
    reserve: VerifiedLaunchpadCurrencyReserveState?,
) : VerifiedExchangeState {
    override val currencyCode: String = rate.exchange_rate?.currency_code.orEmpty()
    override val exchangeRate: Double = rate.exchange_rate?.exchange_rate ?: 0.0
    override val encodedRate: ByteArray = rate.encode()
    override val encodedReserve: ByteArray? = reserve?.encode()
}

internal fun StreamLiveMintDataResponse.LiveData.toDomain(): LiveMintDataResponse? {
    core_mint_fiat_exchange_rates?.let { batch ->
        return LiveMintDataResponse.ExchangeRates(
            rates = batch.exchange_rates.mapNotNull { verified ->
                val rate = verified.exchange_rate ?: return@mapNotNull null
                val code = CurrencyCode.tryValueOf(rate.currency_code) ?: return@mapNotNull null
                VerifiedResponseData.ExchangeRate(
                    rate = Rate(fx = rate.exchange_rate, currency = code),
                    timestamp = (rate.timestamp?.getEpochSecond() ?: 0L).secondsToInstant(),
                    signature = verified.signature?.value_?.toByteArray()?.toSignature() ?: ByteArray(64).toSignature(),
                )
            },
        )
    }
    launchpad_currency_reserve_states?.let { batch ->
        return LiveMintDataResponse.LaunchpadReserveState(
            reserveStates = batch.reserve_states.map { verified ->
                val state = requireNotNull(verified.reserve_state)
                VerifiedResponseData.LaunchpadReserveState(
                    reserveState = LaunchpadReserveStateSnapshot(
                        mint = requireNotNull(state.mint).value_.toByteArray().toMint(),
                        currentSupply = state.supply_from_bonding,
                        timestamp = (state.timestamp?.getEpochSecond() ?: 0L).secondsToInstant(),
                    ),
                    signature = verified.signature?.value_?.toByteArray()?.toSignature() ?: ByteArray(64).toSignature(),
                )
            },
        )
    }
    return null
}

internal fun TokenAccountInfo.toDomain(): AccountInfo? {
    val accountType = account_type.toDomain() ?: return null
    return AccountInfo(
        index = index.toInt(),
        accountType = accountType,
        address = address.bytes().toPublicKey(),
        owner = owner.bytes().toPublicKey(),
        authority = authority.bytes().toPublicKey(),
        balanceSource = when (balance_source) {
            TokenAccountInfo.BalanceSource.BALANCE_SOURCE_UNKNOWN -> AccountInfo.BalanceSource.Unknown
            TokenAccountInfo.BalanceSource.BALANCE_SOURCE_BLOCKCHAIN -> AccountInfo.BalanceSource.Blockchain
            TokenAccountInfo.BalanceSource.BALANCE_SOURCE_CACHE -> AccountInfo.BalanceSource.Cache
        },
        balance = balance,
        managementState = when (management_state) {
            TokenAccountInfo.ManagementState.MANAGEMENT_STATE_UNKNOWN -> AccountInfo.ManagementState.Unknown
            TokenAccountInfo.ManagementState.MANAGEMENT_STATE_NONE -> AccountInfo.ManagementState.None
            TokenAccountInfo.ManagementState.MANAGEMENT_STATE_LOCKING -> AccountInfo.ManagementState.Locking
            TokenAccountInfo.ManagementState.MANAGEMENT_STATE_LOCKED -> AccountInfo.ManagementState.Locked
            TokenAccountInfo.ManagementState.MANAGEMENT_STATE_UNLOCKING -> AccountInfo.ManagementState.Unlocking
            TokenAccountInfo.ManagementState.MANAGEMENT_STATE_UNLOCKED -> AccountInfo.ManagementState.Unlocked
            TokenAccountInfo.ManagementState.MANAGEMENT_STATE_CLOSING -> AccountInfo.ManagementState.Closing
            TokenAccountInfo.ManagementState.MANAGEMENT_STATE_CLOSED -> AccountInfo.ManagementState.Closed
        },
        blockchainState = when (blockchain_state) {
            TokenAccountInfo.BlockchainState.BLOCKCHAIN_STATE_UNKNOWN -> AccountInfo.BlockchainState.Unknown
            TokenAccountInfo.BlockchainState.BLOCKCHAIN_STATE_DOES_NOT_EXIST -> AccountInfo.BlockchainState.DoesntExist
            TokenAccountInfo.BlockchainState.BLOCKCHAIN_STATE_EXISTS -> AccountInfo.BlockchainState.Exists
        },
        claimState = when (claim_state) {
            TokenAccountInfo.ClaimState.CLAIM_STATE_UNKNOWN -> AccountInfo.ClaimState.Unknown
            TokenAccountInfo.ClaimState.CLAIM_STATE_NOT_CLAIMED -> AccountInfo.ClaimState.NotClaimed
            TokenAccountInfo.ClaimState.CLAIM_STATE_CLAIMED -> AccountInfo.ClaimState.Claimed
            TokenAccountInfo.ClaimState.CLAIM_STATE_EXPIRED -> AccountInfo.ClaimState.Expired
        },
        originalExchangeData = (original_exchange_data ?: WireExchangeData()).let {
            ExchangeData.WithRate(
                currencyCode = it.currency,
                exchangeRate = it.exchange_rate,
                nativeAmount = it.native_amount,
                quarks = it.quarks,
                mint = it.mint.bytes().toMint(),
            )
        },
        mint = mint.bytes().toMint(),
        createdAt = (created_at?.getEpochSecond() ?: 0L) * 1000L,
        isGiftCardIssuer = is_gift_card_issuer,
        usdCostBasis = usd_cost_basis,
    )
}

/** An unset account id reads as empty bytes, as protobuf-java's default instance does. */
private fun SolanaAccountId?.bytes(): ByteArray = this?.value_?.toByteArray() ?: ByteArray(0)

// endregion
