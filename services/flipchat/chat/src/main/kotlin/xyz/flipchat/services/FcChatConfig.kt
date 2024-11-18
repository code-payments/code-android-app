package xyz.flipchat.services

import com.getcode.services.ChannelConfig
import xyz.flipchat.services.chat.BuildConfig

internal data class FcChatConfig(
    override val baseUrl: String = "chat.api.flipchat-infra.xyz",
    override val userAgent: String = "Flipchat/Chat/Android/${BuildConfig.VERSION_NAME}",
): ChannelConfig
