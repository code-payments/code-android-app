package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.activity.v1.Model
import com.codeinc.flipcash.gen.activity.v1.paymentAmountOrNull
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.models.ActivityFeedMessage
import com.flipcash.services.models.FeedMessageMetadata
import com.getcode.opencode.internal.domain.mapper.Mapper
import kotlinx.datetime.Instant
import javax.inject.Inject

internal class ActivityFeedMessageMapper @Inject constructor(
    private val paymentAmountMapper: PaymentAmountMapper,
) : Mapper<Model.Notification, ActivityFeedMessage> {
    override fun map(from: Model.Notification): ActivityFeedMessage {
        return ActivityFeedMessage(
            id = from.id.toId(),
            text = from.localizedText,
            amount = from.paymentAmountOrNull?.let { paymentAmountMapper.map(it) },
            timestamp = Instant.fromEpochSeconds(from.ts.seconds, 0),
            metadata = when (from.additionalMetadataCase) {
                Model.Notification.AdditionalMetadataCase.WELCOME_BONUS -> FeedMessageMetadata.WelcomeBonus
                Model.Notification.AdditionalMetadataCase.GAVE_USDC -> FeedMessageMetadata.GaveUsdc
                Model.Notification.AdditionalMetadataCase.RECEIVED_USDC -> FeedMessageMetadata.ReceivedUsdc
                Model.Notification.AdditionalMetadataCase.WITHDREW_USDC -> FeedMessageMetadata.WithdrewUsdc
                Model.Notification.AdditionalMetadataCase.ADDITIONALMETADATA_NOT_SET,
                null -> FeedMessageMetadata.Unknown
            }

        )
    }
}