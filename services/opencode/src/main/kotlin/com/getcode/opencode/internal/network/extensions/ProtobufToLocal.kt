package com.getcode.opencode.internal.network.extensions

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.messaging.v1.MessagingService
import com.codeinc.opencode.gen.messaging.v1.domainOrNull
import com.codeinc.opencode.gen.messaging.v1.metadataOrNull
import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519.createKeyPair
import com.getcode.model.Domain
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.messaging.MessageKind
import com.getcode.opencode.model.transactions.AirdropType
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.model.transactions.Fee
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.solana.keys.PublicKey

internal fun Model.IntentId.toId(): ID = value.toByteArray().toList()
internal fun Model.SolanaAccountId.toPublicKey(): PublicKey = value.toByteArray().toPublicKey()
internal fun Model.Signature.toPublicKey(): PublicKey = value.toByteArray().toPublicKey()
internal fun MessagingService.MessageId.toPublicKey(): PublicKey = value.toByteArray().toPublicKey()
internal fun MessagingService.RendezvousKey.toPublicKey(): PublicKey =
    value.toByteArray().toPublicKey()

internal fun TransactionService.ExchangeData.toModel(): ExchangeData.WithRate {
    return ExchangeData.WithRate(
        currencyCode = this.currency,
        exchangeRate = this.exchangeRate,
        nativeAmount = this.nativeAmount,
        quarks = this.quarks
    )
}

internal fun TransactionService.ExchangeDataWithoutRate.toModel(): ExchangeData.WithoutRate {
    return ExchangeData.WithoutRate(
        currencyCode = this.currency,
        nativeAmount = this.nativeAmount,
    )
}

internal fun MessagingService.RequestToGrabBill.toMessageKind(): MessageKind.RequestToGrabBill {
    return MessageKind.RequestToGrabBill(
        requestor = requestorAccount.toPublicKey()
    )
}

internal fun MessagingService.RequestToReceiveBill.toMessageKind(): MessageKind.RequestToReceiveBill {
    return MessageKind.RequestToReceiveBill(
        requestor = requestorAccount.toPublicKey(),
        exchangeData = when (exchangeDataCase) {
            MessagingService.RequestToReceiveBill.ExchangeDataCase.EXACT -> exact.toModel()
            MessagingService.RequestToReceiveBill.ExchangeDataCase.PARTIAL -> partial.toModel()
            MessagingService.RequestToReceiveBill.ExchangeDataCase.EXCHANGEDATA_NOT_SET -> ExchangeData.Unset
            else -> ExchangeData.Unset
        },
        domainVerification = Domain.from(domainOrNull?.value)?.let { domain ->
            MessageKind.RequestToReceiveBill.DomainVerification(
                domain = domain,
                verifier = verifier.toPublicKey(),
                signature = createKeyPair(signature.toByteArray()),
                rendezvous = rendezvousKey.toPublicKey()
            )
        },
        additionalFees = additionalFeesList.map { proto ->
            Fee(
                bps = proto.feeBps,
                destination = proto.destination.toPublicKey()
            )
        }
    )
}

internal fun MessagingService.CodeScanned.toMessageKind(): MessageKind.CodeScanned {
    return MessageKind.CodeScanned(
        timestamp = timestamp.seconds * 1_000
    )
}

internal fun MessagingService.ClientRejectedPayment.toMessageKind(): MessageKind.ClientRejectedPayment {
    return MessageKind.ClientRejectedPayment(
        intentId = intentId.toId()
    )
}

internal fun MessagingService.IntentSubmitted.toMessageKind(): MessageKind.IntentSubmitted {
    return MessageKind.IntentSubmitted(
        intentId = intentId.toId(),
        metadata = metadataOrNull?.toMetadata().takeIf { it !is TransactionMetadata.Unknown }
    )
}

internal fun TransactionService.Metadata.toMetadata(): TransactionMetadata {
    return when (typeCase) {
        TransactionService.Metadata.TypeCase.OPEN_ACCOUNTS -> TransactionMetadata.OpenAccounts
        TransactionService.Metadata.TypeCase.SEND_PUBLIC_PAYMENT -> TransactionMetadata.SendPublicPayment(
            source = sendPublicPayment.source.toPublicKey(),
            destination = sendPublicPayment.destination.toPublicKey(),
            exchangeData = sendPublicPayment.exchangeData.toModel(),
            isWithdrawal = sendPublicPayment.isWithdrawal
        )

        TransactionService.Metadata.TypeCase.RECEIVE_PAYMENTS_PUBLICLY -> TransactionMetadata.ReceivePublicPayment(
            source = receivePaymentsPublicly.source.toPublicKey(),
            quarks = receivePaymentsPublicly.quarks,
            isRemoteSend = receivePaymentsPublicly.isRemoteSend,
            isIssuerVoidingGiftCard = receivePaymentsPublicly.isIssuerVoidingGiftCard,
            exchangeData = receivePaymentsPublicly.exchangeData.toModel()
        )

        TransactionService.Metadata.TypeCase.TYPE_NOT_SET -> TransactionMetadata.Unknown
        else -> TransactionMetadata.Unknown
    }
}

internal fun MessagingService.WebhookCalled.toMessageKind(): MessageKind.WebhookCalled {
    return MessageKind.WebhookCalled(
        timestamp = timestamp.seconds * 1_000
    )
}