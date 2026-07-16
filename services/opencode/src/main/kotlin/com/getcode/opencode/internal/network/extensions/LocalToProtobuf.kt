package com.getcode.opencode.internal.network.extensions

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.currency.v1.OcpCurrencyService
import com.codeinc.opencode.gen.messaging.v1.OcpMessagingService
import com.codeinc.opencode.gen.messaging.v1.requestToGiveBill
import com.codeinc.opencode.gen.messaging.v1.requestToGrabBill
import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService.OpenAccountsMetadata.AccountSet
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.SocialLink
import com.getcode.opencode.model.messaging.Message
import com.getcode.opencode.model.messaging.MessageKind
import com.getcode.opencode.model.moderation.ModerationAttestation
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.model.transactions.GiveRequest
import com.getcode.opencode.model.transactions.GrabRequest
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.model.transactions.StatefulSwapRequest
import com.getcode.opencode.model.transactions.SwapStartKind
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.model.transactions.TransferRequest
import com.getcode.opencode.model.ui.BillBackground
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import com.getcode.utils.base58
import com.getcode.utils.toByteString
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp

internal fun ByteArray.asSignature(): Model.Signature {
    return Model.Signature.newBuilder().setValue(this.toByteString())
        .build()
}

internal fun KeyPair.asSignature(): Model.Signature {
    return Model.Signature.newBuilder().setValue(this.publicKeyBytes.toByteString())
        .build()
}

internal fun Signature.asSignature(): Model.Signature {
    return Model.Signature.newBuilder().setValue(this.bytes.toByteString())
        .build()
}

internal fun KeyPair.asSolanaAccountId(): Model.SolanaAccountId {
    return Model.SolanaAccountId.newBuilder().setValue(this.publicKeyBytes.toByteString()).build()
}

internal fun PublicKey.asSolanaAccountId(): Model.SolanaAccountId {
    return Model.SolanaAccountId.newBuilder().setValue(this.byteArray.toByteString()).build()
}

internal fun Hash.asSolanaBlockHash(): Model.Blockhash {
    return Model.Blockhash.newBuilder().setValue(this.bytes.toByteString()).build()
}

internal fun PublicKey.asMessageId(): OcpMessagingService.MessageId {
    return OcpMessagingService.MessageId.newBuilder().setValue(this.byteArray.toByteString()).build()
}

internal fun PublicKey.asIntentId(): Model.IntentId {
    return Model.IntentId.newBuilder().setValue(this.byteArray.toByteString()).build()
}

internal fun SwapId.asSwapId(): Model.SwapId {
    return Model.SwapId.newBuilder().setValue(this.publicKey.bytes.toByteString()).build()
}

internal fun PublicKey.asRendezvousKey(): OcpMessagingService.RendezvousKey {
    return OcpMessagingService.RendezvousKey.newBuilder().setValue(this.bytes.toByteString())
        .build()
}

internal fun KeyPair.asRendezvousKey(): OcpMessagingService.RendezvousKey {
    return OcpMessagingService.RendezvousKey.newBuilder().setValue(
        ByteString.copyFrom(publicKeyBytes)
    ).build()
}

internal fun openMessageStreamRequest(rendezvous: KeyPair): OcpMessagingService.OpenMessageStreamRequest {
    return OcpMessagingService.OpenMessageStreamRequest.newBuilder()
        .setRendezvousKey(rendezvous.asRendezvousKey())
        .apply { setSignature(sign(rendezvous)) }
        .build()
}

internal fun clientPongWith(timestampInMillis: Long): Model.ClientPong {
    return Model.ClientPong.newBuilder()
        .setTimestamp(
            timestampInMillis.asProtobufTimestamp()
        ).build()
}

internal fun Long.asProtobufTimestamp(): Timestamp =
    Timestamp.newBuilder().setSeconds(this / 1_000).build()

internal fun ID.asMessageId(): OcpMessagingService.MessageId {
    return OcpMessagingService.MessageId.newBuilder().setValue(toByteString()).build()
}

