package xyz.flipchat.services

import com.getcode.services.ChannelConfig
import xyz.flipchat.services.payments.BuildConfig

internal data class FcPaymentsConfig(
    override val baseUrl: String = "payments.api.flipchat-infra.xyz",
    override val userAgent: String = "Flipchat/Payments/Android/${BuildConfig.VERSION_NAME}",
): ChannelConfig
