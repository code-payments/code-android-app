package com.flipcash.app.pools.internal.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.features.pools.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.inputColors
import com.getcode.ui.components.TextInput
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.ConstraintMode
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun PoolQuestionScreen(viewModel: PoolCreateViewModel) {
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    PoolQuestionScreenContent(state.nameEntryState, viewModel::dispatchEvent)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<PoolCreateViewModel.Event.OnNameConfirmed>()
            .onEach {
                navigator.push(ScreenRegistry.get(NavScreenProvider.HomeScreen.Pools.Create.Amount))
            }.launchIn(this)
    }
}

@Composable
private fun PoolQuestionScreenContent(
    state: NameEntryState,
    dispatchEvent: (PoolCreateViewModel.Event) -> Unit
) {
    val keyboardController = rememberKeyboardController()
    CodeScaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = CodeTheme.dimens.grid.x3),
            ) {
                CodeButton(
                    enabled = state.canUseName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(bottom = CodeTheme.dimens.grid.x2),
                    buttonState = ButtonState.Filled,
                    text = stringResource(R.string.action_next),
                ) {
                    keyboardController.hideIfVisible {
                        dispatchEvent(PoolCreateViewModel.Event.OnNameConfirmed)
                    }
                }
            }
        }
    ) { padding ->
        val focusRequester = remember { FocusRequester() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .padding(horizontal = CodeTheme.dimens.inset),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x6)
            ) {
                TextInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    state = state.textFieldState,
                    colors = inputColors(
                        backgroundColor = Color.Transparent,
                        borderColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    textFieldAlignment = Alignment.Center,
                    constraintMode = ConstraintMode.AutoSize(minimum = CodeTheme.typography.displayExtraSmall),
                    style = CodeTheme.typography.displaySmall.copy(textAlign = TextAlign.Center),
                )

                Text(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    text = stringResource(R.string.subtitle_poolNameHint),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary
                )
            }
        }

        LaunchedEffect(focusRequester) {
            focusRequester.requestFocus()
        }
    }
}