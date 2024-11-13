package xyz.flipchat.services

import com.getcode.oct24.services.chat.BuildConfig
import com.getcode.services.ChannelConfig

internal data class FcChatConfig(
    override val baseUrl: String = "api.flipchat.codeinfra.net", //"chat.api.flipchat-infra.xyz",
    override val userAgent: String = "Flipchat/Android/${BuildConfig.VERSION_NAME}",
): ChannelConfig
