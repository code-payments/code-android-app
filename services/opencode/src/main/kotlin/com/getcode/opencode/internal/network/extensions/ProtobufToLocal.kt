package com.getcode.opencode.internal.network.extensions

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.messaging.v1.MessagingService
import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.messaging.MessageKind
import com.getcode.opencode.model.transactions.ExchangeData
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
            exchangeData = receivePaymentsPublicly.exchangeData.toModel()
        )

        TransactionService.Metadata.TypeCase.TYPE_NOT_SET -> TransactionMetadata.Unknown
        else -> TransactionMetadata.Unknown
    }
}