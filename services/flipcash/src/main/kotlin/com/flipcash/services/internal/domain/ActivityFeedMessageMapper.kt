package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.activity.v1.Model
import com.codeinc.flipcash.gen.activity.v1.paymentAmountOrNull
import com.flipcash.services.internal.extensions.toPublicKey
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.models.ActivityFeedNotification
import com.flipcash.services.models.NotificationMetadata
import com.flipcash.services.models.NotificationState
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import kotlinx.datetime.Instant
import javax.inject.Inject

internal class ActivityFeedMessageMapper @Inject constructor(
) : Mapper<Model.Notification, ActivityFeedNotification> {
    override fun map(from: Model.Notification): ActivityFeedNotification {
        return ActivityFeedNotification(
            id = from.id.toId(),
            text = from.localizedText,
            amount = from.paymentAmountOrNull?.let {
                LocalFiat(
                    usdc = Fiat(quarks = it.quarks.toULong()),
                    converted = Fiat(fiat = it.nativeAmount, currencyCode = CurrencyCode.tryValueOf(it.currency) ?: CurrencyCode.USD),
                    rate = Rate.ignore
                )
            },
            timestamp = Instant.fromEpochSeconds(from.ts.seconds, 0),
            state = when (from.state) {
                Model.NotificationState.NOTIFICATION_STATE_PENDING -> NotificationState.PENDING
                Model.NotificationState.NOTIFICATION_STATE_COMPLETED -> NotificationState.COMPLETED
                Model.NotificationState.NOTIFICATION_STATE_UNKNOWN,
                Model.NotificationState.UNRECOGNIZED,
                null -> NotificationState.UNKNOWN
            },
            metadata = when (from.additionalMetadataCase) {
                Model.Notification.AdditionalMetadataCase.WELCOME_BONUS -> NotificationMetadata.WelcomeBonus
                Model.Notification.AdditionalMetadataCase.GAVE_USDC -> NotificationMetadata.GaveUsdc
                Model.Notification.AdditionalMetadataCase.RECEIVED_USDC -> NotificationMetadata.ReceivedUsdc
                Model.Notification.AdditionalMetadataCase.WITHDREW_USDC -> NotificationMetadata.WithdrewUsdc
                Model.Notification.AdditionalMetadataCase.SENT_USDC -> NotificationMetadata.SentUsdc(
                    creator = from.sentUsdc.vault.value.toByteArray().toPublicKey(),
                    canCancel = from.sentUsdc.canInitiateCancelAction
                )
                Model.Notification.AdditionalMetadataCase.DEPOSITED_USDC -> NotificationMetadata.DepositedUsdc
                Model.Notification.AdditionalMetadataCase.ADDITIONALMETADATA_NOT_SET,
                null -> NotificationMetadata.Unknown
            }
        )
    }
}