package com.flipcash.app.lab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.lab.internal.LabsScreenContent
import com.flipcash.app.lab.internal.LabsScreenViewModel
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun LabsScreen() {
    val navigator = LocalCodeNavigator.current
    val isSheetRoot = remember(navigator) { navigator.backStack.size <= 1 }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isSheetRoot) {
            AppBarWithTitle(
                title = stringResource(R.string.title_betaFlags),
                titleAlignment = Alignment.CenterHorizontally,
                isInModal = true,
                endContent = {
                    AppBarDefaults.Close { navigator.hide() }
                }
            )
        } else {
            AppBarWithTitle(
                title = stringResource(R.string.title_betaFlags),
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                isInModal = true,
                onBackIconClicked = navigator::pop
            )
        }

        val viewModel = getActivityScopedViewModel<LabsScreenViewModel>()

        LabsScreenContent(viewModel)
    }
}
