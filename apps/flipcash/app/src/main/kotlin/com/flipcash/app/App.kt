package com.flipcash.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.transitions.CrossfadeTransition
import cafe.adriel.voyager.transitions.SlideTransition
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.router.LocalRouter
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.theme.FlipcashTheme
import com.flipcash.app.ui.navigation.AppScreenContent
import com.flipcash.app.ui.navigation.MainRoot
import com.flipcash.services.modals.ModalManager
import com.getcode.navigation.core.BottomSheetNavigator
import com.getcode.navigation.core.CombinedNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.navigation.transitions.SheetSlideTransition
import com.getcode.theme.LocalCodeColors
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.components.bars.BottomBarContainer
import com.getcode.ui.components.bars.TopBarContainer
import com.getcode.ui.components.bars.rememberBarManager
import com.getcode.ui.core.RestrictionType
import com.getcode.ui.decor.ScrimSupport
import com.getcode.ui.theme.CodeScaffold
import dev.bmcreations.tipkit.TipScaffold
import dev.bmcreations.tipkit.engines.TipsEngine
import dev.theolm.rinku.DeepLink
import dev.theolm.rinku.compose.ext.DeepLinkListener

@Composable
fun App(
    tipsEngine: TipsEngine,
) {
    val router = LocalRouter.currentOrThrow

    val viewModel = getActivityScopedViewModel<HomeViewModel>()

    // We are obtaining deep link here to handle a login request while already logged in to
    // present the option for the user to switch accounts
    var deepLink by remember { mutableStateOf<DeepLink?>(null) }
    var loginRequest by remember { mutableStateOf<String?>(null) }

    DeepLinkListener {
        val type = router.processType(it)
        if (type is DeeplinkType.Login) {
            loginRequest = type.entropy
            return@DeepLinkListener
        }
        deepLink = it
    }

    val userManager = LocalUserManager.currentOrThrow
    val session = LocalSessionController.currentOrThrow
    val userState by userManager.state.collectAsState()

    FlipcashTheme {
        val barManager = rememberBarManager()
        AppScreenContent {
            // TODO: create PaymentScaffold for flipcash
//            PaymentScaffold {
                TipScaffold(tipsEngine = tipsEngine) {
                    ScrimSupport {
                        AppNavHost {
                            val codeNavigator = LocalCodeNavigator.current
                            CodeScaffold { innerPaddingModifier ->
                                Navigator(
                                    screen = MainRoot { deepLink },
                                ) { navigator ->
                                    LaunchedEffect(navigator.lastItem) {
                                        // update global navigator for platform access to support push/pop from a single
                                        // navigator current
                                        codeNavigator.screensNavigator = navigator
                                    }

                                    Box(
                                        modifier = Modifier
                                            .padding(innerPaddingModifier)
                                    ) {

                                        when (navigator.lastEvent) {
                                            StackEvent.Push,
                                            StackEvent.Pop -> {
                                                when (navigator.lastItem) {
                                                    ScreenRegistry.get(NavScreenProvider.Login.SeedInput),
                                                    ScreenRegistry.get(NavScreenProvider.Permissions.Camera()),
                                                    is MainRoot -> {
                                                        CrossfadeTransition(navigator = navigator)
                                                    }

                                                    else -> SlideTransition(navigator = navigator)
                                                }
                                            }

                                            StackEvent.Idle,
                                            StackEvent.Replace -> CurrentScreen()
                                        }
                                    }

                                    LaunchedEffect(deepLink) {
                                        if (codeNavigator.lastItem !is MainRoot) {
                                            if (deepLink != null) {
                                                val screenSet = router.processDestination(deepLink)
                                                if (screenSet.isNotEmpty()) {
                                                    codeNavigator.replaceAll(screenSet)
                                                }
                                            }
                                        }
                                    }

                                    LaunchedEffect(loginRequest) {
                                        loginRequest?.let { entropy ->
                                            viewModel.handleLoginEntropy(
                                                entropy,
                                                onSwitchAccount = {
                                                    loginRequest = null
                                                    codeNavigator.replaceAll(
                                                        ScreenRegistry.get(
                                                            NavScreenProvider.Login.Home(
                                                                entropy
                                                            )
                                                        )
                                                    )
                                                },
                                                onCancel = {
                                                    loginRequest = null
                                                }
                                            )
                                        }
                                    }

                                    LaunchedEffect(userState.isTimelockUnlocked) {
                                        if (userState.isTimelockUnlocked) {
                                            codeNavigator.replaceAll(
                                                ScreenRegistry.get(
                                                    NavScreenProvider.AppRestricted(RestrictionType.TIMELOCK_UNLOCKED)
                                                )
                                            )
                                        }
                                    }

                                    OnLifecycleEvent { _, event ->
                                        when (event) {
                                            Lifecycle.Event.ON_RESUME -> {
                                                session.onAppInForeground()
                                            }

                                            Lifecycle.Event.ON_STOP,
                                            Lifecycle.Event.ON_DESTROY -> {
                                                session.onAppInBackground()
                                            }

                                            else -> Unit
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
//            }
        }
        TopBarContainer(barManager.barMessages)
        BottomBarContainer(barManager.barMessages)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AppNavHost(content: @Composable () -> Unit) {
    var combinedNavigator by remember {
        mutableStateOf<CombinedNavigator?>(null)
    }
    BottomSheetNavigator(
        modifier = Modifier.fillMaxSize(),
        sheetBackgroundColor = LocalCodeColors.current.background,
        sheetContentColor = LocalCodeColors.current.onBackground,
        sheetContent = { sheetNav ->
            if (combinedNavigator == null) {
                combinedNavigator = CombinedNavigator(sheetNav)
            }
            combinedNavigator?.let {
                CompositionLocalProvider(LocalCodeNavigator provides it) {
                    SheetSlideTransition(navigator = it)
                }
            }

        },
        onHide = ModalManager::clear
    ) { sheetNav ->
        if (combinedNavigator == null) {
            combinedNavigator = CombinedNavigator(sheetNav)
        }
        combinedNavigator?.let {
            CompositionLocalProvider(LocalCodeNavigator provides it) {
                content()
            }
        }
    }
}