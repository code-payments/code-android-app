package com.flipcash.app.internal.ui.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import com.flipcash.app.appsettings.AppSettingsScreen
import com.flipcash.app.backupkey.BackupKeyScreen
import com.flipcash.app.balance.BalanceScreen
import com.flipcash.app.balance.PreloadBalance
import com.flipcash.app.cash.CashScreen
import com.flipcash.app.contact.verification.VerificationFlowScreen
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.currency.CurrencySelectionModal
import com.flipcash.app.deposit.DepositScreen
import com.flipcash.app.lab.LabsModal
import com.flipcash.app.lab.LabsScreen
import com.flipcash.app.login.accesskey.AccessKeyScreen
import com.flipcash.app.login.router.LoginRouter
import com.flipcash.app.login.seed.SeedInputScreen
import com.flipcash.app.menu.MenuScreen
import com.flipcash.app.myaccount.MyAccountScreen
import com.flipcash.app.onramp.OnRampCustomAmountScreen
import com.flipcash.app.onramp.OnRampFlowTracker
import com.flipcash.app.onramp.OnRampProviderListScreen
import com.flipcash.app.permissions.CameraPermissionScreen
import com.flipcash.app.permissions.NotificationPermissionScreen
import com.flipcash.app.pools.PoolBettingScreen
import com.flipcash.app.pools.PoolCreateFlow
import com.flipcash.app.pools.PoolListScreen
import com.flipcash.app.pools.create.PoolConfirmationScreen
import com.flipcash.app.pools.create.PoolCustomBidEntryScreen
import com.flipcash.app.pools.create.PoolQuestionScreen
import com.flipcash.app.purchase.PurchaseAccountScreen
import com.flipcash.app.scanner.ScannerScreen
import com.flipcash.app.shareapp.ShareAppScreen
import com.flipcash.app.transfers.TransferInformationalScreen
import com.flipcash.app.withdrawal.WithdrawalConfirmationScreen
import com.flipcash.app.withdrawal.WithdrawalDestinationScreen
import com.flipcash.app.withdrawal.WithdrawalEntryScreen
import com.flipcash.app.withdrawal.WithdrawalFlow
import com.getcode.navigation.modal.ModalScreen

@Composable
internal fun AppScreenContent(content: @Composable () -> Unit) {
    ScreenRegistry {
        register<NavScreenProvider.AppRestricted> {
            AppRestrictedScreen(it.restrictionType)
        }
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
            PurchaseAccountScreen(it.fromLogin)
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

        register<NavScreenProvider.HomeScreen.Pools.Root> {
            PoolListScreen()
        }

        register<NavScreenProvider.HomeScreen.Pools.Create.Name> {
            PoolCreateFlow.start()
            PoolQuestionScreen()
        }

        register<NavScreenProvider.HomeScreen.Pools.Create.Amount> {
            PoolCustomBidEntryScreen()
        }

        register<NavScreenProvider.HomeScreen.Pools.Create.Confirmation> {
            PoolConfirmationScreen()
        }

        register<NavScreenProvider.HomeScreen.Pools.ChoiceSelection> {
             PoolBettingScreen(
                 poolId = it.poolId,
                 rendezvous = it.rendezvous
             )
        }

        register<NavScreenProvider.HomeScreen.CurrencySelection> {
            CurrencySelectionModal(it.kind)
        }
        
        register<NavScreenProvider.HomeScreen.ShareApp> {
            ShareAppScreen()
        }

        register<NavScreenProvider.HomeScreen.Verification.Flow> {
            VerificationFlowScreen(
                origin = it.origin,
                target = it.target,
                includePhone = it.includePhone,
                includeEmail = it.includeEmail,
                emailAddress = it.email,
                emailVerificationCode = it.emailVerificationCode
            )
        }

        register<NavScreenProvider.HomeScreen.OnRamp.ProviderList> {
            OnRampFlowTracker.start(it.from)
            OnRampProviderListScreen(
                neededAmount = it.neededAmount?.quarks,
                neededCurrency = it.neededAmount?.currencyCode
            )
        }

        register<NavScreenProvider.HomeScreen.OnRamp.Amount> {
            OnRampCustomAmountScreen()
        }

        register<NavScreenProvider.HomeScreen.Menu.Root> {
            MenuScreen()
        }

        register<NavScreenProvider.HomeScreen.Menu.AppSettings> {
            AppSettingsScreen()
        }

        register<NavScreenProvider.Login.Lab> {
            LabsScreen()
        }

        register<NavScreenProvider.HomeScreen.Menu.Lab> {
            LabsModal()
        }

        register<NavScreenProvider.HomeScreen.Menu.Transfers.Learn> {
            TransferInformationalScreen(it.direction)
        }

        register<NavScreenProvider.HomeScreen.Menu.Transfers.Deposit> {
            DepositScreen()
        }

        register<NavScreenProvider.HomeScreen.Menu.Transfers.Withdrawal.Amount> {
            WithdrawalFlow.start()
            WithdrawalEntryScreen()
        }

        register<NavScreenProvider.HomeScreen.Menu.Transfers.Withdrawal.Destination> {
            WithdrawalDestinationScreen()
        }

        register<NavScreenProvider.HomeScreen.Menu.Transfers.Withdrawal.Confirmation> {
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

private class Dummy: Screen {
    @Composable
    override fun Content() {

    }
}

private class DummyModal: ModalScreen {
    @Composable
    override fun ModalContent() {

    }
}