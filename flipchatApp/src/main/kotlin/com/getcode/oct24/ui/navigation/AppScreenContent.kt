package com.getcode.oct24.ui.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipchat.features.balance.BalanceScreen
import com.flipchat.features.chat.conversation.ConversationScreen
import com.flipchat.features.chat.list.ChatListScreen
import com.flipchat.features.home.TabbedHomeScreen
import com.flipchat.features.login.LoginScreen
import com.flipchat.features.settings.SettingsScreen
import com.getcode.navigation.NavScreenProvider
import com.getcode.oct24.features.chat.info.ChatInfoScreen
import com.getcode.oct24.features.chat.lookup.LookupRoomScreen
import com.getcode.oct24.features.chat.lookup.confirm.JoinConfirmationScreen
import com.getcode.oct24.features.login.accesskey.SeedInputScreen
import com.getcode.oct24.features.login.permissions.NotificationPermissionScreen
import com.getcode.oct24.features.login.register.RegisterScreen

@Composable
fun AppScreenContent(content: @Composable () -> Unit) {
    ScreenRegistry {
        register<NavScreenProvider.Registration> {
            RegisterScreen
        }

        register<NavScreenProvider.NotificationPermission> {
            NotificationPermissionScreen(it.fromOnboarding)
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

        register<NavScreenProvider.Chat.Lookup.Entry> {
            LookupRoomScreen
        }

        register<NavScreenProvider.Chat.Lookup.Confirm> {
            JoinConfirmationScreen(it.args)
        }

        register<NavScreenProvider.Chat.Conversation> {
            ConversationScreen(
                chatId = it.chatId,
                intentId = it.intentId,
            )
        }

        register<NavScreenProvider.Chat.Info> {
            ChatInfoScreen(it.args)
        }

        register<NavScreenProvider.Settings> {
            SettingsScreen
        }
    }

    // preload balance VM
//    getActivityScopedViewModel<BalanceSheetViewModel>()

    content()
}