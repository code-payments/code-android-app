package com.getcode.opencode.internal.network.extensions

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.common.v1.domain
import com.codeinc.opencode.gen.messaging.v1.MessagingService
import com.codeinc.opencode.gen.messaging.v1.airdropReceived
import com.codeinc.opencode.gen.messaging.v1.clientRejectedPayment
import com.codeinc.opencode.gen.messaging.v1.codeScanned
import com.codeinc.opencode.gen.messaging.v1.intentSubmitted
import com.codeinc.opencode.gen.messaging.v1.requestToGrabBill
import com.codeinc.opencode.gen.messaging.v1.requestToReceiveBill
import com.codeinc.opencode.gen.messaging.v1.webhookCalled
import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.messaging.Message
import com.getcode.opencode.model.messaging.MessageKind
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.toByteString
import com.google.protobuf.Timestamp

internal fun ByteArray.asSignature(): Model.Signature {
    return Model.Signature.newBuilder().setValue(this.toByteString())
        .build()
}

internal fun KeyPair.asSignature(): Model.Signature {
    return Model.Signature.newBuilder().setValue(this.publicKeyBytes.toByteString())
        .build()
}

internal fun KeyPair.asSolanaAccountId(): Model.SolanaAccountId {
    return Model.SolanaAccountId.newBuilder().setValue(this.publicKeyBytes.toByteString()).build()
}

internal fun PublicKey.asSolanaAccountId(): Model.SolanaAccountId {
    return Model.SolanaAccountId.newBuilder().setValue(this.byteArray.toByteString()).build()
}

internal fun PublicKey.asIntentId(): Model.IntentId {
    return Model.IntentId.newBuilder().setValue(this.byteArray.toByteString()).build()
}

internal fun PublicKey.asRendezvousKey(): MessagingService.RendezvousKey {
    return MessagingService.RendezvousKey.newBuilder().setValue(this.bytes.toByteString())
        .build()
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

internal fun TransactionMetadata.asProtobufMetadata(): TransactionService.Metadata {
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
                    .setExchangeData(exchangeData.asProtobufExchangeData())
                    .build()
            )
        }
        is TransactionMetadata.SendPublicPayment -> {

        }
        TransactionMetadata.Unknown -> Unit
    }

    return builder.build()
}

internal fun ExchangeData.WithRate.asProtobufExchangeData(): TransactionService.ExchangeData {
    return TransactionService.ExchangeData.newBuilder()
        .setCurrency(currencyCode)
        .setExchangeRate(exchangeRate)
        .setNativeAmount(nativeAmount)
        .setQuarks(quarks)
        .build()
}

internal fun ExchangeData.WithoutRate.asProtobufExchangeData(): TransactionService.ExchangeDataWithoutRate {
    return TransactionService.ExchangeDataWithoutRate.newBuilder()
        .setCurrency(currencyCode)
        .setNativeAmount(nativeAmount)
        .build()
}

internal fun Message.asProtobufMessage(): MessagingService.Message {
    val builder = MessagingService.Message.newBuilder()
        .setId(id.asMessageId())

    when (kind) {
        is MessageKind.AirdropReceived -> {
            builder.airdropReceived = airdropReceived {
                airdropType = TransactionService.AirdropType.forNumber(kind.type.ordinal)
                exchangeData = kind.exchangeData.asProtobufExchangeData()
                timestamp = kind.timestamp.asProtobufTimestamp()
            }
        }

        is MessageKind.ClientRejectedPayment -> {
            builder.clientRejectedPayment = clientRejectedPayment {
                intentId = kind.intentId.asIntentId()
            }
        }

        is MessageKind.CodeScanned -> {
            builder.codeScanned = codeScanned {
                timestamp = kind.timestamp.asProtobufTimestamp()
            }
        }

        is MessageKind.IntentSubmitted -> {
            builder.intentSubmitted = intentSubmitted {
                intentId = kind.intentId.asIntentId()
                if (kind.metadata != null) {
                    metadata = kind.metadata.asProtobufMetadata()
                }
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
                        exact = exchange.asProtobufExchangeData()
                    }
                    is ExchangeData.WithoutRate -> {
                        partial = exchange.asProtobufExchangeData()
                    }

                    ExchangeData.Unset -> Unit
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
                timestamp = kind.timestamp.asProtobufTimestamp()
            }
        }

        MessageKind.Unknown -> Unit
    }

    return builder.build()
}