internal fun ID.asIntentId(): Model.IntentId {
    return Model.IntentId.newBuilder().setValue(toByteString()).build()
}

internal fun ID.asSwapId(): Model.SwapId {
    return Model.SwapId.newBuilder().setValue(toByteString()).build()
}

internal fun TransactionMetadata.asProtobufMetadata(): OcpTransactionService.Metadata {
    val builder = OcpTransactionService.Metadata.newBuilder()

    when (this) {
        is TransactionMetadata.OpenAccount -> {
            builder.setOpenAccounts(
                OcpTransactionService.OpenAccountsMetadata.newBuilder()
                    .setMint(mint.asSolanaAccountId())
                    .setAccountSet(
                        when (type) {
                            AccountType.Pool -> AccountSet.POOL
                            AccountType.Primary -> AccountSet.USER
                            else -> AccountSet.UNRECOGNIZED
                        }
                    )
                    .build()
            )
        }

        is TransactionMetadata.ReceivePublicPayment -> {
            builder.setReceivePaymentsPublicly(
                OcpTransactionService.ReceivePaymentsPubliclyMetadata.newBuilder()
                    .setSource(source.asSolanaAccountId())
                    .setMint(mint.asSolanaAccountId())
                    .setQuarks(quarks)
                    .setIsIndirectSend(isIndirect)
                    // exchange data cannot be set on incoming transactions
//                    .setExchangeData(exchangeData.asProtobufExchangeData())
                    .build()
            )
        }

        is TransactionMetadata.SendPublicPayment -> {
            builder.setSendPublicPayment(
                OcpTransactionService.SendPublicPaymentMetadata.newBuilder()
                    .setSource(source.asSolanaAccountId())
                    .setMint(exchangeData.mint.asSolanaAccountId())
                    .apply {
                        if (verifiedExchangeData != null) {
                            setClientExchangeData(verifiedExchangeData.asProtobufExchangeData())
                        }
                    }
                    .setDestination(destination.asSolanaAccountId())
                    .apply {
                        if (this@asProtobufMetadata.destinationOwner != null) {
                            setDestinationOwner(this@asProtobufMetadata.destinationOwner.asSolanaAccountId())
                        }
                    }
                    .setIsIndirectSend(isIndirect)
                    .setIsWithdrawal(isWithdrawal)
                    .build()
            )
            if (appMetadata != null) {
                builder.setAppMetadata(
                    OcpTransactionService.AppMetadata.newBuilder()
                        .setValue(appMetadata.toByteString())
                )
            }
        }

        is TransactionMetadata.PublicDistribution -> {
            builder.setPublicDistribution(
                OcpTransactionService.PublicDistributionMetadata.newBuilder()
                    .setSource(source.asSolanaAccountId())
                    .setMint(mint.asSolanaAccountId())
                    .apply {
                        distributions.forEachIndexed { index, distribution ->
                            addDistributions(
                                index,
                                OcpTransactionService.PublicDistributionMetadata.Distribution
                                    .newBuilder()
                                    .setQuarks(distribution.amount.quarks)
                                    .setDestination(distribution.destination.asSolanaAccountId())
                            )
                        }
                    }.build()
            )
        }

        TransactionMetadata.Unknown -> Unit
    }

    return builder.build()
}

internal fun ExchangeData.WithRate.asProtobufExchangeData(): OcpTransactionService.ExchangeData {
    return OcpTransactionService.ExchangeData.newBuilder()
        .setCurrency(currencyCode.lowercase()) // ensure always lowercase
        .setExchangeRate(exchangeRate)
        .setNativeAmount(nativeAmount)
        .setQuarks(quarks)
        .setMint(mint.asSolanaAccountId())
        .build()
}

