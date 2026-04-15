package com.flipcash.app.currencycreator.internal.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.tokens.CurrencyCreatorResult
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.core.ui.DisplayTextInput
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.app.core.ui.transitions.CircleOverlayClip
import com.flipcash.app.core.ui.transitions.SharedTransition
import com.flipcash.app.core.ui.transitions.sharedBoundsTransition
import com.flipcash.app.currencycreator.internal.CurrencyCreatorViewModel
import com.flipcash.core.R
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.text.AnimatedNumberText
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun DescriptionSelectionScreen() {
    val flowNavigator = rememberFlowNavigator<CurrencyCreatorStep, CurrencyCreatorResult>()

    val viewModel = flowSharedViewModel<CurrencyCreatorViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    DescriptionSelectionContent(state, viewModel::dispatchEvent)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<CurrencyCreatorViewModel.Event.OnDescriptionApproved>()
            .onEach { flowNavigator.navigateTo(CurrencyCreatorStep.BillCustomization()) }
            .launchIn(this)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun DescriptionSelectionContent(
    state: CurrencyCreatorViewModel.State,
    dispatch: (CurrencyCreatorViewModel.Event) -> Unit
) {
    val keyboard = rememberKeyboardController()

    CodeScaffold(
        modifier = Modifier
            .padding(horizontal = CodeTheme.dimens.inset),
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = CodeTheme.dimens.grid.x1),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isOverLimit = remember(state.remainingSpaceForDescription) {
                        state.remainingSpaceForDescription < 0
                    }
                    val textColor by animateColorAsState(
                        if (isOverLimit) CodeTheme.colors.errorText else CodeTheme.colors.textSecondary
                    )

                    AnimatedNumberText(
                        value = state.remainingSpaceForDescription.toString(),
                        style = CodeTheme.typography.textSmall,
                        color = textColor,
                    )

                    Text(
                        text = stringResource(R.string.label_characters),
                        style = CodeTheme.typography.textSmall,
                        color = textColor,
                    )

                    AnimatedVisibility(
                        visible = state.remainingSpaceForDescription < state.descriptionLengthMax,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = stringResource(R.string.label_remaining),
                            style = CodeTheme.typography.textSmall,
                            color = textColor,
                        )
                    }
                }

                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            bottom = CodeTheme.dimens.grid.x3
                        ),
                    text = stringResource(R.string.action_next),
                    enabled = state.hasDescription && state.processingState.isIdle,
                    isLoading = state.processingState.loading,
                    isSuccess = state.processingState.success,
                    onClick = {
                        keyboard.hideIfVisible {
                            dispatch(CurrencyCreatorViewModel.Event.CheckDescription)
                        }
                    },
                )
            }
        }
    ) { padding ->
        val focusRequester = remember { FocusRequester() }
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState)
                .verticalScrollStateGradient(
                    scrollState = scrollState,
                    color = CodeTheme.colors.background,
                    isLongGradient = true
                ),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
            ) {
                TokenIconWithName(
                    tokenName = state.nameFieldState.text.let {
                        if (it.isNotBlank()) it.toString() else stringResource(
                            R.string.placeholder_currencyName
                        )
                    },
                    iconModifier = Modifier.sharedBoundsTransition(
                        transition = SharedTransition.CurrencyIcon,
                    ),
                    textModifier = Modifier.sharedBoundsTransition(
                        transition = SharedTransition.CurrencyName,
                    ),
                    tokenImage = state.icon.dataOrNull,
                    imageSize = CodeTheme.dimens.staticGrid.x6,
                    spacing = CodeTheme.dimens.grid.x2
                )

                Text(
                    text = stringResource(R.string.title_currencyCreatorDescription),
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain,
                )
            }

            DisplayTextInput(
                state = state.descriptionFieldState,
                placeholder = stringResource(R.string.hint_description),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                style = CodeTheme.typography.screenTitle,
                placeholderStyle = CodeTheme.typography.screenTitle.copy(
                    color = CodeTheme.colors.textTertiary,
                ),
                maxLines = Int.MAX_VALUE,
                onKeyboardAction = {
                    keyboard.hideIfVisible {
                        dispatch(CurrencyCreatorViewModel.Event.CheckDescription)
                    }
                },
            )
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}
