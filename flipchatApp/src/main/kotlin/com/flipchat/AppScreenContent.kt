package com.flipchat

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipchat.features.chat.conversation.ConversationScreen
import com.flipchat.features.chat.list.ChatListScreen
import com.flipchat.features.chat.lookup.ChatByUsernameScreen
import com.getcode.navigation.NavScreenProvider

@Composable
fun AppScreenContent(content: @Composable () -> Unit) {
    ScreenRegistry {
        register<NavScreenProvider.Chat.List> {
            ChatListScreen
        }

        register<NavScreenProvider.Chat.ChatByUsername> {
            ChatByUsernameScreen
        }

        register<NavScreenProvider.Chat.Conversation> {
            ConversationScreen(
                chatId = it.chatId,
                intentId = it.intentId,
                user = it.user
            )
        }
    }

    content()
}