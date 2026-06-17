package com.flipcash.app.currency

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.currency.internal.RegionSelectionViewModel
import com.flipcash.app.currency.internal.RegionSelectionScreen
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun RegionSelectionScreen() {
    val navigator = LocalCodeNavigator.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_selectRegion),
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = {
                navigator.pop()
            }
        )

        val viewModel = hiltViewModel<RegionSelectionViewModel>()
        RegionSelectionScreen(viewModel)
    }
}
