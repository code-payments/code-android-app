package com.flipcash.services.models

import com.codeinc.flipcash.gen.push.v1.Model
import com.flipcash.services.internal.network.extensions.asPayload
import com.getcode.solana.keys.Mint
import com.getcode.utils.decodeBase64

data class NotificationPayload(
    val navigation: NavigationTrigger?
) {
    companion object {
        fun fromEncoded(encoded: String): NotificationPayload? {
            val proto = runCatching { Model.Payload.parseFrom(encoded.decodeBase64()) }.getOrNull()
                ?: return null
            return fromProto(proto)
        }

        fun fromProto(proto: Model.Payload): NotificationPayload {
            return proto.asPayload()
        }
    }
}

sealed interface NavigationTrigger {
    data class CurrencyInfo(val mint: Mint) : NavigationTrigger
}