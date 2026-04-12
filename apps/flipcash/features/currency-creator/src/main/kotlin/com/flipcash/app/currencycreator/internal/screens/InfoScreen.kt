package com.flipcash.app.currencycreator.internal.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.tokens.CurrencyCreatorResult
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.currencycreator.internal.CurrencyCreatorViewModel
import com.flipcash.app.currencycreator.internal.components.Stepper
import com.flipcash.core.R
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun InfoScreen() {
    val viewModel = flowSharedViewModel<CurrencyCreatorViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    InfoScreenContent(state)
}

@Composable
internal fun InfoScreenContent(state: CurrencyCreatorViewModel.State) {
    val flowNavigator = rememberFlowNavigator<CurrencyCreatorStep, CurrencyCreatorResult>()

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
                onClick = { flowNavigator.navigateTo(CurrencyCreatorStep.NameSelection()) },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Spacer(Modifier.height(CodeTheme.dimens.grid.x2))

            Text(
                text = stringResource(R.string.title_createYourCurrency),
                style = CodeTheme.typography.displaySmall,
                color = Color.White,
            )

            Text(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                text = stringResource(R.string.subtitle_currencyCreatorInfo, state.stepCount),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )

            Stepper(
                modifier = Modifier
                    .weight(0.5f)
                    .padding(top = CodeTheme.dimens.grid.x12),
                cost = state.purchaseAmount,
            )
        }
    }
}