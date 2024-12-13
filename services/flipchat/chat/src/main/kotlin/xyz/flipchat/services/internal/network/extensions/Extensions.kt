package xyz.flipchat.services.internal.network.extensions

import com.codeinc.flipchat.gen.common.v1.Common
import com.codeinc.flipchat.gen.messaging.v1.Model
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.toByteString
import xyz.flipchat.services.domain.model.query.PagingToken
import xyz.flipchat.services.domain.model.query.QueryOptions

internal fun ByteArray.toSignature(): Common.Signature {
    return Common.Signature.newBuilder().setValue(this.toByteString())
        .build()
}

internal fun KeyPair.asPublicKey(): Common.PublicKey {
    return Common.PublicKey.newBuilder().setValue(this.publicKeyBytes.toByteString()).build()
}

internal fun ID.toUserId(): Common.UserId {
    return Common.UserId.newBuilder().setValue(toByteString()).build()
}

internal fun ID.toMessageId(): Model.MessageId {
    return Model.MessageId.newBuilder().setValue(toByteString()).build()
}

internal fun ID.toChatId(): Common.ChatId {
    return Common.ChatId.newBuilder().setValue(toByteString()).build()
}

internal fun ID.toIntentId(): Common.IntentId {
    return Common.IntentId.newBuilder().setValue(toByteString()).build()
}

internal fun Common.PublicKey.toPublicKey(): PublicKey {
    return PublicKey(this.value.toByteArray().toList())
}

internal fun KinAmount.toPaymentAmount(): Common.PaymentAmount {
    return Common.PaymentAmount.newBuilder().setQuarks(this.kin.quarks).build()
}

internal fun QueryOptions.toProto(): Common.QueryOptions {
   return Common.QueryOptions.newBuilder()
        .setPageSize(this@toProto.limit.toLong())
        .setOrder(
            if (this@toProto.descending) Common.QueryOptions.Order.DESC
            else Common.QueryOptions.Order.ASC
        ).apply {
            this@toProto.token?.let {
                setPagingToken(it.toPagingToken())
            }
        }.build()
}

internal fun PagingToken.toPagingToken(): Common.PagingToken {
    return Common.PagingToken.newBuilder().setValue(this.toByteString()).build()
}