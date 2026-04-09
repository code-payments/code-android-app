package com.flipcash.app.internal.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.flipcash.app.advanced.AdvancedFeaturesScreen
import com.flipcash.app.appsettings.AppSettingsScreen
import com.flipcash.app.devicelogs.DeviceLogsScreen
import com.flipcash.app.backupkey.BackupKeyScreen
import com.flipcash.app.balance.BalanceScreen
import com.flipcash.app.cash.CashScreen
import com.flipcash.app.contact.verification.VerificationFlowScreen
import com.flipcash.app.core.AppRoute
import com.flipcash.app.currency.RegionSelectionScreen
import com.flipcash.app.deposit.DepositScreen
import com.flipcash.app.discovery.TokenDiscoveryScreen
import com.flipcash.app.internal.ui.navigation.decorators.rememberNavMessagingEntryDecorator
import com.flipcash.app.lab.LabsScreen
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
import com.flipcash.app.permissions.NotificationPermissionRationaleScreen
import com.flipcash.app.permissions.NotificationPermissionScreen
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
import com.flipcash.app.userflags.UserFlagsScreen
import com.flipcash.app.withdrawal.WithdrawalConfirmationScreen
import com.flipcash.app.withdrawal.WithdrawalDestinationScreen
import com.flipcash.app.withdrawal.WithdrawalEntryScreen
import com.flipcash.app.withdrawal.WithdrawalFlow
import com.getcode.navigation.AppNavHost
import com.getcode.navigation.NonDismissableRoute
import com.getcode.navigation.NonDraggableRoute
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.core.rememberCodeNavigator
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher
import com.getcode.navigation.scenes.LocalSheetNavigator
import com.getcode.navigation.scenes.ModalBottomSheetSceneStrategy
import com.getcode.ui.components.bars.BarManager
import dev.theolm.rinku.DeepLink

fun appEntryProvider(
    resultStateRegistry: NavResultStateRegistry,
    barManager: BarManager,
    deepLink: () -> DeepLink?,
): (NavKey) -> NavEntry<NavKey> = entryProvider {

    // Loading / splash
    annotatedEntry<AppRoute.Loading> { MainRoot(deepLink) }

    // Onboarding
    annotatedEntry<AppRoute.Onboarding.Login> { key -> LoginRouter(key.seed, key.fromDeeplink) }
    annotatedEntry<AppRoute.Onboarding.SeedInput> { SeedInputScreen() }
    annotatedEntry<AppRoute.Onboarding.AccessKey> { AccessKeyScreen() }
    annotatedEntry<AppRoute.Onboarding.AccessKeySavedLocation> { PhotoAccessKeyScreen() }
    annotatedEntry<AppRoute.Onboarding.Purchase> { key -> PurchaseAccountScreen(key.fromLogin) }
    annotatedEntry<AppRoute.Onboarding.NotificationPermission> { key -> NotificationPermissionScreen(key.postCreate) }
    annotatedEntry<AppRoute.Onboarding.NotificationPermissionRationale> { key -> NotificationPermissionRationaleScreen(key.permanentlyDenied) }
    annotatedEntry<AppRoute.Onboarding.CameraPermission> { }

    // Main
    annotatedEntry<AppRoute.Main.Sheet> { key ->
        SheetContent(key, resultStateRegistry, barManager)
    }
    annotatedEntry<AppRoute.Main.AppRestricted> { key -> AppRestrictedScreen(key.restrictionType) }
    annotatedEntry<AppRoute.Main.Scanner> { ScannerScreen() }
    annotatedEntry<AppRoute.Main.RegionSelection> { key -> RegionSelectionScreen(key.kind) }

    // Sheets (inner content — wrapped in Main.Sheet by navigateTo())
    annotatedEntry<AppRoute.Sheets.Give> { key -> CashScreen(key.mint, key.fromTokenInfo) }
    annotatedEntry<AppRoute.Sheets.TokenSelection> { key -> TokenSelectScreen(key.purpose) }
    annotatedEntry<AppRoute.Sheets.Wallet> { BalanceScreen() }
    annotatedEntry<AppRoute.Sheets.ShareApp> { ShareAppScreen() }
    annotatedEntry<AppRoute.Sheets.Menu> { MenuScreen() }
    annotatedEntry<AppRoute.Sheets.Lab> { StandaloneLabsScreen() }

    // Tokens
    annotatedEntry<AppRoute.Token.Info> { key ->
        TokenInfoScreen(key.mint, key.forNeededFunds, key.fromDeeplink)
    }
    annotatedEntry<AppRoute.Token.Transactions> { key -> TransactionHistoryScreen(key.mint) }
    annotatedEntry<AppRoute.Token.SwapTransact> { key ->
        remember { BuySellFlow.start(key.forNeededFunds) }
        TokenBuySellEntryScreen(key.purpose)
    }
    annotatedEntry<AppRoute.Token.TxProcessing> { key ->
        TokenTxProcessingScreen(key.swapId, key.awaitExternalWallet)
    }
    annotatedEntry<AppRoute.Token.SellReceipt> { TokenSellReceiptScreen() }
    annotatedEntry<AppRoute.Token.Discovery> { TokenDiscoveryScreen() }

    // Verification
    annotatedEntry<AppRoute.Verification> { key ->
        VerificationFlowScreen(
            origin = key.origin,
            target = key.target,
            includePhone = key.includePhone,
            includeEmail = key.includeEmail,
            emailAddress = key.email,
            emailVerificationCode = key.emailVerificationCode,
        )
    }

    // OnRamp
    annotatedEntry<AppRoute.OnRamp.ProviderList> { key ->
        remember { OnRampFlowTracker.start(key.from) }
        OnRampProviderListScreen(
            neededAmount = key.neededAmount?.quarks,
            neededCurrency = key.neededAmount?.currencyCode,
        )
    }
    annotatedEntry<AppRoute.OnRamp.AmountEntry> { key -> OnRampCustomAmountScreen(key.mint) }

    // Menu
    annotatedEntry<AppRoute.Menu.AppSettings> { AppSettingsScreen() }
    annotatedEntry<AppRoute.Menu.Lab> { LabsScreen() }
    annotatedEntry<AppRoute.Menu.MyAccount> { MyAccountScreen() }
    annotatedEntry<AppRoute.Menu.Deposit> { key -> DepositScreen(key.mint) }
    annotatedEntry<AppRoute.Menu.BackupKey> { BackupKeyScreen() }
    annotatedEntry<AppRoute.Menu.AdvancedFeatures> { AdvancedFeaturesScreen() }
    annotatedEntry<AppRoute.Menu.DeviceLogs> { DeviceLogsScreen() }

    annotatedEntry<AppRoute.UserFlags> { UserFlagsScreen() }
    // Transfers
    annotatedEntry<AppRoute.Transfers.Withdrawal.Amount> { key ->
        remember { WithdrawalFlow.start() }
        WithdrawalEntryScreen(key.mint)
    }
    annotatedEntry<AppRoute.Transfers.Withdrawal.Destination> { WithdrawalDestinationScreen() }
    annotatedEntry<AppRoute.Transfers.Withdrawal.Confirmation> { WithdrawalConfirmationScreen() }
}

