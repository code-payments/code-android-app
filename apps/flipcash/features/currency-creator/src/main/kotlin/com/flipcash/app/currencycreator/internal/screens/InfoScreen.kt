package com.flipcash.app.currencycreator.internal.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.tokens.CurrencyCreatorResult
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.currencycreator.internal.CurrencyCreatorViewModel
import com.flipcash.app.currencycreator.internal.components.Stepper
import com.flipcash.core.R
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun InfoScreen() {
    val viewModel = flowSharedViewModel<CurrencyCreatorViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val flowNavigator = rememberFlowNavigator<CurrencyCreatorStep, CurrencyCreatorResult>()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<CurrencyCreatorViewModel.Event.AdvanceFromInfo>()
            .onEach { flowNavigator.navigateTo(CurrencyCreatorStep.NameSelection()) }
            .launchIn(this)
    }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<CurrencyCreatorViewModel.Event.OpenScreen>()
            // navigate() pushes onto the OUTER app navigator; using the inner flow
            // navigator (LocalCodeNavigator) would try to render this app route inside
            // the currency-creator flow, which only knows CurrencyCreatorStep keys → crash.
            .onEach { flowNavigator.navigate(it.screen) }
            .launchIn(this)
    }

    InfoScreenContent(
        state = state,
        onGetStarted = { viewModel.dispatchEvent(CurrencyCreatorViewModel.Event.OnIntroContinue) },
    )
}

@Composable
internal fun InfoScreenContent(
    state: CurrencyCreatorViewModel.State,
    onGetStarted: () -> Unit = {},
) {
    CodeScaffold(
        modifier = Modifier
            .padding(horizontal = CodeTheme.dimens.inset),
        bottomBar = {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        top = CodeTheme.dimens.grid.x6,
                        bottom = CodeTheme.dimens.grid.x3
                    ),
                text = stringResource(R.string.action_getStarted),
                buttonState = ButtonState.Filled,
                onClick = onGetStarted,
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = CodeTheme.dimens.grid.x2),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
            ) {
                Text(
                    modifier = Modifier
                        .padding(horizontal = CodeTheme.dimens.inset),
                    text = stringResource(R.string.subtitle_currencyCreatorInfo, state.stepCount),
                    style = CodeTheme.typography.textSmall,
                    textAlign = TextAlign.Center,
                    color = CodeTheme.colors.textSecondary,
                )
            }

            Stepper(
                modifier = Modifier
                    .weight(0.5f)
                    .padding(top = CodeTheme.dimens.grid.x12),
                cost = state.totalCost,
                fee = state.feeAmount
            )
        }
    }
}