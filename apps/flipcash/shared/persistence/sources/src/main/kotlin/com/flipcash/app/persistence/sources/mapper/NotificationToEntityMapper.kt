package com.flipcash.app.persistence.sources.mapper

import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.persistence.entities.MessageEntity
import com.flipcash.services.models.ActivityFeedNotification
import com.flipcash.services.models.NotificationMetadata
import com.getcode.opencode.internal.domain.mapper.Mapper
import com.getcode.utils.base58
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class NotificationToEntityMapper @Inject constructor(
    private val singleMapper: SingleNotificationToEntityMapper
) : Mapper<List<ActivityFeedNotification>, List<MessageEntity>> {
    override fun map(from: List<ActivityFeedNotification>): List<MessageEntity> {
        return from.map { singleMapper.map(it) }
    }
}

class SingleNotificationToEntityMapper @Inject constructor(
    private val metadataMapper: MetadataMapper
) :
    Mapper<ActivityFeedNotification, MessageEntity> {
    override fun map(from: ActivityFeedNotification): MessageEntity {

        val (usdc, native, currency, fx) = from.amount?.let { amount ->
            AmountHolder(
                usdc = amount.usdc.quarks.toLong(),
                native = amount.converted.quarks.toLong(),
                currency = amount.converted.currencyCode.name,
                fx = amount.rate.fx
            )
        } ?: AmountHolder()

        return MessageEntity(
            idBase58 = from.id.base58,
            text = from.text,
            timestamp = from.timestamp.toEpochMilliseconds(),
            state = from.state.name,
            metadata = metadataMapper.map(from.metadata)?.let { Json.encodeToString(it) },
            // financials
            amountUsdc = usdc,
            amountNative = native,
            nativeCurrency = currency,
            rate = fx
        )
    }
}

class MetadataMapper @Inject constructor(): Mapper<NotificationMetadata?, MessageMetadata?> {
    override fun map(from: NotificationMetadata?): MessageMetadata? {
        from ?: return null
        return when (from) {
            NotificationMetadata.GaveUsdc -> MessageMetadata.GaveUsdc
            NotificationMetadata.ReceivedUsdc -> MessageMetadata.ReceivedUsdc
            is NotificationMetadata.SentUsdc -> MessageMetadata.SentUsdc(from.creator, from.canCancel)
            NotificationMetadata.Unknown -> MessageMetadata.Unknown
            NotificationMetadata.WelcomeBonus -> MessageMetadata.WelcomeBonus
            NotificationMetadata.WithdrewUsdc -> MessageMetadata.WithdrewUsdc
            NotificationMetadata.DepositedUsdc -> MessageMetadata.DepositedUsdc
        }
    }
}

private data class AmountHolder(
    val usdc: Long? = null,
    val native: Long? = null,
    val currency: String? = null,
    val fx: Double? = null
)