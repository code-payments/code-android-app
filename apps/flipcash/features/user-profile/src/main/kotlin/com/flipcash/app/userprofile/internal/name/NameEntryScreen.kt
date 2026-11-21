package com.flipcash.app.userprofile.internal.name

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.DisplayNameSource
import com.flipcash.app.core.ui.DisplayTextInput
import com.flipcash.app.core.ui.transitions.SharedTransition
import com.flipcash.app.core.ui.transitions.sharedElementTransition
import com.flipcash.app.core.userprofile.UpdateProfileResult
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.app.core.ui.transitions.RequestFocusWhenSettled
import com.flipcash.core.R
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.flipcash.app.core.ui.OnboardingHeadline
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun NameEntryScreen(
    source: DisplayNameSource,
    allowBack: Boolean = true,
    // Group Money onboarding: the "Your name" headline, with the line under it instead of under the field.
    forGroups: Boolean = false,
) {
    val flowNavigator = rememberFlowNavigator<UpdateProfileStep, UpdateProfileResult>()

    val viewModel = hiltViewModel<NameEntryViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    val keyboard = rememberKeyboardController()

    // In onboarding this is a mandatory step, so hide the back affordance and swallow system back.
    BackHandler(enabled = !allowBack) { /* no-op: cannot back out of the name step */ }

    Column {
        if (allowBack) {
            AppBarWithTitle(
                onBackIconClicked = {
                    keyboard.hideIfVisible {
                        // Leaving throws the edit away rather than carrying it back in. A gesture
                        // back lands in the same place by a different route: the step's ViewModel
                        // is scoped to its nav entry, so popping the entry drops the field with it.
                        viewModel.dispatchEvent(NameEntryViewModel.Event.DiscardChanges)
                        flowNavigator.back()
                    }
                },
            )
        } else if (forGroups) {
            // The same empty bar the phone and email steps keep, so the headline sits at their height.
            AppBarWithTitle()
        } else {
            Spacer(Modifier.statusBarsPadding().padding(2.5.dp))
        }
        NameEntryScreenContent(state, source, forGroups, viewModel::dispatchEvent)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<NameEntryViewModel.Event.OnNameApproved>()
            .onEach { flowNavigator.proceed() }
            .launchIn(this)
    }
}

@Composable
private fun NameEntryScreenContent(
    state: NameEntryViewModel.State,
    source: DisplayNameSource,
    forGroups: Boolean,
    dispatchEvent: (NameEntryViewModel.Event) -> Unit,
) {
    val keyboard = rememberKeyboardController()
    CodeScaffold(
        modifier = Modifier
            .padding(horizontal = CodeTheme.dimens.inset),
        topBar = {
            if (forGroups) {
                OnboardingHeadline(
                    // The gap OnboardingStepLayout leaves above the headline on the other steps.
                    modifier = Modifier.padding(
                        top = CodeTheme.dimens.grid.x3,
                        bottom = CodeTheme.dimens.inset
                    ),
                    title = stringResource(R.string.title_onboardingName),
                    subtitle = stringResource(R.string.subtitle_onboardingName),
                )
                return@CodeScaffold
            }
            Text(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(
                        top = CodeTheme.dimens.grid.x2,
                        bottom = CodeTheme.dimens.inset
                    ),
                text = stringResource(R.string.title_profileNameSelection),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
            )
        },
        bottomBar = {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        top = CodeTheme.dimens.grid.x6,
                        bottom = CodeTheme.dimens.grid.x3
                    ).imePadding(),
                text = stringResource(R.string.action_next),
                enabled = state.isSubmittable(source) && state.isChanged && state.processingState.isIdle,
                isLoading = state.processingState.loading,
                isSuccess = state.processingState.success,
                onClick = {
                    keyboard.hideIfVisible {
                        dispatchEvent(NameEntryViewModel.Event.ConfirmNameChange(source))
                    }
                },
            )
        }
    ) { padding ->
        val focusRequester = remember { FocusRequester() }
        // Apply the scaffold's content padding to the wrapper, not the field —
        // padding on the field itself inflates its box and pushes the sublabel
        // far below the entered text instead of letting it sit just underneath.
        Column(modifier = Modifier.padding(padding)) {
            DisplayTextInput(
                state = state.nameFieldState,
                placeholder = stringResource(R.string.hint_profileName),
                sublabel = if (forGroups) null else stringResource(R.string.subtitle_profileNameSelection),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textModifier = Modifier.sharedElementTransition(
                    transition = SharedTransition.CurrencyName,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                onKeyboardAction = {
                    keyboard.hideIfVisible {
                        dispatchEvent(NameEntryViewModel.Event.ConfirmNameChange(source))
                    }
                },
            )
        }

        // In Group Money onboarding the email step hands over with the keyboard up; take focus as
        // the step arrives so the keyboard changes type rather than dropping.
        RequestFocusWhenSettled(focusRequester, immediatelyIfImeVisible = forGroups)
    }
}