package com.getcode.view.main.getKin

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavOptions
import com.getcode.MainDestinations
import com.getcode.codeSheetNavGraph
import com.getcode.rememberCodeAppState
import com.getcode.theme.sheetHeight
import com.getcode.view.SheetSections
import com.getcode.view.components.SheetTitle
import com.getcode.view.main.home.HomeViewModel
import androidx.navigation.compose.NavHost


@Composable
fun GetKinSheet(
    isVisible: Boolean = true,
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
            .fillMaxWidth()
            .fillMaxHeight(sheetHeight),
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
            appState.sheetNavController.currentDestination?.route == SheetSections.GET_KIN.route
        if (!isHome) {
            appState.sheetNavController.popBackStack()
            appState.sheetNavController.navigate(
                SheetSections.GET_KIN.route,
                NavOptions.Builder().setPopUpTo(
                    SheetSections.NONE.route, inclusive = true, saveState = false
                ).build()
            )
        }
    }
}