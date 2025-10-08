package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.activity.v1.Model
import com.codeinc.flipcash.gen.activity.v1.paymentAmountOrNull
import com.codeinc.flipcash.gen.pool.v1.Model.*
import com.flipcash.libs.currency.math.units
import com.flipcash.services.internal.domain.mapper.Mapper
import com.flipcash.services.internal.extensions.toPublicKey
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.internal.network.extensions.toPublicKey
import com.flipcash.services.models.ActivityFeedNotification
import com.flipcash.services.models.NetworkPoolResolution
import com.flipcash.services.models.NotificationMetadata
import com.flipcash.services.models.NotificationState
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import kotlinx.datetime.Instant
import java.math.BigDecimal
import javax.inject.Inject

internal class ActivityFeedMessageMapper @Inject constructor(
) : Mapper<Model.Notification, ActivityFeedNotification> {
    override fun map(from: Model.Notification): ActivityFeedNotification {
        return ActivityFeedNotification(
            id = from.id.toId(),
            text = from.localizedText,
            amount = from.paymentAmountOrNull?.let {
                val units = BigDecimal(it.quarks).units()
                val rate = Rate(1f / units.toDouble(), CurrencyCode.tryValueOf(it.currency) ?: CurrencyCode.USD)
                LocalFiat(
                    underlyingTokenAmount = Fiat(quarks = it.quarks),
                    mint = it.mint.toPublicKey(),
                    rate = rate,
                    nativeAmount = Fiat(fiat = it.nativeAmount, currencyCode = CurrencyCode.tryValueOf(it.currency) ?: CurrencyCode.USD),
                )
            },
            timestamp = Instant.fromEpochSeconds(from.ts.seconds),
            state = when (from.state) {
                Model.NotificationState.NOTIFICATION_STATE_PENDING -> NotificationState.PENDING
                Model.NotificationState.NOTIFICATION_STATE_COMPLETED -> NotificationState.COMPLETED
                Model.NotificationState.NOTIFICATION_STATE_UNKNOWN,
                Model.NotificationState.UNRECOGNIZED,
                null -> NotificationState.UNKNOWN
            },
            metadata = when (from.additionalMetadataCase) {
                Model.Notification.AdditionalMetadataCase.WELCOME_BONUS -> NotificationMetadata.WelcomeBonus
                Model.Notification.AdditionalMetadataCase.GAVE_CRYPTO -> NotificationMetadata.GaveCrypto
                Model.Notification.AdditionalMetadataCase.RECEIVED_CRYPTO -> NotificationMetadata.ReceivedCrypto
                Model.Notification.AdditionalMetadataCase.WITHDREW_CRYPTO -> NotificationMetadata.WithdrewCrypto
                Model.Notification.AdditionalMetadataCase.SENT_CRYPTO -> NotificationMetadata.SentCrypto(
                    creator = from.sentCrypto.vault.value.toByteArray().toPublicKey(),
                    canCancel = from.sentCrypto.canInitiateCancelAction
                )
                Model.Notification.AdditionalMetadataCase.DEPOSITED_CRYPTO -> NotificationMetadata.DepositedCrypto
                Model.Notification.AdditionalMetadataCase.PAID_CRYPTO -> NotificationMetadata.PaidCrypto(
                    poolId = from.paidCrypto.pool.poolId.value.toList()
                )
                Model.Notification.AdditionalMetadataCase.DISTRIBUTED_CRYPTO -> NotificationMetadata.DistributedUsdc(
                    poolId = from.distributedCrypto.pool.poolId.value.toList(),
                    outcome = when (from.distributedCrypto.pool.outcome) {
                        UserOutcome.WIN_OUTCOME -> NetworkPoolResolution.BooleanResolution(true)
                        UserOutcome.LOSE_OUTCOME -> NetworkPoolResolution.BooleanResolution(false)
                        UserOutcome.REFUND_OUTCOME -> NetworkPoolResolution.Refund
                        UserOutcome.UNKNOWN_OUTCOE,
                        UserOutcome.NO_OUTCOME,
                        UserOutcome.UNRECOGNIZED -> NetworkPoolResolution.NotSet
                    }
                )
                Model.Notification.AdditionalMetadataCase.ADDITIONALMETADATA_NOT_SET,
                null -> NotificationMetadata.Unknown
            }
        )
    }
}