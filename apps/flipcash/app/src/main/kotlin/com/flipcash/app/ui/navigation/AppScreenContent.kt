package com.flipcash.app.ui.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.appsettings.AppSettingsScreen
import com.flipcash.app.backupkey.BackupKeyScreen
import com.flipcash.app.balance.BalanceScreen
import com.flipcash.app.balance.PreloadBalance
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.currency.CurrencySelectionModal
import com.flipcash.app.deposit.DepositScreen
import com.flipcash.app.cash.CashScreen
import com.flipcash.app.lab.LabScreen
import com.flipcash.app.login.accesskey.AccessKeyScreen
import com.flipcash.app.login.router.LoginRouter
import com.flipcash.app.login.seed.SeedInputScreen
import com.flipcash.app.menu.MenuScreen
import com.flipcash.app.myaccount.MyAccountScreen
import com.flipcash.app.permissions.CameraPermissionScreen
import com.flipcash.app.permissions.NotificationPermissionScreen
import com.flipcash.app.purchase.PurchaseAccountScreen
import com.flipcash.app.scanner.ScannerScreen
import com.flipcash.app.shareapp.ShareAppScreen
import com.flipcash.app.withdrawal.WithdrawalConfirmationScreen
import com.flipcash.app.withdrawal.WithdrawalDestinationScreen
import com.flipcash.app.withdrawal.WithdrawalEntryScreen
import com.flipcash.app.withdrawal.WithdrawalFlow


@Composable
fun AppScreenContent(content: @Composable () -> Unit) {
    ScreenRegistry {
        register<NavScreenProvider.Login.Home> {
            LoginRouter(it.seed, it.fromDeeplink)
        }

        register<NavScreenProvider.Login.SeedInput> {
            SeedInputScreen()
        }

        register<NavScreenProvider.CreateAccount.AccessKey> {
            AccessKeyScreen()
        }

        register<NavScreenProvider.CreateAccount.Purchase> {
            PurchaseAccountScreen()
        }

        register<NavScreenProvider.Permissions.Notification> {
            NotificationPermissionScreen(it.fromOnboarding)
        }

        register<NavScreenProvider.Permissions.Camera> {
            CameraPermissionScreen(it.fromOnboarding)
        }

        register<NavScreenProvider.HomeScreen.Scanner> {
            ScannerScreen(it.deeplink)
        }

//        register<NavScreenProvider.HomeScreen.Give> {
//            CashScreen()
//        }
//
//        register<NavScreenProvider.HomeScreen.Send> {
//            SendScreen()
//        }

        register<NavScreenProvider.HomeScreen.Cash> {
            CashScreen()
        }

        register<NavScreenProvider.HomeScreen.Balance> {
            BalanceScreen()
        }

        register<NavScreenProvider.HomeScreen.CurrencySelection> {
            CurrencySelectionModal(it.kind)
        }
        
        register<NavScreenProvider.HomeScreen.ShareApp> {
            ShareAppScreen()
        }

        register<NavScreenProvider.HomeScreen.Menu.Root> {
            MenuScreen()
        }

        register<NavScreenProvider.HomeScreen.Menu.AppSettings> {
            AppSettingsScreen()
        }

        register<NavScreenProvider.HomeScreen.Menu.Lab> {
            LabScreen()
        }

        register<NavScreenProvider.HomeScreen.Menu.Deposit> {
            DepositScreen()
        }

        register<NavScreenProvider.HomeScreen.Menu.Withdrawal.Amount> {
            WithdrawalFlow.start()
            WithdrawalEntryScreen()
        }

        register<NavScreenProvider.HomeScreen.Menu.Withdrawal.Destination> {
            WithdrawalDestinationScreen()
        }

        register<NavScreenProvider.HomeScreen.Menu.Withdrawal.Confirmation> {
            WithdrawalConfirmationScreen()
        }

        register<NavScreenProvider.HomeScreen.Menu.MyAccount.Root> {
            MyAccountScreen()
        }

        register<NavScreenProvider.HomeScreen.Menu.MyAccount.BackupKey> {
            BackupKeyScreen()
        }
    }

    PreloadBalance()

    content()
}