package com.flipcash.app.tipping

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.core.MinimumTipSource
import com.flipcash.app.tipping.internal.SetMinimumTipViewModel
import com.flipcash.features.tipping.R
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Minimum-tip entry (nodes 9541:10951, 9553:113170). Leaving without saving discards the entry —
 * there is no draft to keep, so a changed-but-abandoned amount just doesn't reach the profile.
 */
@Composable
fun SetMinimumTipScreen(source: MinimumTipSource) {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<SetMinimumTipViewModel>()

    LaunchedEffect(viewModel, source) { viewModel.onSourceResolved(source) }

    AmountEntryScreen(
        controller = viewModel.amountDelegate,
        onConfirm = { viewModel.dispatchEvent(SetMinimumTipViewModel.Event.ConfirmRequested) },
        largeHeader = true,
        appBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_minimumTipEntry),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = { navigator.pop() },
            )
        },
    )

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SetMinimumTipViewModel.Event.Saved>()
            .onEach { navigator.pop() }
            .launchIn(this)
    }
}
