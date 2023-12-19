package com.getcode.view.main.account

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import com.getcode.MainDestinations
import com.getcode.codeSheetNavGraph
import com.getcode.navigation.LocalCodeNavigator
import com.getcode.rememberCodeAppState
import com.getcode.theme.sheetHeight
import com.getcode.view.SheetSections
import com.getcode.view.components.SheetTitle
import com.getcode.view.main.home.HomeViewModel


//@Composable
//fun AccountSheet(
//) {
//    val navigator = LocalCodeNavigator.current
//    Column(
//        modifier = Modifier
//            .fillMaxWidth()
//            .fillMaxHeight(sheetHeight)
//    ) {
//        AccountHome(viewModel = ) {
//
//        }
//    }
//}

@Composable
fun SheetNavContainer(
    homeViewModel: HomeViewModel,
    onClose: () -> Unit = {}
) {
    var isBackButtonVisible: Boolean by remember { mutableStateOf(false) }

//    NavHost(
//        modifier = Modifier
//            .fillMaxSize(),
//        navController = appState.sheetNavController,
//        startDestination = MainDestinations.SHEET_GRAPH,
//    ) {
//        codeSheetNavGraph(
//            navController = appState.sheetNavController,
//            homeViewModel = homeViewModel,
//            onTitleChange = { title = it },
//            onBackButtonVisibilityChange = { isBackButtonVisible = it },
//            onClose = onClose
//        )
//    }

//    LaunchedEffect(isVisible) {
//        val isHome =
//            appState.sheetNavController.currentDestination?.route == SheetSections.HOME.route
//        if (!isHome) {
//            appState.sheetNavController.popBackStack()
//            appState.sheetNavController.navigate(
//                SheetSections.HOME.route,
//                NavOptions.Builder().setPopUpTo(
//                    SheetSections.NONE.route, inclusive = true, saveState = false
//                ).build()
//            )
//        }
//    }
}