package xyz.flipchat.ui.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.registry.ScreenRegistry
import xyz.flipchat.features.balance.BalanceScreen
import xyz.flipchat.features.chat.conversation.ConversationScreen
import xyz.flipchat.features.chat.list.ChatListScreen
import xyz.flipchat.features.home.TabbedHomeScreen
import xyz.flipchat.features.login.LoginScreen
import xyz.flipchat.features.settings.SettingsScreen
import com.getcode.navigation.NavScreenProvider
import xyz.flipchat.features.chat.info.ChatInfoScreen
import xyz.flipchat.features.chat.lookup.LookupRoomScreen
import xyz.flipchat.features.chat.lookup.confirm.JoinConfirmationScreen
import xyz.flipchat.features.login.accesskey.SeedInputScreen
import xyz.flipchat.features.login.permissions.NotificationPermissionScreen
import xyz.flipchat.features.login.register.RegisterScreen

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
    content()
}