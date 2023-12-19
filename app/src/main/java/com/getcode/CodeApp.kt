package com.getcode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.navigation
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.getcode.navigation.AccountModal
import com.getcode.navigation.BottomSheetNavigator
import com.getcode.navigation.CodeNavigator
import com.getcode.navigation.HomeScreen
import com.getcode.navigation.LocalCodeNavigator
import com.getcode.navigation.LoginScreen
import com.getcode.navigation.ModalRoot
import com.getcode.navigation.SheetSlideTransition
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.theme.LocalCodeColors
import com.getcode.view.SheetSections
import com.getcode.view.addSheetGraph
import com.getcode.view.components.AuthCheck
import com.getcode.view.components.BottomBarContainer
import com.getcode.view.components.CodeScaffold
import com.getcode.view.components.TitleBar
import com.getcode.view.components.TopBarContainer
import com.getcode.view.main.home.HomeViewModel

@Composable
fun CodeApp() {
    CodeTheme {
        AppNavHost {
            val appState = rememberCodeAppState()

            CodeScaffold(
                backgroundColor = Brand,
                scaffoldState = appState.scaffoldState
            ) { innerPaddingModifier ->
                Navigator(
                    screen = HomeScreen(),
                ) { navigator ->
//                    val codeNavigator = LocalCodeNavigator.current
//                    LaunchedEffect(navigator.lastItem) {
//                        // update global navigator for platform access to support push/pop from a single
//                        // navigator current
//                        codeNavigator.screensNavigator = navigator
//                    }

                    val onNavigateToLogin = {
                        appState.navigator.replaceAll(LoginScreen)
                    }
                    val onNavigateToHomeScan = {
//                            appState.navigator.push(MainSections.HOME.route)
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
                        SlideTransition(navigator = navigator)
                    }

                    TopBarContainer(appState)
                    BottomBarContainer(appState)

                    //Listen for authentication changes here
                    AuthCheck(
                        navigator = appState.navigator,
                        onNavigateToLogin = onNavigateToLogin,
                        onNavigateToHome = onNavigateToHomeScan
                    )
                }
            }
        }
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
            val navigator = remember(it) { CodeNavigator(it) }
            CompositionLocalProvider(LocalCodeNavigator provides navigator) {
                if (it.isVisible) {
                    SheetSlideTransition(navigator = navigator.sheetNavigator)
                } else {
                    CurrentScreen()
                }
            }
        }
    ) {
        val navigator = remember(it) { CodeNavigator(it) }
        CompositionLocalProvider(LocalCodeNavigator provides navigator) {
            content()
        }
    }
}

fun NavGraphBuilder.codeSheetNavGraph(
    navController: NavController,
    homeViewModel: HomeViewModel,
    onTitleChange: (Int?) -> Unit,
    onBackButtonVisibilityChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    navigation(
        route = MainDestinations.SHEET_GRAPH,
        startDestination = SheetSections.NONE.route
    ) {
        addSheetGraph(
            navController,
            homeViewModel,
            onTitleChange,
            onBackButtonVisibilityChange,
            onClose
        )
    }
}
