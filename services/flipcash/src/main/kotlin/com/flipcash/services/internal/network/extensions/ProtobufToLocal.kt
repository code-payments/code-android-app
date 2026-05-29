package com.flipcash.services.internal.network.extensions


import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.common.v1.Common.UserId
import com.codeinc.flipcash.gen.moderation.v1.ModerationService
import com.codeinc.flipcash.gen.push.v1.navigationOrNull
import com.codeinc.flipcash.gen.push.v1.Model as PushModels
import com.flipcash.services.internal.extensions.toChecksum
import com.flipcash.services.internal.extensions.toMint
import com.flipcash.services.internal.extensions.toPublicKey
import com.flipcash.services.models.NavigationTrigger
import com.flipcash.services.models.NotificationCategory
import com.flipcash.services.models.NotificationPayload
import com.flipcash.services.models.Substitution
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.Checksum
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import com.codeinc.flipcash.gen.activity.v1.Model as ActivityModels

internal fun ActivityModels.NotificationId.toId(): ID = value.toByteArray().toList()
internal fun Common.UserId.toId(): ID = value.toByteArray().toList()
internal fun Common.Hash.toChecksum(): Checksum = value.toByteArray().toChecksum()
internal fun Common.PublicKey.toPublicKey(): PublicKey = value.toByteArray().toPublicKey()
internal fun Common.PublicKey.toMint(): Mint = value.toByteArray().toMint()

internal fun PushModels.Payload.asPayload(): NotificationPayload {
    val navigationTrigger = navigationOrNull?.typeCase?.let { type ->
        when (type) {
            PushModels.Navigation.TypeCase.CURRENCY_INFO -> {
                val mint = navigation.currencyInfo.toMint()
                NavigationTrigger.CurrencyInfo(mint)
            }
            PushModels.Navigation.TypeCase.TYPE_NOT_SET -> null
        }
    }

    val notificationCategory = when (category) {
        PushModels.Payload.Category.DEPOSIT_WITHDRAWAL -> NotificationCategory.DEPOSIT_WITHDRAWAL
        PushModels.Payload.Category.BUY_SELL -> NotificationCategory.BUY_SELL
        PushModels.Payload.Category.GAIN -> NotificationCategory.GAIN
        PushModels.Payload.Category.CHAT -> NotificationCategory.CHAT
        PushModels.Payload.Category.CONTACT_JOIN -> NotificationCategory.CONTACT_JOIN
        else -> NotificationCategory.DEFAULT
    }

    val titleSubs = titleSubstitutionsList.map { it.asSubstitution() }
    val bodySubs = bodySubstitutionsList.map { it.asSubstitution() }

    return NotificationPayload(
        navigation = navigationTrigger,
        category = notificationCategory,
        groupKey = groupKey,
        titleSubstitutions = titleSubs,
        bodySubstitutions = bodySubs,
    )
}

internal fun PushModels.Substitution.asSubstitution(): Substitution {
    val phoneNumber = when (kindCase) {
        PushModels.Substitution.KindCase.CONTACT -> contact.value
        else -> null
    }
    return Substitution(fallback = fallback, phoneNumber = phoneNumber)
}

internal fun Common.Signature.toSignature(): Signature {
    return Signature(value.toByteArray().toList())
}