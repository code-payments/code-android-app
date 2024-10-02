package com.getcode

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransition
import cafe.adriel.voyager.transitions.ScreenTransitionContent
import cafe.adriel.voyager.transitions.SlideTransition
import com.getcode.manager.ModalManager
import com.getcode.navigation.core.BottomSheetNavigator
import com.getcode.navigation.core.CombinedNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.LoginScreen
import com.getcode.navigation.transitions.SheetSlideTransition
import com.getcode.theme.CodeTheme
import com.getcode.theme.LocalCodeColors
import com.getcode.ui.components.AuthCheck
import com.getcode.ui.components.bars.BottomBarContainer
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.components.ModalContainer
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.bars.TopBarContainer
import com.getcode.ui.modals.ConfirmationModals
import com.getcode.ui.utils.getActivity
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.ui.utils.measured
import com.getcode.ui.utils.rememberBiometricsState
import com.getcode.util.BiometricsError
import com.getcode.view.main.scanner.views.BiometricsBlockingView
import dev.bmcreations.tipkit.TipScaffold
import dev.bmcreations.tipkit.engines.TipsEngine

@Composable
fun CodeApp(tipsEngine: TipsEngine) {
    val tlvm = MainRoot.getActivityScopedViewModel<TopLevelViewModel>()
    val state by tlvm.state.collectAsState()
    val activity = LocalContext.current.getActivity()
    val biometricsState = rememberBiometricsState(
        requireBiometrics = state.requireBiometrics,
        onError = { error ->
            if (error == BiometricsError.NoBiometrics) {
                tlvm.onMissingBiometrics()
            }
        }
    )

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            tlvm.onResume()
        }
    }

    CodeTheme {
        val appState = rememberCodeAppState()
        CompositionLocalProvider(LocalBiometricsState provides biometricsState) {
            AppNavHost {
                val codeNavigator = LocalCodeNavigator.current
                TipScaffold(tipsEngine = tipsEngine) {
                    CodeScaffold(
                        scaffoldState = appState.scaffoldState
                    ) { innerPaddingModifier ->
                        Navigator(
                            screen = MainRoot,
                        ) { navigator ->
                            appState.navigator = codeNavigator

                            LaunchedEffect(navigator.lastItem) {
                                // update global navigator for platform access to support push/pop from a single
                                // navigator current
                                codeNavigator.screensNavigator = navigator
                            }

                            var topBarHeight by remember {
                                mutableStateOf(0.dp)
                            }

                            val (isVisibleTopBar, isVisibleBackButton) = appState.isVisibleTopBar
                            if (isVisibleTopBar && appState.currentTitle.isNotBlank()) {
                                AppBarWithTitle(
                                    modifier = Modifier.measured { topBarHeight = it.height },
                                    title = appState.currentTitle,
                                    backButton = isVisibleBackButton,
                                    onBackIconClicked = appState::upPress
                                )
                            } else {
                                topBarHeight = 0.dp
                            }

                            CompositionLocalProvider(
                                LocalTopBarPadding provides PaddingValues(top = topBarHeight),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(innerPaddingModifier)
                                ) {
                                    when (navigator.lastEvent) {
                                        StackEvent.Push,
                                        StackEvent.Pop -> {
                                            when (navigator.lastItem) {
                                                is LoginScreen, is MainRoot -> CrossfadeTransition(
                                                    navigator = navigator
                                                )

                                                else -> SlideTransition(navigator = navigator)
                                            }
                                        }

                                        StackEvent.Idle,
                                        StackEvent.Replace -> CurrentScreen()
                                    }
                                }
                            }

                            //Listen for authentication changes here
                            AuthCheck(
                                navigator = codeNavigator,
                                onNavigate = { screens ->
                                    codeNavigator.replaceAll(screens, inSheet = false)
                                },
                                onSwitchAccounts = { seed ->
                                    activity?.let {
                                        tlvm.logout(it) {
                                            appState.navigator.replaceAll(LoginScreen(seed))
                                        }
                                    }
                                }
                            )
                        }
                    }
                    ModalContainer(codeNavigator, appState)
                }
            }
        }
        BiometricsBlockingView(modifier = Modifier.fillMaxSize(), biometricsState)
        TopBarContainer(appState.barMessages)
        BottomBarContainer(appState.barMessages)
        ConfirmationModals(Modifier.fillMaxSize())
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
            combinedNavigator = combinedNavigator?.apply { sheetNavigator = sheetNav }
                ?: CombinedNavigator(sheetNav)
            combinedNavigator?.let {
                CompositionLocalProvider(LocalCodeNavigator provides it) {
                    SheetSlideTransition(navigator = it)
                }
            }

        },
        onHide = ModalManager::clear
    ) { sheetNav ->
        combinedNavigator =
            combinedNavigator?.apply { sheetNavigator = sheetNav } ?: CombinedNavigator(sheetNav)
        combinedNavigator?.let {
            CompositionLocalProvider(LocalCodeNavigator provides it) {
                content()
            }
        }
    }
}

@Composable
private fun CrossfadeTransition(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    content: ScreenTransitionContent = { it.Content() }
) {
    ScreenTransition(
        navigator = navigator,
        modifier = modifier,
        content = content,
        transition = { fadeIn() togetherWith fadeOut() }
    )
}
