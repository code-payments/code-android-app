package com.getcode.opencode

import com.getcode.services.ChannelConfig

internal data class OpenCodeConfig(
    override val baseUrl: String = "chat.api.flipchat-infra.xyz",
    override val userAgent: String = "Flipchat/Chat/Android/${BuildConfig.VERSION_NAME}",
): ChannelConfig
