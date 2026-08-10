package com.flipcash.app.lab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.lab.internal.LabsScreenContent
import com.flipcash.app.lab.internal.LabsScreenViewModel
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun LabsScreen(onboarding: Boolean = false) {
    val navigator = LocalCodeNavigator.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Sheet-aware app bar: a Close (✕) at the sheet root, a back arrow when pushed deeper.
        AppBarWithTitle(
            title = stringResource(R.string.title_betaFlags),
            titleAlignment = Alignment.CenterHorizontally,
            onBackIconClicked = { navigator.navigateBack() },
        )

        val viewModel = getActivityScopedViewModel<LabsScreenViewModel>()

        LabsScreenContent(viewModel, onboarding)
    }
}