internal fun ExchangeData.Verified.asProtobufExchangeData(): OcpTransactionService.VerifiedExchangeData {
    return OcpTransactionService.VerifiedExchangeData.newBuilder()
        .setMint(mint.asSolanaAccountId())
        .setQuarks(quarks)
        .setNativeAmount(nativeAmount)
        .setCoreMintFiatExchangeRate(verifiedState.rateProto)
        .apply {
            if (verifiedState.reserveProto != null) {
                setLaunchpadCurrencyReserveState(verifiedState.reserveProto)
            }
        }.build()
}

internal fun ExchangeData.WithoutRate.asProtobufExchangeData(): OcpTransactionService.ExchangeDataWithoutRate {
    return OcpTransactionService.ExchangeDataWithoutRate.newBuilder()
        .setCurrency(currencyCode.lowercase()) // ensure always lowercase
        .setNativeAmount(nativeAmount)
        .build()
}

internal fun TransferRequest.asProtobufMessage(): OcpMessagingService.Message {
    return when (this) {
        is GiveRequest -> OcpMessagingService.Message
            .newBuilder()
            .setRequestToGiveBill(
                OcpMessagingService.RequestToGiveBill
                    .newBuilder()
                    .setMint(mint.asSolanaAccountId())
                    .setExchangeData(exchangeData.asProtobufExchangeData())
            ).build()

        is GrabRequest -> OcpMessagingService.Message
            .newBuilder()
            .setRequestToGrabBill(
                OcpMessagingService.RequestToGrabBill
                    .newBuilder()
                    .setRequestorAccount(account.asSolanaAccountId())
            ).build()
    }
}

internal fun Message.asProtobufMessage(): OcpMessagingService.Message {
    val builder = OcpMessagingService.Message.newBuilder()
        .setId(id.asMessageId())

    when (kind) {
        is MessageKind.RequestToGrabBill -> {
            builder.requestToGrabBill = requestToGrabBill {
                requestorAccount = kind.requestor.asSolanaAccountId()
            }
        }

        is MessageKind.RequestToGiveBill -> {
            builder.requestToGiveBill = requestToGiveBill {
                mint = kind.mint.asSolanaAccountId()
            }
        }

        MessageKind.Unknown -> Unit
    }

    return builder.build()
}

internal fun LocalFiat.asExchangeData(): OcpTransactionService.ExchangeData {
    return OcpTransactionService.ExchangeData.newBuilder()
        .setQuarks(underlyingTokenAmount.quarks)
        .setCurrency(rate.currency.name.lowercase())
        .setExchangeRate(rate.fx)
        .setNativeAmount(nativeAmount.decimalValue)
        .build()
}

internal fun StatefulSwapRequest.currencyCreatorParams(): OcpTransactionService.StatefulSwapRequest.Initiate.ReserveSwapClientParameters.Builder {
    return when (val details = kind) {
        is SwapStartKind.Reserve -> {
            OcpTransactionService.StatefulSwapRequest.Initiate.ReserveSwapClientParameters.newBuilder()
                .setId(swapId.asSwapId())
                .setFromMint(details.fromMint.asSolanaAccountId())
                .setToMint(details.toMint.asSolanaAccountId())
                .setSwapAmount(this@currencyCreatorParams.swapAmount.underlyingTokenAmount.quarks)
                .setFeeAmount(this@currencyCreatorParams.feeAmount?.underlyingTokenAmount?.quarks ?: 0)
                .apply {
                    // Required by the server when creating a new reserve currency from a non-core
                    // source mint (treasury-funded flow); omitted otherwise.
                    val fullAmountExchangeData = this@currencyCreatorParams.fullAmountExchangeData
                    if (fullAmountExchangeData != null) {
                        setFullAmountExchangeData(fullAmountExchangeData.asProtobufExchangeData())
                    }
                }
                .apply {
                    when (val source = details.fundingSource) {
                        is SwapFundingSource.ExternalWallet -> {
                            setFundingSource(OcpTransactionService.FundingSource.FUNDING_SOURCE_EXTERNAL_WALLET)
                            setFundingId(source.transactionSignature.base58)
                        }

                        is SwapFundingSource.SubmitIntent -> {
                            setFundingSource(OcpTransactionService.FundingSource.FUNDING_SOURCE_SUBMIT_INTENT)
                            setFundingId(source.id.base58)
                        }

                        is SwapFundingSource.CoinbaseOnramp -> {
                            setFundingSource(OcpTransactionService.FundingSource.FUNDING_SOURCE_COINBASE_ONRAMP)
                            setFundingId(source.orderId)
                        }

                        SwapFundingSource.Unknown -> Unit
                    }
                }
        }

        is SwapStartKind.Stablecoin -> {
            throw IllegalStateException("Stablecoin should not be used for currency creator params")
        }
    }
}

