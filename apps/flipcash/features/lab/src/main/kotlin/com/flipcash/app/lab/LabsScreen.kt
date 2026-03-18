package com.flipcash.app.lab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.lab.internal.LabsScreenContent
import com.flipcash.app.lab.internal.LabsScreenViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun LabsScreen() {
    val navigator = LocalCodeNavigator.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_betaFlags),
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            isInModal = true,
            onBackIconClicked = navigator::pop
        )

        val viewModel = getActivityScopedViewModel<LabsScreenViewModel>()

        LabsScreenContent(viewModel)
    }
}

@Composable
fun StandaloneLabsScreen() {
    val navigator = LocalCodeNavigator.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_betaFlags),
            titleAlignment = Alignment.CenterHorizontally,
            isInModal = true,
            endContent = {
                AppBarDefaults.Close { navigator.hide() }
            }
        )

        val viewModel = getActivityScopedViewModel<LabsScreenViewModel>()

        LabsScreenContent(viewModel)
    }
}

@Composable
fun PreloadLabs() {
    val viewModel = getActivityScopedViewModel<LabsScreenViewModel>()
}
