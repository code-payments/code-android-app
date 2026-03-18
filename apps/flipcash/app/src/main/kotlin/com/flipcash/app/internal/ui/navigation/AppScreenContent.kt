package com.flipcash.app.internal.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.flipcash.app.advanced.AdvancedFeaturesScreen
import com.flipcash.app.appsettings.AppSettingsScreen
import com.flipcash.app.backupkey.BackupKeyScreen
import com.flipcash.app.balance.BalanceScreen
import com.flipcash.app.balance.PreloadBalance
import com.flipcash.app.cash.CashScreen
import com.flipcash.app.contact.verification.VerificationFlowScreen
import com.flipcash.app.core.AppRoute
import com.flipcash.app.currency.RegionSelectionScreen
import com.flipcash.app.deposit.DepositScreen
import com.flipcash.app.internal.ui.navigation.decorators.rememberNavMessagingEntryDecorator
import com.flipcash.app.lab.LabsScreen
import com.flipcash.app.lab.PreloadLabs
import com.flipcash.app.lab.StandaloneLabsScreen
import com.flipcash.app.login.accesskey.AccessKeyScreen
import com.flipcash.app.login.accesskey.PhotoAccessKeyScreen
import com.flipcash.app.login.router.LoginRouter
import com.flipcash.app.login.seed.SeedInputScreen
import com.flipcash.app.menu.MenuScreen
import com.flipcash.app.myaccount.MyAccountScreen
import com.flipcash.app.onramp.OnRampCustomAmountScreen
import com.flipcash.app.onramp.OnRampFlowTracker
import com.flipcash.app.onramp.OnRampProviderListScreen
import com.flipcash.app.purchase.PurchaseAccountScreen
import com.flipcash.app.scanner.ScannerScreen
import com.flipcash.app.shareapp.ShareAppScreen
import com.flipcash.app.tokens.BuySellFlow
import com.flipcash.app.tokens.TokenBuySellEntryScreen
import com.flipcash.app.tokens.TokenInfoScreen
import com.flipcash.app.tokens.TokenSelectScreen
import com.flipcash.app.tokens.TokenSellReceiptScreen
import com.flipcash.app.tokens.TokenTxProcessingScreen
import com.flipcash.app.transactions.TransactionHistoryScreen
import com.flipcash.app.withdrawal.WithdrawalConfirmationScreen
import com.flipcash.app.withdrawal.WithdrawalDestinationScreen
import com.flipcash.app.withdrawal.WithdrawalEntryScreen
import com.flipcash.app.withdrawal.WithdrawalFlow
import com.getcode.navigation.AppNavHost
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.core.rememberCodeNavigator
import com.getcode.navigation.metadata
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher
import com.getcode.navigation.scenes.ModalBottomSheetSceneStrategy
import com.getcode.ui.components.bars.BarManager
import dev.theolm.rinku.DeepLink

@Composable
fun AppPreloads() {
    PreloadBalance()
    PreloadLabs()
}

