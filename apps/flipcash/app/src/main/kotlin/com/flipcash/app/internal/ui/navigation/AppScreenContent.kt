package com.flipcash.app.internal.ui.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import com.flipcash.app.advanced.AdvancedFeaturesScreen
import com.flipcash.app.appsettings.AppSettingsScreen
import com.flipcash.app.backupkey.BackupKeyScreen
import com.flipcash.app.balance.BalanceScreen
import com.flipcash.app.balance.PreloadBalance
import com.flipcash.app.cash.CashScreen
import com.flipcash.app.contact.verification.VerificationFlowScreen
import com.flipcash.app.core.AppRoute
import com.flipcash.app.currency.CurrencySelectionScreen
import com.flipcash.app.deposit.DepositScreen
import com.flipcash.app.lab.LabsScreen
import com.flipcash.app.lab.PreloadLabs
import com.flipcash.app.lab.StandaloneLabsScreen
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
import com.flipcash.app.pools.PoolListSheet
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
        register<AppRoute.Onboarding.Login> {
            LoginRouter(it.seed, it.fromDeeplink)
        }

        register<AppRoute.Onboarding.SeedInput> {
            SeedInputScreen()
        }

        register<AppRoute.Onboarding.AccessKey> {
            AccessKeyScreen()
        }

        register<AppRoute.Onboarding.Purchase> {
            PurchaseAccountScreen(it.fromLogin)
        }

        register<AppRoute.Onboarding.NotificationPermission> {
            NotificationPermissionScreen(it.postCreate)
        }

        register<AppRoute.Onboarding.CameraPermission> {
            CameraPermissionScreen(it.postCreate)
        }

        register<AppRoute.Sheets.Lab> {
            StandaloneLabsScreen()
        }

        register<AppRoute.Main.AppRestricted> {
            AppRestrictedScreen(it.restrictionType)
        }

        register<AppRoute.Main.Scanner> {
            ScannerScreen(it.deeplink)
        }

        register<AppRoute.Sheets.Give> {
            CashScreen()
        }

        register<AppRoute.Sheets.Wallet> {
            BalanceScreen()
        }

        register<AppRoute.Sheets.PoolList> {
            PoolListSheet()
        }

        register<AppRoute.Pool.Create.Name> {
            PoolCreateFlow.start()
            PoolQuestionScreen()
        }

        register<AppRoute.Pool.Create.Amount> {
            PoolCustomBidEntryScreen()
        }

        register<AppRoute.Pool.Create.Confirmation> {
            PoolConfirmationScreen()
        }

        register<AppRoute.Pool.Details> {
             PoolBettingScreen(
                 poolId = it.poolId,
                 rendezvous = it.rendezvous
             )
        }

        register<AppRoute.Main.CurrencySelection> {
            CurrencySelectionScreen(it.kind)
        }
        
        register<AppRoute.Sheets.ShareApp> {
            ShareAppScreen()
        }

        register<AppRoute.Verification> {
            VerificationFlowScreen(
                origin = it.origin,
                target = it.target,
                includePhone = it.includePhone,
                includeEmail = it.includeEmail,
                emailAddress = it.email,
                emailVerificationCode = it.emailVerificationCode
            )
        }

        register<AppRoute.OnRamp.ProviderList> {
            OnRampFlowTracker.start(it.from)
            OnRampProviderListScreen(
                neededAmount = it.neededAmount?.quarks,
                neededCurrency = it.neededAmount?.currencyCode
            )
        }

        register<AppRoute.OnRamp.AmountEntry> {
            OnRampCustomAmountScreen()
        }

        register<AppRoute.Sheets.Menu> {
            MenuScreen()
        }

        register<AppRoute.Menu.AppSettings> {
            AppSettingsScreen()
        }

        register<AppRoute.Menu.Lab> {
            LabsScreen()
        }

        register<AppRoute.Transfers.Learn> {
            TransferInformationalScreen(it.direction)
        }

        register<AppRoute.Transfers.Withdrawal.Amount> {
            WithdrawalFlow.start()
            WithdrawalEntryScreen()
        }

        register<AppRoute.Transfers.Withdrawal.Destination> {
            WithdrawalDestinationScreen()
        }

        register<AppRoute.Transfers.Withdrawal.Confirmation> {
            WithdrawalConfirmationScreen()
        }

        register<AppRoute.Menu.MyAccount> {
            MyAccountScreen()
        }

        register<AppRoute.Menu.BackupKey> {
            BackupKeyScreen()
        }

        register<AppRoute.Menu.AdvancedFeatures> {
            AdvancedFeaturesScreen()
        }

        register<AppRoute.Advanced.Deposit> {
            DepositScreen()
        }

        register<AppRoute.Advanced.PoolList> {
            PoolListScreen()
        }
    }

    PreloadBalance()
    PreloadLabs()

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