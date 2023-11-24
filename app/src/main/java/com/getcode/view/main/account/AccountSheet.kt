package com.getcode.view.main.account

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import com.getcode.MainDestinations
import com.getcode.codeSheetNavGraph
import com.getcode.rememberCodeAppState
import com.getcode.theme.sheetHeight
import com.getcode.view.SheetSections
import com.getcode.view.components.SheetTitle
import com.getcode.view.main.home.HomeViewModel


@Composable
fun AccountSheet(isVisible: Boolean = true,
                 homeViewModel: HomeViewModel,
                 onClose: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(sheetHeight)
    ) {
            SheetNavContainer(isVisible, homeViewModel, onClose)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SheetNavContainer(isVisible: Boolean = true, homeViewModel: HomeViewModel, onClose: () -> Unit = {}) {
    var title: Int? by remember { mutableStateOf(null) }
    var isBackButtonVisible: Boolean by remember { mutableStateOf(false) }
    val appState = rememberCodeAppState()

    SheetTitle(
        title = title?.let { stringResource(id = it) },
        backButton = isBackButtonVisible,
        closeButton = !isBackButtonVisible,
        onBackIconClicked = { appState.sheetNavController.popBackStack() },
        onCloseIconClicked = onClose
    )

    NavHost(
        modifier = Modifier
            .fillMaxSize(),
        navController = appState.sheetNavController,
        startDestination = MainDestinations.SHEET_GRAPH,
    ) {
        codeSheetNavGraph(
            navController = appState.sheetNavController,
            homeViewModel = homeViewModel,
            onTitleChange = { title = it },
            onBackButtonVisibilityChange = { isBackButtonVisible = it },
            onClose = onClose
        )
    }

    LaunchedEffect(isVisible) {
        val isHome =
            appState.sheetNavController.currentDestination?.route == SheetSections.HOME.route
        if (!isHome) {
            appState.sheetNavController.popBackStack()
            appState.sheetNavController.navigate(
                SheetSections.HOME.route,
                NavOptions.Builder().setPopUpTo(
                    SheetSections.NONE.route, inclusive = true, saveState = false
                ).build()
            )
        }
    }
}