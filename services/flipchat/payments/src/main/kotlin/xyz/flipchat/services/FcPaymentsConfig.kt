package xyz.flipchat.services

import com.getcode.oct24.services.payments.BuildConfig
import com.getcode.services.ChannelConfig

internal data class FcPaymentsConfig(
    override val baseUrl: String = "payments.api.flipchat-infra.xyz",
    override val userAgent: String = "Flipchat/Payments/Android/${BuildConfig.VERSION_NAME}",
): ChannelConfig