/**
 * Sheet content with nested [AppNavHost] for inner-sheet navigation.
 * Uses slide transitions for intra-sheet navigation.
 */
@Composable
private fun SheetContent(
    key: AppRoute.Main.Sheet,
    resultStateRegistry: NavResultStateRegistry,
    barManager: BarManager,
) {
    val sheetDismissDispatcher = LocalBottomSheetDismissDispatcher.current
    // Seed the backstack with initialRoute + innerRoutes so the sheet
    // appears already on the final destination (no visible push transition).
    val backStack = remember {
        NavBackStack<NavKey>(key.initialRoute).apply {
            key.innerRoutes.forEach { add(it) }
        }
    }
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

    // Toggle the outer sheet's drag/dismiss behavior based on the current inner route.
    val sheetNavigator = LocalSheetNavigator.current
    val currentInnerRoute by remember {
        derivedStateOf { backStack.lastOrNull() }
    }
    if (sheetNavigator != null) {
        val isDragDisabled = currentInnerRoute is NonDraggableRoute
        val isDismissDisabled = currentInnerRoute is NonDismissableRoute
        DisposableEffect(isDragDisabled, isDismissDisabled) {
            sheetNavigator.sheetDragDisabled = isDragDisabled
            sheetNavigator.sheetDismissDisabled = isDismissDisabled
            onDispose {
                sheetNavigator.sheetDragDisabled = false
                sheetNavigator.sheetDismissDisabled = false
            }
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
                if (targetState is OverlayScene<*> || initialState is OverlayScene<*>) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    slideInHorizontally(initialOffsetX = { it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { -it })
                }
            },
            popTransitionSpec = {
                if (targetState is OverlayScene<*> || initialState is OverlayScene<*>) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                }
            },
            predictivePopTransitionSpec = {
                if (targetState is OverlayScene<*> || initialState is OverlayScene<*>) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                }
            },
            onBack = { onBack() },
            entryProvider = appEntryProvider(resultStateRegistry, barManager, deepLink = { null }),
        )

        BackHandler { onBack() }
    }
}
