package com.flipcash.app.currencycreator.internal.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.tokens.CurrencyCreatorResult
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.core.ui.DisplayTextInput
import com.flipcash.app.core.ui.transitions.SharedTransition
import com.flipcash.app.core.ui.transitions.sharedBoundsTransition
import com.flipcash.app.core.ui.transitions.sharedElementTransition
import com.flipcash.app.currencycreator.internal.CurrencyCreatorViewModel
import com.flipcash.core.R
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.rememberKeyboardController

@Composable
internal fun NameSelectionScreen() {
    val viewModel = flowSharedViewModel<CurrencyCreatorViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    NameSelectionContent(state, viewModel::dispatchEvent)
}

@Composable
internal fun NameSelectionContent(
    state: CurrencyCreatorViewModel.State,
    dispatch: (CurrencyCreatorViewModel.Event) -> Unit
) {
    val flowNavigator = rememberFlowNavigator<CurrencyCreatorStep, CurrencyCreatorResult>()
    val keyboard = rememberKeyboardController()
    CodeScaffold(
        modifier = Modifier
            .padding(horizontal = CodeTheme.dimens.inset),
        topBar = {
            Text(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(
                        top = CodeTheme.dimens.grid.x2,
                        bottom = CodeTheme.dimens.inset
                    ),
                text = stringResource(R.string.title_currencyCreatorNameSelection),
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
                    ),
                text = stringResource(R.string.action_next),
                enabled = state.hasName,
                onClick = {
                    keyboard.hideIfVisible {
                        flowNavigator.navigateTo(CurrencyCreatorStep.IconSelection())
                    }
                },
            )
        }
    ) { padding ->
        val focusRequester = remember { FocusRequester() }
        DisplayTextInput(
            state = state.nameFieldState,
            placeholder = stringResource(R.string.hint_currencyName),
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .focusRequester(focusRequester),
            textModifier = Modifier.sharedElementTransition(
                transition = SharedTransition.CurrencyName,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            onKeyboardAction = {
                keyboard.hideIfVisible {
                    flowNavigator.navigateTo(CurrencyCreatorStep.IconSelection())
                }
            },
        )

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}
