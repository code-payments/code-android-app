package com.getcode.opencode.internal.network.extensions

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.codeinc.opencode.gen.messaging.v1.MessagingService
import com.codeinc.opencode.gen.messaging.v1.requestToGiveBill
import com.codeinc.opencode.gen.messaging.v1.requestToGrabBill
import com.codeinc.opencode.gen.transaction.v1.TransactionService
import com.codeinc.opencode.gen.transaction.v1.TransactionService.OpenAccountsMetadata.AccountSet
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

internal fun PublicKey.asMessageId(): MessagingService.MessageId {
    return MessagingService.MessageId.newBuilder().setValue(this.byteArray.toByteString()).build()
}

internal fun PublicKey.asIntentId(): Model.IntentId {
    return Model.IntentId.newBuilder().setValue(this.byteArray.toByteString()).build()
}

internal fun SwapId.asSwapId(): Model.SwapId {
    return Model.SwapId.newBuilder().setValue(this.publicKey.bytes.toByteString()).build()
}

internal fun PublicKey.asRendezvousKey(): MessagingService.RendezvousKey {
    return MessagingService.RendezvousKey.newBuilder().setValue(this.bytes.toByteString())
        .build()
}

internal fun KeyPair.asRendezvousKey(): MessagingService.RendezvousKey {
    return MessagingService.RendezvousKey.newBuilder().setValue(
        ByteString.copyFrom(publicKeyBytes)
    ).build()
}

