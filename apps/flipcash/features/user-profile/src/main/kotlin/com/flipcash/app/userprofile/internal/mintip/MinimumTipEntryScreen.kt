package com.flipcash.app.userprofile.internal.mintip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flipcash.app.core.userprofile.UpdateProfileResult
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.core.R
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Minimum-tip entry. Leaving without saving discards the entry — there is no draft to keep, so a
 * changed-but-abandoned amount just doesn't reach the profile.
 *
 * @param isLastStep whether the flow ends here, which is the only thing that decides between
 * "Save" and "Next".
 */
@Composable
internal fun MinimumTipEntryScreen(isLastStep: Boolean) {
    val flowNavigator = rememberFlowNavigator<UpdateProfileStep, UpdateProfileResult>()
    val viewModel = hiltViewModel<MinimumTipEntryViewModel>()

    LaunchedEffect(viewModel, isLastStep) { viewModel.onPositionResolved(isLastStep) }

    AmountEntryScreen(
        controller = viewModel.amountDelegate,
        onConfirm = { viewModel.dispatchEvent(MinimumTipEntryViewModel.Event.ConfirmRequested) },
        largeHeader = true,
        appBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_minimumTipEntry),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = { flowNavigator.back() },
            )
        },
    )

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<MinimumTipEntryViewModel.Event.Saved>()
            .onEach { flowNavigator.proceed() }
            .launchIn(this)
    }
}