internal fun StatefulSwapRequest.stablecoinParams(): OcpTransactionService.StatefulSwapRequest.Initiate.CoinbaseStableSwapperClientParameters.Builder {
    return when (val details = kind) {
        is SwapStartKind.Reserve -> {
            throw IllegalStateException("Reserve should not be used for stable swapper params")
        }

        is SwapStartKind.Stablecoin -> {
            OcpTransactionService.StatefulSwapRequest.Initiate.CoinbaseStableSwapperClientParameters.newBuilder()
                .setId(swapId.asSwapId())
                .setFromMint(details.fromMint.asSolanaAccountId())
                .setToMint(details.toMint.asSolanaAccountId())
                .setSwapAmount(this@stablecoinParams.swapAmount.underlyingTokenAmount.quarks)
                .setDestinationOwner(this@stablecoinParams.kind.destinationOwner.asSolanaAccountId())
                .setFeeAmount(this@stablecoinParams.feeAmount?.underlyingTokenAmount?.quarks ?: 0)
                .setFundingSource(OcpTransactionService.FundingSource.FUNDING_SOURCE_SUBMIT_INTENT)
                .setFundingId(details.fundingSource.id.base58)
        }
    }
}

internal fun StatefulSwapRequest.verifiedMetadata(): OcpTransactionService.VerifiedSwapMetadata.Builder {
    return OcpTransactionService.VerifiedSwapMetadata.newBuilder()
        .apply {
            when (kind) {
                is SwapStartKind.Reserve -> setReserve(
                    OcpTransactionService.VerifiedReserveSwapMetadata.newBuilder()
                        .setClientParameters(currencyCreatorParams())
                )
                is SwapStartKind.Stablecoin -> setStablecoin(
                    OcpTransactionService.VerifiedCoinbaseStableSwapperSwapMetadata.newBuilder()
                        .setClientParameters(stablecoinParams())
                )
            }
        }
}

internal fun TokenBillCustomizations.asProto(): OcpCurrencyService.BillCustomization {
    return OcpCurrencyService.BillCustomization.newBuilder()
        .apply {
            when (background) {
                is BillBackground.Gradient -> addAllColors(
                    background.colors.map { color ->
                        OcpCurrencyService.Color.newBuilder().setHex(color).build()
                    }
                )

                is BillBackground.Solid -> addColors(
                    OcpCurrencyService.Color.newBuilder().setHex(background.colorHex)
                )
            }
        }.build()
}

internal fun SocialLink.asProto(): OcpCurrencyService.SocialLink {
    return OcpCurrencyService.SocialLink.newBuilder()
        .apply {
            when (this@asProto) {
                is SocialLink.Discord -> setDiscord(OcpCurrencyService.SocialLink.Discord.newBuilder().setInviteCode(inviteCode))
                is SocialLink.Telegram -> setTelegram(OcpCurrencyService.SocialLink.Telegram.newBuilder().setUsername(username))
                is SocialLink.Website -> setWebsite(OcpCurrencyService.SocialLink.Website.newBuilder().setUrl(url))
                is SocialLink.X -> setX(OcpCurrencyService.SocialLink.X.newBuilder().setUsername(username))
            }
        }.build()
}

internal fun ModerationAttestation.asProto(): OcpCurrencyService.ModerationAttestation {
    return OcpCurrencyService.ModerationAttestation.newBuilder()
        .setRawValue(attestation.toByteString())
        .build()
}