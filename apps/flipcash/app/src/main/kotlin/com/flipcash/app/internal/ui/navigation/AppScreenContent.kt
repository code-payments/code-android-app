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
import com.flipcash.app.currencycreator.CurrencyCreatorFlowScreen
import com.flipcash.app.core.AppRoute
import com.flipcash.app.currency.RegionSelectionScreen
import com.flipcash.app.deposit.DepositFlowScreen
import com.flipcash.app.directsend.SendFlowScreen
import com.flipcash.app.invite.InviteContactScreen
import com.flipcash.app.messenger.MessengerScreen
import com.flipcash.app.messenger.ChatAmountEntryScreen
import com.flipcash.app.discovery.TokenDiscoveryScreen
import com.flipcash.app.internal.ui.navigation.decorators.rememberNavMessagingEntryDecorator
import com.flipcash.app.lab.LabsScreen
import com.flipcash.app.lab.NavBarSettingsScreen
import com.flipcash.app.login.OnboardingFlowScreen
import com.flipcash.app.menu.MenuScreen
import com.flipcash.app.myaccount.UserProfileScreen
import com.flipcash.app.myaccount.MyAccountScreen
import com.flipcash.app.scanner.ScannerScreen
import com.flipcash.app.shareapp.ShareAppScreen
import com.flipcash.app.tokens.SwapFlowScreen
import com.flipcash.app.tokens.TokenInfoScreen
import com.flipcash.app.tokens.TokenSelectScreen
import com.flipcash.app.tokens.TokenTxProcessingScreen
import com.flipcash.app.transactions.TransactionHistoryScreen
import com.flipcash.app.userflags.UserFlagsScreen
import com.flipcash.app.withdrawal.WithdrawalFlowScreen
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

    // Onboarding flow
    annotatedEntry<AppRoute.OnboardingFlow> { key ->
        OnboardingFlowScreen(route = key, resultStateRegistry = resultStateRegistry)
    }

    // Main
    annotatedEntry<AppRoute.Main.Sheet> { key ->
        SheetContent(key, resultStateRegistry, barManager)
    }
    annotatedEntry<AppRoute.Main.AppRestricted> { key -> AppRestrictedScreen(key.restrictionType) }
    annotatedEntry<AppRoute.Main.Scanner> { ScannerScreen() }
    annotatedEntry<AppRoute.Main.RegionSelection> { RegionSelectionScreen() }
    annotatedEntry<AppRoute.Main.InviteContact> { key -> InviteContactScreen(key.phoneNumber) }

    // Sheets (inner content — wrapped in Main.Sheet by navigateTo())
    annotatedEntry<AppRoute.Sheets.Give> { key -> CashScreen(key.mint, key.fromTokenInfo) }
    annotatedEntry<AppRoute.Sheets.Send> { SendFlowScreen(resultStateRegistry = resultStateRegistry) }
    annotatedEntry<AppRoute.Sheets.TokenSelection> { key -> TokenSelectScreen(key.purpose) }
    annotatedEntry<AppRoute.Sheets.Wallet> { BalanceScreen() }
    annotatedEntry<AppRoute.Sheets.ShareApp> { ShareAppScreen() }
    annotatedEntry<AppRoute.Sheets.Menu> { MenuScreen() }

    // Messaging
    annotatedEntry<AppRoute.Messaging.Chat> { key ->
        MessengerScreen(key.e164, key.displayName)
    }
    annotatedEntry<AppRoute.Messaging.AmountEntry> { key ->
        ChatAmountEntryScreen(key.e164, key.displayName)
    }

    // Tokens
    annotatedEntry<AppRoute.Token.Info> { key ->
        TokenInfoScreen(key.mint, key.shortfall, key.fromDeeplink)
    }
    annotatedEntry<AppRoute.Token.Transactions> { key -> TransactionHistoryScreen(key.mint) }
    annotatedEntry<AppRoute.Token.Swap> { key ->
        SwapFlowScreen(route = key, resultStateRegistry = resultStateRegistry)
    }
    // TODO: fold this into above entry
    annotatedEntry<AppRoute.Token.TxProcessing> { key ->
        TokenTxProcessingScreen(key.swapId, key.swapPurpose, key.amount, key.isFundingShortfall)
    }
    annotatedEntry<AppRoute.Token.Discovery> { TokenDiscoveryScreen() }
    annotatedEntry<AppRoute.Token.CurrencyCreator> { key ->
        CurrencyCreatorFlowScreen(route = key, resultStateRegistry = resultStateRegistry)
    }

    // Verification
    annotatedEntry<AppRoute.Verification> { key ->
        VerificationFlowScreen(route = key, resultStateRegistry = resultStateRegistry)
    }

    // Menu
    annotatedEntry<AppRoute.Menu.AppSettings> { AppSettingsScreen() }
    annotatedEntry<AppRoute.Menu.Lab> { LabsScreen() }
    annotatedEntry<AppRoute.Menu.NavBarSettings> { NavBarSettingsScreen() }
    annotatedEntry<AppRoute.Menu.UserProfile> { UserProfileScreen() }
    annotatedEntry<AppRoute.Menu.MyAccount> { MyAccountScreen() }
    annotatedEntry<AppRoute.Menu.BackupKey> { BackupKeyScreen() }
    annotatedEntry<AppRoute.Menu.AdvancedFeatures> { AdvancedFeaturesScreen() }
    annotatedEntry<AppRoute.Menu.DeviceLogs> { DeviceLogsScreen() }

    annotatedEntry<AppRoute.UserFlags> { UserFlagsScreen() }

    // Transfers
    annotatedEntry<AppRoute.Transfers.Deposit> { key ->
        DepositFlowScreen(route = key, resultStateRegistry = resultStateRegistry)
    }
    annotatedEntry<AppRoute.Transfers.Withdrawal> { key ->
        WithdrawalFlowScreen(route = key, resultStateRegistry = resultStateRegistry)
    }
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
            sceneStrategies = listOf(
                ModalBottomSheetSceneStrategy(navigator.resultStore) {
                    navigator.backStack.getOrNull(navigator.backStack.lastIndex - 1)
                },
                SinglePaneSceneStrategy(),
            ),
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
