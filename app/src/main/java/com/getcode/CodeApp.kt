package com.getcode

import android.content.Intent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.*
import androidx.navigation.compose.NavHost
import com.getcode.manager.SessionManager
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.view.*
import com.getcode.view.components.*
import com.getcode.view.main.home.HomeViewModel
import timber.log.Timber

@Composable
fun CodeApp(appState: CodeAppState) {
    CodeTheme {
        CodeScaffold(
            backgroundColor = Brand,
            scaffoldState = appState.scaffoldState
        ) { innerPaddingModifier ->
            val onNavigateToLogin = {
                appState.navController.navigate(LoginSections.LOGIN.route) {
                    popUpTo(LoginSections.LOGIN.route) {
                        inclusive = true
                    }
                }
            }
            val onNavigateToHomeScan = { appState.navController.navigate(MainSections.HOME.route) }

            val (isVisibleTopBar, isVisibleBackButton) = appState.isVisibleTopBar
            if (isVisibleTopBar && appState.currentTitle.isNotBlank()) {
                TitleBar(
                    title = appState.currentTitle,
                    backButton = isVisibleBackButton,
                    onBackIconClicked = appState::upPress
                )
            }

            NavHost(
                navController = appState.navController,
                startDestination = MainDestinations.MAIN_GRAPH,
                modifier = Modifier.padding(innerPaddingModifier)
            ) {
                //Start destination should be the launch screen to await authentication
                navigation(
                    route = MainDestinations.MAIN_GRAPH,
                    startDestination = MainSections.LAUNCH.route
                ) {
                    addLoginGraph(appState.navController, appState::upPress)
                    addMainGraph()
                }
            }

            //Listen for authentication changes here
            authCheck(
                navController = appState.navController,
                onNavigateToLogin = onNavigateToLogin,
                onNavigateToHome = onNavigateToHomeScan
            )

            TopBarContainer(appState)
            BottomBarContainer(appState)
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
        addSheetGraph(navController, homeViewModel, onTitleChange, onBackButtonVisibilityChange, onClose)
    }
}
