package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.activity.v1.Model
import com.codeinc.flipcash.gen.activity.v1.paymentAmountOrNull
import com.codeinc.flipcash.gen.common.v1.mintOrNull
import com.flipcash.libs.currency.math.units
import com.flipcash.services.internal.domain.mapper.Mapper
import com.flipcash.services.internal.extensions.toPublicKey
import com.flipcash.services.internal.network.extensions.asSubstitution
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.internal.network.extensions.toMint
import com.flipcash.services.internal.network.extensions.toPublicKey
import com.flipcash.services.models.ActivityFeedNotification
import com.flipcash.services.models.NotificationMetadata
import com.flipcash.services.models.NotificationState
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import kotlin.time.Instant
import java.math.BigDecimal
import javax.inject.Inject

internal class ActivityFeedMessageMapper @Inject constructor(
) : Mapper<Model.Notification, ActivityFeedNotification> {
    override fun map(from: Model.Notification): ActivityFeedNotification {
        return ActivityFeedNotification(
            id = from.id.toId(),
            text = from.localizedText,
            amount = from.paymentAmountOrNull?.let {
                val currencyCode = CurrencyCode.tryValueOf(it.currency) ?: CurrencyCode.USD
                val tokenAmount = Fiat(quarks = it.quarks)
                val nativeAmount = Fiat(fiat = it.nativeAmount, currencyCode)
                // if no mint, or it's usdf, then we can operate as a normal localized Fiat
                if (it.mintOrNull == Mint.usdf || it.mintOrNull == null) {
                    LocalFiat(
                        usdf = tokenAmount,
                        nativeAmount = nativeAmount,
                    )
                } else {
                    val units = BigDecimal(it.quarks).units()
                    val rate = Rate(
                        1f / units.toDouble(),
                       currencyCode,
                    )
                    LocalFiat(
                        underlyingTokenAmount = tokenAmount,
                        mint = it.mint.toMint(),
                        rate = rate,
                        nativeAmount = nativeAmount,
                    )
                }
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
                Model.Notification.AdditionalMetadataCase.DIRECTLY_SENT_CRYPTO -> {
                    val meta = from.directlySentCrypto
                    NotificationMetadata.DirectlySentCrypto(
                        phoneNumber = if (meta.destinationIdentifierCase ==
                            Model.DirectlySentCryptoNotificationMetadata.DestinationIdentifierCase.PHONE
                        ) meta.phone.value else null,
                        userId = if (meta.destinationIdentifierCase ==
                            Model.DirectlySentCryptoNotificationMetadata.DestinationIdentifierCase.USER_ID
                        ) meta.userId.value.toByteArray().toList() else null,
                    )
                }
                Model.Notification.AdditionalMetadataCase.RECEIVED_CRYPTO -> {
                    val meta = from.receivedCrypto
                    NotificationMetadata.ReceivedCrypto(
                        phoneNumber = if (meta.sourceIdentifierCase ==
                            Model.ReceivedCryptoNotificationMetadata.SourceIdentifierCase.PHONE
                        ) meta.phone.value else null,
                        userId = if (meta.sourceIdentifierCase ==
                            Model.ReceivedCryptoNotificationMetadata.SourceIdentifierCase.USER_ID
                        ) meta.userId.value.toByteArray().toList() else null,
                    )
                }
                Model.Notification.AdditionalMetadataCase.WITHDREW_CRYPTO -> NotificationMetadata.WithdrewCrypto
                Model.Notification.AdditionalMetadataCase.INDIRECTLY_SENT_CRYPTO -> NotificationMetadata.IndirectlySentCrypto(
                    creator = from.indirectlySentCrypto.vault.value.toByteArray().toPublicKey(),
                    canCancel = from.indirectlySentCrypto.canInitiateCancelAction
                )
                Model.Notification.AdditionalMetadataCase.DEPOSITED_CRYPTO -> NotificationMetadata.DepositedCrypto
                Model.Notification.AdditionalMetadataCase.BOUGHT_CRYPTO -> NotificationMetadata.BoughtToken
                Model.Notification.AdditionalMetadataCase.SOLD_CRYPTO -> NotificationMetadata.SoldToken
                Model.Notification.AdditionalMetadataCase.ADDITIONALMETADATA_NOT_SET,
                null -> NotificationMetadata.Unknown
            },
            textSubstitutions = from.textSubstitutionsList.mapNotNull { it.asSubstitution() },
        )
    }
}