internal fun openMessageStreamRequest(rendezvous: KeyPair): MessagingService.OpenMessageStreamRequest {
    return MessagingService.OpenMessageStreamRequest.newBuilder()
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

internal fun ID.asMessageId(): MessagingService.MessageId {
    return MessagingService.MessageId.newBuilder().setValue(toByteString()).build()
}

internal fun ID.asIntentId(): Model.IntentId {
    return Model.IntentId.newBuilder().setValue(toByteString()).build()
}

internal fun ID.asSwapId(): Model.SwapId {
    return Model.SwapId.newBuilder().setValue(toByteString()).build()
}

internal fun TransactionMetadata.asProtobufMetadata(): TransactionService.Metadata {
    val builder = TransactionService.Metadata.newBuilder()

    when (this) {
        is TransactionMetadata.OpenAccount -> {
            builder.setOpenAccounts(
                TransactionService.OpenAccountsMetadata.newBuilder()
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
                TransactionService.ReceivePaymentsPubliclyMetadata.newBuilder()
                    .setSource(source.asSolanaAccountId())
                    .setMint(mint.asSolanaAccountId())
                    .setQuarks(quarks)
                    .setIsRemoteSend(isRemoteSend)
                    // exchange data cannot be set on incoming transactions
//                    .setExchangeData(exchangeData.asProtobufExchangeData())
                    .build()
            )
        }

        is TransactionMetadata.SendPublicPayment -> {
            builder.setSendPublicPayment(
                TransactionService.SendPublicPaymentMetadata.newBuilder()
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
                    .setIsRemoteSend(isRemoteSend)
                    .setIsWithdrawal(isWithdrawal)
                    .build()
            )
        }

        is TransactionMetadata.PublicDistribution -> {
            builder.setPublicDistribution(
                TransactionService.PublicDistributionMetadata.newBuilder()
                    .setSource(source.asSolanaAccountId())
                    .setMint(mint.asSolanaAccountId())
                    .apply {
                        distributions.forEachIndexed { index, distribution ->
                            addDistributions(
                                index,
                                TransactionService.PublicDistributionMetadata.Distribution
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

internal fun ExchangeData.WithRate.asProtobufExchangeData(): TransactionService.ExchangeData {
    return TransactionService.ExchangeData.newBuilder()
        .setCurrency(currencyCode.lowercase()) // ensure always lowercase
        .setExchangeRate(exchangeRate)
        .setNativeAmount(nativeAmount)
        .setQuarks(quarks)
        .setMint(mint.asSolanaAccountId())
        .build()
}

internal fun ExchangeData.Verified.asProtobufExchangeData(): TransactionService.VerifiedExchangeData {
    return TransactionService.VerifiedExchangeData.newBuilder()
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

internal fun ExchangeData.WithoutRate.asProtobufExchangeData(): TransactionService.ExchangeDataWithoutRate {
    return TransactionService.ExchangeDataWithoutRate.newBuilder()
        .setCurrency(currencyCode.lowercase()) // ensure always lowercase
        .setNativeAmount(nativeAmount)
        .build()
}

internal fun TransferRequest.asProtobufMessage(): MessagingService.Message {
    return when (this) {
        is GiveRequest -> MessagingService.Message
            .newBuilder()
            .setRequestToGiveBill(
                MessagingService.RequestToGiveBill
                    .newBuilder()
                    .setMint(mint.asSolanaAccountId())
                    .setExchangeData(exchangeData.asProtobufExchangeData())
            ).build()

        is GrabRequest -> MessagingService.Message
            .newBuilder()
            .setRequestToGrabBill(
                MessagingService.RequestToGrabBill
                    .newBuilder()
                    .setRequestorAccount(account.asSolanaAccountId())
            ).build()
    }
}

internal fun Message.asProtobufMessage(): MessagingService.Message {
    val builder = MessagingService.Message.newBuilder()
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

internal fun LocalFiat.asExchangeData(): TransactionService.ExchangeData {
    return TransactionService.ExchangeData.newBuilder()
        .setQuarks(underlyingTokenAmount.quarks)
        .setCurrency(rate.currency.name.lowercase())
        .setExchangeRate(rate.fx)
        .setNativeAmount(nativeAmount.decimalValue)
        .build()
}

internal fun StatefulSwapRequest.currencyCreatorParams(): TransactionService.StatefulSwapRequest.Initiate.ReserveSwapClientParameters.Builder {
    return when (val details = kind) {
        is SwapStartKind.Reserve -> {
            TransactionService.StatefulSwapRequest.Initiate.ReserveSwapClientParameters.newBuilder()
                .setId(swapId.asSwapId())
                .setFromMint(details.fromMint.asSolanaAccountId())
                .setToMint(details.toMint.asSolanaAccountId())
                .setSwapAmount(this@currencyCreatorParams.swapAmount.underlyingTokenAmount.quarks)
                .setFeeAmount(this@currencyCreatorParams.feeAmount?.underlyingTokenAmount?.quarks ?: 0)
                .apply {
                    when (val source = details.fundingSource) {
                        is SwapFundingSource.ExternalWallet -> {
                            setFundingSource(TransactionService.FundingSource.FUNDING_SOURCE_EXTERNAL_WALLET)
                            setFundingId(source.transactionSignature.base58)
                        }

                        is SwapFundingSource.SubmitIntent -> {
                            setFundingSource(TransactionService.FundingSource.FUNDING_SOURCE_SUBMIT_INTENT)
                            setFundingId(source.id.base58)
                        }

                        is SwapFundingSource.CoinbaseOnramp -> {
                            setFundingSource(TransactionService.FundingSource.FUNDING_SOURCE_COINBASE_ONRAMP)
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

internal fun StatefulSwapRequest.stablecoinParams(): TransactionService.StatefulSwapRequest.Initiate.CoinbaseStableSwapperClientParameters.Builder {
    return when (val details = kind) {
        is SwapStartKind.Reserve -> {
            throw IllegalStateException("Reserve should not be used for stable swapper params")
        }

        is SwapStartKind.Stablecoin -> {
            TransactionService.StatefulSwapRequest.Initiate.CoinbaseStableSwapperClientParameters.newBuilder()
                .setId(swapId.asSwapId())
                .setFromMint(details.fromMint.asSolanaAccountId())
                .setToMint(details.toMint.asSolanaAccountId())
                .setSwapAmount(this@stablecoinParams.swapAmount.underlyingTokenAmount.quarks)
                .setDestinationOwner(this@stablecoinParams.kind.destinationOwner.asSolanaAccountId())
                .setFeeAmount(this@stablecoinParams.feeAmount?.underlyingTokenAmount?.quarks ?: 0)
                .setFundingSource(TransactionService.FundingSource.FUNDING_SOURCE_SUBMIT_INTENT)
                .setFundingId(details.fundingSource.id.base58)
        }
    }
}

internal fun StatefulSwapRequest.verifiedMetadata(): TransactionService.VerifiedSwapMetadata.Builder {
    return TransactionService.VerifiedSwapMetadata.newBuilder()
        .apply {
            when (kind) {
                is SwapStartKind.Reserve -> setReserve(
                    TransactionService.VerifiedReserveSwapMetadata.newBuilder()
                        .setClientParameters(currencyCreatorParams())
                )
                is SwapStartKind.Stablecoin -> setStablecoin(
                    TransactionService.VerifiedCoinbaseStableSwapperSwapMetadata.newBuilder()
                        .setClientParameters(stablecoinParams())
                )
            }
        }
}

internal fun TokenBillCustomizations.asProto(): CurrencyService.BillCustomization {
    return CurrencyService.BillCustomization.newBuilder()
        .apply {
            when (background) {
                is BillBackground.Gradient -> addAllColors(
                    background.colors.map { color ->
                        CurrencyService.Color.newBuilder().setHex(color).build()
                    }
                )

                is BillBackground.Solid -> addColors(
                    CurrencyService.Color.newBuilder().setHex(background.colorHex)
                )
            }
        }.build()
}

internal fun SocialLink.asProto(): CurrencyService.SocialLink {
    return CurrencyService.SocialLink.newBuilder()
        .apply {
            when (this@asProto) {
                is SocialLink.Discord -> setDiscord(CurrencyService.SocialLink.Discord.newBuilder().setInviteCode(inviteCode))
                is SocialLink.Telegram -> setTelegram(CurrencyService.SocialLink.Telegram.newBuilder().setUsername(username))
                is SocialLink.Website -> setWebsite(CurrencyService.SocialLink.Website.newBuilder().setUrl(url))
                is SocialLink.X -> setX(CurrencyService.SocialLink.X.newBuilder().setUsername(username))
            }
        }.build()
}

internal fun ModerationAttestation.asProto(): CurrencyService.ModerationAttestation {
    return CurrencyService.ModerationAttestation.newBuilder()
        .setRawValue(attestation.toByteString())
        .build()
}