fun appEntryProvider(
    key: NavKey,
    resultStateRegistry: NavResultStateRegistry,
    barManager: BarManager,
    deepLink: () -> DeepLink?,
): NavEntry<NavKey> {
    return when (key) {
        is AppRoute.Loading -> NavEntry(key = key, metadata = key.metadata()) {
            MainRoot(deepLink)
        }

        is AppRoute.Onboarding.Login -> NavEntry(key = key, metadata = key.metadata()) {
            LoginRouter(key.seed, key.fromDeeplink)
        }
        is AppRoute.Onboarding.SeedInput -> NavEntry(key = key, metadata = key.metadata()) {
            SeedInputScreen()
        }
        is AppRoute.Onboarding.AccessKey -> NavEntry(key = key, metadata = key.metadata()) {
            AccessKeyScreen()
        }
        is AppRoute.Onboarding.AccessKeySavedLocation -> NavEntry(key = key, metadata = key.metadata()) {
            PhotoAccessKeyScreen()
        }
        is AppRoute.Onboarding.Purchase -> NavEntry(key = key, metadata = key.metadata()) {
            PurchaseAccountScreen(key.fromLogin)
        }
        is AppRoute.Onboarding.NotificationPermission,
        is AppRoute.Onboarding.CameraPermission -> NavEntry(key = key, metadata = key.metadata()) {
            // Deprecated — permissions requested at time of use
        }

        is AppRoute.Main.Sheet -> sheetEntry(key, resultStateRegistry, barManager)
        is AppRoute.Main.AppRestricted -> NavEntry(key = key, metadata = key.metadata()) {
            AppRestrictedScreen(key.restrictionType)
        }
        is AppRoute.Main.Scanner -> NavEntry(key = key, metadata = key.metadata()) {
            ScannerScreen(key.deeplink)
        }
        is AppRoute.Sheets.Give -> NavEntry(key = key, metadata = key.metadata()) {
            CashScreen(key.mint, key.fromTokenInfo)
        }
        is AppRoute.Main.RegionSelection -> NavEntry(key = key, metadata = key.metadata()) {
            RegionSelectionScreen(key.kind)
        }

        is AppRoute.Token.Info -> NavEntry(key = key, metadata = key.metadata()) {
            TokenInfoScreen(key.mint, key.forNeededFunds, key.fromDeeplink)
        }
        is AppRoute.Token.Transactions -> NavEntry(key = key, metadata = key.metadata()) {
            TransactionHistoryScreen(key.mint)
        }
        is AppRoute.Token.SwapTransact -> NavEntry(key = key, metadata = key.metadata()) {
            remember { BuySellFlow.start(key.forNeededFunds) }
            TokenBuySellEntryScreen(key.purpose)
        }
        is AppRoute.Token.TxProcessing -> NavEntry(key = key, metadata = key.metadata()) {
            TokenTxProcessingScreen(key.swapId, key.awaitExternalWallet)
        }
        is AppRoute.Token.SellReceipt -> NavEntry(key = key, metadata = key.metadata()) {
            TokenSellReceiptScreen()
        }

        is AppRoute.Sheets.TokenSelection -> NavEntry(key = key, metadata = key.metadata()) {
            TokenSelectScreen(key.purpose)
        }
        is AppRoute.Sheets.Wallet -> NavEntry(key = key, metadata = key.metadata()) {
            BalanceScreen()
        }
        is AppRoute.Sheets.ShareApp -> NavEntry(key = key, metadata = key.metadata()) {
            ShareAppScreen()
        }
        is AppRoute.Sheets.Menu -> NavEntry(key = key, metadata = key.metadata()) {
            MenuScreen()
        }
        is AppRoute.Sheets.Lab -> NavEntry(key = key, metadata = key.metadata()) {
            StandaloneLabsScreen()
        }

        is AppRoute.Verification -> NavEntry(key = key, metadata = key.metadata()) {
            VerificationFlowScreen(
                origin = key.origin,
                target = key.target,
                includePhone = key.includePhone,
                includeEmail = key.includeEmail,
                emailAddress = key.email,
                emailVerificationCode = key.emailVerificationCode,
            )
        }

        is AppRoute.OnRamp.ProviderList -> NavEntry(key = key, metadata = key.metadata()) {
            remember { OnRampFlowTracker.start(key.from) }
            OnRampProviderListScreen(
                neededAmount = key.neededAmount?.quarks,
                neededCurrency = key.neededAmount?.currencyCode,
            )
        }
        is AppRoute.OnRamp.AmountEntry -> NavEntry(key = key, metadata = key.metadata()) {
            OnRampCustomAmountScreen()
        }

        is AppRoute.Menu.AppSettings -> NavEntry(key = key, metadata = key.metadata()) {
            AppSettingsScreen()
        }
        is AppRoute.Menu.Lab -> NavEntry(key = key, metadata = key.metadata()) {
            LabsScreen()
        }
        is AppRoute.Menu.MyAccount -> NavEntry(key = key, metadata = key.metadata()) {
            MyAccountScreen()
        }
        is AppRoute.Menu.Deposit -> NavEntry(key = key, metadata = key.metadata()) {
            DepositScreen(key.mint)
        }
        is AppRoute.Menu.BackupKey -> NavEntry(key = key, metadata = key.metadata()) {
            BackupKeyScreen()
        }
        is AppRoute.Menu.AdvancedFeatures -> NavEntry(key = key, metadata = key.metadata()) {
            AdvancedFeaturesScreen()
        }

        is AppRoute.Transfers.Withdrawal.Amount -> NavEntry(key = key, metadata = key.metadata()) {
            remember { WithdrawalFlow.start() }
            WithdrawalEntryScreen(key.mint)
        }
        is AppRoute.Transfers.Withdrawal.Destination -> NavEntry(key = key, metadata = key.metadata()) {
            WithdrawalDestinationScreen()
        }
        is AppRoute.Transfers.Withdrawal.Confirmation -> NavEntry(key = key, metadata = key.metadata()) {
            WithdrawalConfirmationScreen()
        }

        else -> error("Unknown route: $key")
    }
}

/**
 * Sheet entry with nested [AppNavHost] for inner-sheet navigation.
 * Uses slide transitions for intra-sheet navigation.
 */
private fun sheetEntry(
    key: AppRoute.Main.Sheet,
    resultStateRegistry: NavResultStateRegistry,
    barManager: BarManager,
): NavEntry<NavKey> {
    return NavEntry(key = key, metadata = key.metadata()) {
        val sheetDismissDispatcher = LocalBottomSheetDismissDispatcher.current
        val backStack = rememberNavBackStack(key.initialRoute)
        val navigator = rememberCodeNavigator(
            backStack = backStack,
            resultStateRegistry = resultStateRegistry,
            onRootReached = { sheetDismissDispatcher() },
        )

        val onBack = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            } else {
                sheetDismissDispatcher()
            }
        }

        CompositionLocalProvider(LocalCodeNavigator provides navigator) {
            AppNavHost(
                navigator = navigator,
                resultStateRegistry = resultStateRegistry,
                decorators = listOf(
                    rememberNavMessagingEntryDecorator(navigator.backStack, barManager)
                ),
                sceneStrategy = ModalBottomSheetSceneStrategy<NavKey>(navigator.resultStore) {
                    navigator.backStack.getOrNull(navigator.backStack.lastIndex - 1)
                } then SinglePaneSceneStrategy(),
                transitionSpec = {
                    slideInHorizontally(initialOffsetX = { it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { -it })
                },
                popTransitionSpec = {
                    slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                },
                predictivePopTransitionSpec = {
                    slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                },
                onBack = { onBack() },
                entryProvider = { innerKey ->
                    appEntryProvider(innerKey, resultStateRegistry, barManager, deepLink = { null })
                }
            )

            BackHandler { onBack() }
        }
    }
}
