package com.flipcash.app.persistence.sources.mapper

import com.flipcash.app.persistence.entities.MessageEntity
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.feed.MessageState
import com.getcode.opencode.internal.domain.mapper.Mapper
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import kotlinx.datetime.Instant
import javax.inject.Inject

class MessageEntityToFeedMessageMapper @Inject constructor() : Mapper<MessageEntity, ActivityFeedMessage> {
    override fun map(from: MessageEntity): ActivityFeedMessage {
        val usdcAmount = from.amountUsdc
        val nativeAmount = from.amountNative
        val currencyCode = CurrencyCode.tryValueOf(from.nativeCurrency)
        val fx = from.rate

        val amount = if (usdcAmount != null && nativeAmount != null && currencyCode != null && fx != null) {
            val usdc = Fiat(usdcAmount.toULong(), CurrencyCode.USD)
            val native = Fiat(nativeAmount.toULong(), currencyCode)
            val rate = Rate(fx, currencyCode)
            LocalFiat(usdc, native, rate)
        } else {
            null
        }

        return ActivityFeedMessage(
            id = from.id,
            text = from.text,
            amount = amount,
            timestamp = Instant.fromEpochMilliseconds(from.timestamp),
            state = MessageState.from(from.state),
            metadata = MessageMetadata.from(from.metadata)
        )
    }
}

