package com.getcode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.getcode.navigation.BottomSheetNavigator
import com.getcode.navigation.CodeNavigator
import com.getcode.navigation.CombinedNavigator
import com.getcode.navigation.HomeScreen
import com.getcode.navigation.LocalCodeNavigator
import com.getcode.navigation.LoginScreen
import com.getcode.navigation.MainRoot
import com.getcode.navigation.SheetSlideTransition
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.theme.LocalCodeColors
import com.getcode.util.LocalDeeplinks
import com.getcode.util.RepeatOnLifecycle
import com.getcode.view.components.AuthCheck
import com.getcode.view.components.BottomBarContainer
import com.getcode.view.components.CodeScaffold
import com.getcode.view.components.TitleBar
import com.getcode.view.components.TopBarContainer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach

@Composable
fun CodeApp() {
    CodeTheme {
        val appState = rememberCodeAppState()
        AppNavHost {
            CodeScaffold(
                backgroundColor = Brand,
                scaffoldState = appState.scaffoldState
            ) { innerPaddingModifier ->
                Navigator(
                    screen = MainRoot,
                ) { navigator ->
                    val codeNavigator = LocalCodeNavigator.current
                    LaunchedEffect(navigator.lastItem) {
                        // update global navigator for platform access to support push/pop from a single
                        // navigator current
                        codeNavigator.screensNavigator = navigator
                    }

                    val (isVisibleTopBar, isVisibleBackButton) = appState.isVisibleTopBar
                    if (isVisibleTopBar && appState.currentTitle.isNotBlank()) {
                        TitleBar(
                            title = appState.currentTitle,
                            backButton = isVisibleBackButton,
                            onBackIconClicked = appState::upPress
                        )
                    }

                    Box(modifier = Modifier.padding(innerPaddingModifier)) {
                        if (navigator.lastItem !is MainRoot) {
                            SlideTransition(navigator = navigator)
                        } else {
                            CurrentScreen()
                        }
                    }

                    //Listen for authentication changes here
                    AuthCheck(
                        navigator = appState.navigator,
                        onNavigate = {
                            navigator.replaceAll(it)
                        }
                    )
                }
            }
        }

        TopBarContainer(appState)
        BottomBarContainer(appState)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AppNavHost(content: @Composable () -> Unit) {
    BottomSheetNavigator(
        modifier = Modifier.fillMaxSize(),
        sheetBackgroundColor = LocalCodeColors.current.background,
        sheetContentColor = LocalCodeColors.current.onBackground,
        sheetContent = {
            val navigator = remember(it) { CombinedNavigator(it) }
            CompositionLocalProvider(LocalCodeNavigator provides navigator) {
                if (it.isVisible) {
                    SheetSlideTransition(navigator = navigator.sheetNavigator)
                } else {
                    CurrentScreen()
                }
            }
        }
    ) {
        val navigator = remember(it) { CombinedNavigator(it) }
        CompositionLocalProvider(LocalCodeNavigator provides navigator) {
            content()
        }
    }
}