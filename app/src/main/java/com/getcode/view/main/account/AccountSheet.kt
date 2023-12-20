package com.getcode.view.main.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import com.getcode.MainDestinations
import com.getcode.analytics.AnalyticsScreenWatcher
import com.getcode.codeSheetNavGraph
import com.getcode.manager.AnalyticsManager
import com.getcode.rememberCodeAppState
import com.getcode.theme.sheetHeight
import com.getcode.util.RepeatOnLifecycle
import com.getcode.view.SheetSections
import com.getcode.view.components.SheetTitle
import com.getcode.view.main.home.HomeViewModel


@Composable
fun AccountSheet(
    homeViewModel: HomeViewModel,
    onClose: () -> Unit = {}
) {
    AnalyticsScreenWatcher(
        lifecycleOwner = LocalLifecycleOwner.current,
        event = AnalyticsManager.Screen.Settings
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(sheetHeight)
    ) {
        SheetNavContainer(homeViewModel, onClose)
    }
}

@Composable
fun SheetNavContainer(
    homeViewModel: HomeViewModel,
    onClose: () -> Unit = {}
) {
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

    RepeatOnLifecycle(targetState = Lifecycle.State.RESUMED) {
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