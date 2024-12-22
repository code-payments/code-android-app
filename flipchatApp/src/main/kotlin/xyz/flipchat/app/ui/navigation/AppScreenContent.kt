package xyz.flipchat.app.ui.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.registry.ScreenRegistry
import xyz.flipchat.app.features.balance.BalanceScreen
import xyz.flipchat.app.features.chat.conversation.ConversationScreen
import xyz.flipchat.app.features.chat.list.RoomListScreen
import xyz.flipchat.app.features.home.TabbedHomeScreen
import xyz.flipchat.app.features.login.LoginScreen
import xyz.flipchat.app.features.settings.SettingsScreen
import com.getcode.navigation.NavScreenProvider
import xyz.flipchat.app.features.beta.BetaFlagsScreen
import xyz.flipchat.app.features.chat.cover.CoverChargeScreen
import xyz.flipchat.app.features.chat.info.RoomInfoScreen
import xyz.flipchat.app.features.chat.lookup.LookupRoomScreen
import xyz.flipchat.app.features.chat.lookup.confirm.JoinConfirmationScreen
import xyz.flipchat.app.features.chat.name.RoomNameScreen
import xyz.flipchat.app.features.login.register.AccessKeyModalScreen
import xyz.flipchat.app.features.login.accesskey.AccessKeyScreen
import xyz.flipchat.app.features.login.accesskey.SeedInputScreen
import xyz.flipchat.app.features.login.permissions.NotificationPermissionScreen
import xyz.flipchat.app.features.login.register.PurchaseAccountScreen
import xyz.flipchat.app.features.login.register.RegisterInfoScreen
import xyz.flipchat.app.features.login.register.RegisterModalScreen
import xyz.flipchat.app.features.login.register.RegisterScreen

@Composable
fun AppScreenContent(content: @Composable () -> Unit) {
    ScreenRegistry {
        register<NavScreenProvider.Login.Home> {
            LoginScreen(it.seed)
        }

        register<NavScreenProvider.CreateAccount.Start> {
            RegisterInfoScreen()
        }

        register<NavScreenProvider.CreateAccount.NameEntry> {
            if (it.showInModal) {
                RegisterModalScreen()
            } else {
                RegisterScreen()
            }
        }

        register<NavScreenProvider.CreateAccount.AccessKey> {
            if (it.showInModal) {
                AccessKeyModalScreen()
            } else {
                AccessKeyScreen()
            }
        }

        register<NavScreenProvider.CreateAccount.Purchase> {
            PurchaseAccountScreen()
        }

        register<NavScreenProvider.Login.NotificationPermission> {
            NotificationPermissionScreen(it.fromOnboarding)
        }

        register<NavScreenProvider.Login.SeedInput> {
            SeedInputScreen
        }

        register<NavScreenProvider.Balance> {
            BalanceScreen()
        }

        register<NavScreenProvider.AppHomeScreen> {
            TabbedHomeScreen(it.deeplink)
        }

        register<NavScreenProvider.Room.List> {
            RoomListScreen()
        }

        register<NavScreenProvider.Room.Lookup.Entry> {
            LookupRoomScreen()
        }

        register<NavScreenProvider.Room.Lookup.Confirm> {
            JoinConfirmationScreen(it.args, it.returnToSender)
        }

        register<NavScreenProvider.Room.Messages> {
            ConversationScreen(
                chatId = it.chatId,
                intentId = it.intentId,
            )
        }

        register<NavScreenProvider.Room.Info> {
            RoomInfoScreen(it.args)
        }

        register<NavScreenProvider.Room.ChangeCover> {
            CoverChargeScreen(it.id)
        }

        register<NavScreenProvider.Room.ChangeName> {
            RoomNameScreen(it.id, it.title)
        }

        register<NavScreenProvider.Settings> {
            SettingsScreen()
        }

        register<NavScreenProvider.BetaFlags> {
            BetaFlagsScreen()
        }
    }
    content()
}