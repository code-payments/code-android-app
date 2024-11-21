package xyz.flipchat.app.ui.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.registry.ScreenRegistry
import xyz.flipchat.app.features.balance.BalanceScreen
import xyz.flipchat.app.features.chat.conversation.ConversationScreen
import xyz.flipchat.app.features.chat.list.ChatListScreen
import xyz.flipchat.app.features.home.TabbedHomeScreen
import xyz.flipchat.app.features.login.LoginScreen
import xyz.flipchat.app.features.settings.SettingsScreen
import com.getcode.navigation.NavScreenProvider
import xyz.flipchat.app.features.chat.cover.CoverChargeScreen
import xyz.flipchat.app.features.chat.info.ChatInfoScreen
import xyz.flipchat.app.features.chat.lookup.LookupRoomScreen
import xyz.flipchat.app.features.chat.lookup.confirm.JoinConfirmationScreen
import xyz.flipchat.app.features.login.accesskey.AccessKeyScreen
import xyz.flipchat.app.features.login.accesskey.SeedInputScreen
import xyz.flipchat.app.features.login.permissions.NotificationPermissionScreen
import xyz.flipchat.app.features.login.register.RegisterScreen

@Composable
fun AppScreenContent(content: @Composable () -> Unit) {
    ScreenRegistry {
        register<NavScreenProvider.Login.Home> {
            LoginScreen(it.seed)
        }

        register<NavScreenProvider.Login.Registration> {
            RegisterScreen
        }

        register<NavScreenProvider.Login.AccessKey> {
            AccessKeyScreen
        }

        register<NavScreenProvider.Login.NotificationPermission> {
            NotificationPermissionScreen(it.fromOnboarding)
        }

        register<NavScreenProvider.Login.SeedInput> {
            SeedInputScreen
        }

        register<NavScreenProvider.Balance> {
            BalanceScreen
        }

        register<NavScreenProvider.AppHomeScreen> {
            TabbedHomeScreen(it.deeplink)
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

        register<NavScreenProvider.Chat.ChangeCover> {
            CoverChargeScreen(it.id)
        }

        register<NavScreenProvider.Settings> {
            SettingsScreen
        }
    }
    content()
}