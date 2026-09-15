package com.flipcash.app.userprofile.internal.mintip

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flipcash.app.core.userprofile.UpdateProfileResult
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.core.R
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Minimum-to-chat entry — the fee another user has to pay to open a DM. Leaving without saving
 * discards the entry: there is no draft to keep, so a changed-but-abandoned amount just doesn't
 * reach the profile.
 *
 * Node 10074:18892. The screen carries no title; the description sitting directly under the amount
 * says what the number is for, which a two-word app-bar title could only repeat.
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
            AppBarWithTitle(onBackIconClicked = { flowNavigator.back() })
        },
        headerCaption = {
            Text(
                text = stringResource(R.string.description_minimumToChat),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Start,
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
