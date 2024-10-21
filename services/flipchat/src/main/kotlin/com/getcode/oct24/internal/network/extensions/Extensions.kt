package com.getcode.oct24.internal.network.extensions

import com.codeinc.flipchat.gen.common.v1.Model
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.oct24.internal.network.utils.sign
import com.getcode.utils.toByteString

internal fun ByteArray.toSignature(): Model.Signature {
    return Model.Signature.newBuilder().setValue(this.toByteString())
        .build()
}

internal fun KeyPair.asPublicKey(): Model.PublicKey {
    return Model.PublicKey.newBuilder().setValue(this.publicKeyBytes.toByteString()).build()
}

internal fun ID.toUserId(): Model.UserId {
    return Model.UserId.newBuilder().setValue(toByteString()).build()
}

internal fun ID.toChatId(): Model.ChatId {
    return Model.ChatId.newBuilder().setValue(toByteString()).build()
}

internal fun KeyPair.forAuth(): Model.Auth {
    val pubKey = asPublicKey()
    return Model.Auth.newBuilder()
        .setKeyPair(Model.Auth.KeyPair.newBuilder()
            .setPubKey(pubKey)
            .apply { setSignature(sign(this@forAuth)) }
        ).build()
}