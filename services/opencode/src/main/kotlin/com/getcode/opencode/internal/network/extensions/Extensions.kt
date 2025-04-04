package com.getcode.opencode.internal.network.extensions

import com.codeinc.gen.common.v1.Model
import com.codeinc.gen.common.v1.domain
import com.codeinc.gen.messaging.v1.MessagingService
import com.codeinc.gen.messaging.v1.airdropReceived
import com.codeinc.gen.messaging.v1.clientRejectedPayment
import com.codeinc.gen.messaging.v1.codeScanned
import com.codeinc.gen.messaging.v1.intentSubmitted
import com.codeinc.gen.messaging.v1.requestToGrabBill
import com.codeinc.gen.messaging.v1.requestToReceiveBill
import com.codeinc.gen.messaging.v1.webhookCalled
import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.opencode.domain.messaging.Message
import com.getcode.opencode.domain.messaging.MessageKind
import com.getcode.opencode.domain.transactions.ExchangeData
import com.getcode.opencode.domain.transactions.TransactionMetadata
import com.getcode.opencode.internal.network.utils.sign
import com.getcode.utils.toByteString
import com.google.protobuf.Timestamp

internal fun ByteArray.toSignature(): Model.Signature {
    return Model.Signature.newBuilder().setValue(this.toByteString())
        .build()
}

internal fun KeyPair.asSolanaAccountId(): Model.SolanaAccountId {
    return Model.SolanaAccountId.newBuilder().setValue(this.publicKeyBytes.toByteString()).build()
}

internal fun KeyPair.asRendezvousKey(): MessagingService.RendezvousKey {
    return MessagingService.RendezvousKey.newBuilder().setValue(this.publicKeyBytes.toByteString())
        .build()
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
            timestampInMillis.toProtobufTimestamp()
        ).build()
}

internal fun Long.toProtobufTimestamp(): Timestamp =
    Timestamp.newBuilder().setSeconds(this / 1_000).build()

internal fun ID.toMessageId(): MessagingService.MessageId {
    return MessagingService.MessageId.newBuilder().setValue(toByteString()).build()
}

internal fun ID.toIntentId(): Model.IntentId {
    return Model.IntentId.newBuilder().setValue(toByteString()).build()
}

internal fun TransactionMetadata.toProtobufMetadata(): TransactionService.Metadata {
    val builder = TransactionService.Metadata.newBuilder()

    when (this) {
        TransactionMetadata.OpenAccounts -> {
            builder.setOpenAccounts(
                TransactionService.OpenAccountsMetadata.newBuilder().build()
            )
        }
        is TransactionMetadata.ReceivePublicPayment -> {
            builder.setReceivePaymentsPublicly(
                TransactionService.ReceivePaymentsPubliclyMetadata.newBuilder()
                    .setSource(source.asSolanaAccountId())
                    .setQuarks(quarks)
                    .setIsRemoteSend(isRemoteSend)
                    .setIsIssuerVoidingGiftCard(isIssuerVoidingGiftCard)
                    .setExchangeData(exchangeData.toProtobufExchangeData())
                    .build()
            )
        }
        is TransactionMetadata.SendPublicPayment -> TODO()
    }

    return builder.build()
}

internal fun ExchangeData.WithRate.toProtobufExchangeData(): TransactionService.ExchangeData {
    return TransactionService.ExchangeData.newBuilder()
        .setCurrency(currencyCode)
        .setExchangeRate(exchangeRate)
        .setNativeAmount(nativeAmount)
        .setQuarks(quarks)
        .build()
}

internal fun ExchangeData.WithoutRate.toProtobufExchangeData(): TransactionService.ExchangeDataWithoutRate {
    return TransactionService.ExchangeDataWithoutRate.newBuilder()
        .setCurrency(currencyCode)
        .setNativeAmount(nativeAmount)
        .build()
}

internal fun Message.toProtobufMessage(): MessagingService.Message {
    val builder = MessagingService.Message.newBuilder()
        .setId(id.toMessageId())

    when (kind) {
        is MessageKind.AirdropReceived -> {
            builder.airdropReceived = airdropReceived {
                airdropType = TransactionService.AirdropType.forNumber(kind.type.ordinal)
                exchangeData = kind.exchangeData.toProtobufExchangeData()
                timestamp = kind.timestamp.toProtobufTimestamp()
            }
        }

        is MessageKind.ClientRejectedPayment -> {
            builder.clientRejectedPayment = clientRejectedPayment {
                intentId = kind.intentId.toIntentId()
            }
        }

        is MessageKind.CodeScanned -> {
            builder.codeScanned = codeScanned {
                timestamp = kind.timestamp.toProtobufTimestamp()
            }
        }

        is MessageKind.IntentSubmitted -> {
            builder.intentSubmitted = intentSubmitted {
                intentId = kind.intentId.toIntentId()
                metadata = kind.metadata.toProtobufMetadata()
            }
        }
        is MessageKind.RequestToGrabBill -> {
            builder.requestToGrabBill = requestToGrabBill {
                requestorAccount = kind.requestor.asSolanaAccountId()
            }
        }
        is MessageKind.RequestToReceiveBill -> {
            builder.requestToReceiveBill = requestToReceiveBill {
                requestorAccount = kind.requestor.asSolanaAccountId()
                when (val exchange = kind.exchangeData) {
                    is ExchangeData.WithRate -> {
                        exact = exchange.toProtobufExchangeData()
                    }
                    is ExchangeData.WithoutRate -> {
                        partial = exchange.toProtobufExchangeData()
                    }
                }
                if (kind.domainVerification != null) {
                    domain = domain {
                        value = kind.domainVerification.domain.urlString
                    }
                    verifier = kind.domainVerification.verifier.asSolanaAccountId()
                    rendezvousKey = kind.domainVerification.rendezvous.asRendezvousKey()
                    signature = Model.Signature.newBuilder().sign(kind.domainVerification.signature)
                }
            }
        }
        is MessageKind.WebhookCalled -> {
            builder.webhookCalled = webhookCalled {
                timestamp = kind.timestamp.toProtobufTimestamp()
            }
        }
    }

    return builder.build()
}