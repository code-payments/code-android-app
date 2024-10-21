package com.getcode.oct24

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipchat.features.balance.BalanceScreen
import com.flipchat.features.balance.BalanceSheetViewModel
import com.flipchat.features.chat.conversation.ConversationScreen
import com.flipchat.features.chat.list.ChatListScreen
import com.flipchat.features.chat.lookup.ChatByUsernameScreen
import com.flipchat.features.home.TabbedHomeScreen
import com.flipchat.features.login.LoginScreen
import com.flipchat.features.settings.SettingsScreen
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.oct24.features.login.accesskey.SeedInputScreen
import com.getcode.oct24.features.login.register.RegisterScreen

@Composable
fun AppScreenContent(content: @Composable () -> Unit) {
    ScreenRegistry {
        register<NavScreenProvider.Registration> {
            RegisterScreen
        }

        register<NavScreenProvider.Login.Home> {
            LoginScreen(it.seed)
        }

        register<NavScreenProvider.Login.SeedInput> {
            SeedInputScreen
        }

        register<NavScreenProvider.Balance> {
            BalanceScreen
        }

        register<NavScreenProvider.AppHomeScreen> {
            TabbedHomeScreen
        }

        register<NavScreenProvider.Chat.List> {
            ChatListScreen
        }

        register<NavScreenProvider.Chat.Conversation> {
            ConversationScreen(
                chatId = it.chatId,
                intentId = it.intentId,
                user = it.user
            )
        }

        register<NavScreenProvider.Settings> {
            SettingsScreen
        }
    }

    // preload balance VM
    getActivityScopedViewModel<BalanceSheetViewModel>()

    content()
}