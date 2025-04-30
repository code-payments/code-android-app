package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.activity.v1.Model
import com.codeinc.flipcash.gen.activity.v1.paymentAmountOrNull
import com.flipcash.services.internal.extensions.toPublicKey
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.models.ActivityFeedMessage
import com.flipcash.services.models.FeedMessageMetadata
import com.flipcash.services.models.FeedMessageState
import com.getcode.opencode.internal.domain.mapper.Mapper
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import kotlinx.datetime.Instant
import javax.inject.Inject

internal class ActivityFeedMessageMapper @Inject constructor(
) : Mapper<Model.Notification, ActivityFeedMessage> {
    override fun map(from: Model.Notification): ActivityFeedMessage {
        return ActivityFeedMessage(
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
                Model.NotificationState.NOTIFICATION_STATE_PENDING -> FeedMessageState.PENDING
                Model.NotificationState.NOTIFICATION_STATE_COMPLETED -> FeedMessageState.COMPLETED
                Model.NotificationState.NOTIFICATION_STATE_UNKNOWN,
                Model.NotificationState.UNRECOGNIZED,
                null -> FeedMessageState.UNKNOWN
            },
            metadata = when (from.additionalMetadataCase) {
                Model.Notification.AdditionalMetadataCase.WELCOME_BONUS -> FeedMessageMetadata.WelcomeBonus
                Model.Notification.AdditionalMetadataCase.GAVE_USDC -> FeedMessageMetadata.GaveUsdc
                Model.Notification.AdditionalMetadataCase.RECEIVED_USDC -> FeedMessageMetadata.ReceivedUsdc
                Model.Notification.AdditionalMetadataCase.WITHDREW_USDC -> FeedMessageMetadata.WithdrewUsdc
                Model.Notification.AdditionalMetadataCase.SENT_USDC -> FeedMessageMetadata.SentUsdc(
                    creator = from.sentUsdc.vault.value.toByteArray().toPublicKey(),
                    canCancel = from.sentUsdc.canInitiateCancelAction
                )
                Model.Notification.AdditionalMetadataCase.ADDITIONALMETADATA_NOT_SET,
                null -> FeedMessageMetadata.Unknown
            }
        )
    }
}