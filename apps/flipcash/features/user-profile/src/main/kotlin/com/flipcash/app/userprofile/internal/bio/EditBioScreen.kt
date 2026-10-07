package com.flipcash.app.userprofile.internal.bio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.ui.DisplayTextInput
import com.flipcash.app.core.userprofile.UpdateProfileResult
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.app.core.ui.transitions.RequestFocusWhenSettled
import com.flipcash.core.R
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun EditBioScreen() {
    val flowNavigator = rememberFlowNavigator<UpdateProfileStep, UpdateProfileResult>()

    val viewModel = hiltViewModel<EditBioViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    val keyboard = rememberKeyboardController()

    Column {
        AppBarWithTitle(
            title = stringResource(R.string.title_bio),
            titleAlignment = Alignment.CenterHorizontally,
            onBackIconClicked = {
                keyboard.hideIfVisible { flowNavigator.back() }
            },
        )
        EditBioScreenContent(state, viewModel::dispatchEvent)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<EditBioViewModel.Event.OnBioSaved>()
            .onEach { flowNavigator.proceed() }
            .launchIn(this)
    }
}

@Composable
private fun EditBioScreenContent(
    state: EditBioViewModel.State,
    dispatchEvent: (EditBioViewModel.Event) -> Unit,
) {
    val keyboard = rememberKeyboardController()
    val draft = state.draft
    CodeScaffold(
        modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
        bottomBar = {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        top = CodeTheme.dimens.grid.x6,
                        bottom = CodeTheme.dimens.staticGrid.x4,
                    ).imePadding(),
                text = stringResource(R.string.action_save),
                enabled = draft.canSave && state.processingState.isIdle,
                isLoading = state.processingState.loading,
                isSuccess = state.processingState.success,
                onClick = {
                    keyboard.hideIfVisible { dispatchEvent(EditBioViewModel.Event.Save) }
                },
            )
        }
    ) { padding ->
        val focusRequester = remember { FocusRequester() }
        // The counter sits right under the field and any error under the counter, as on iOS.
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(top = CodeTheme.dimens.staticGrid.x4),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x2),
        ) {
            DisplayTextInput(
                state = state.fieldState,
                placeholder = stringResource(R.string.placeholder_bio),
                style = CodeTheme.typography.textLarge.copy(color = CodeTheme.colors.textMain),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                minLines = 3,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                ),
            )

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = draft.remaining.toString(),
                style = CodeTheme.typography.textSmall,
                color = if (draft.remaining < 0) CodeTheme.colors.errorText else CodeTheme.colors.textSecondary,
                textAlign = TextAlign.End,
            )

            val error = draft.error
            if (error != null) {
                Text(
                    text = stringResource(
                        when (error) {
                            BioError.Moderated -> R.string.error_bioNotAllowed
                            BioError.Invalid -> R.string.error_bioInvalid
                        }
                    ),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.errorText,
                )
            }
        }

        RequestFocusWhenSettled(focusRequester)
    }
}
