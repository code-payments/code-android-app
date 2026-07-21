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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.tokens.CurrencyCreatorResult
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.core.ui.flow.FlowStepper
import com.flipcash.app.core.ui.flow.StepperItem
import com.flipcash.app.currencycreator.internal.CurrencyCreatorViewModel
import com.flipcash.core.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.minus
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

            FlowStepper(
                modifier = Modifier
                    .weight(0.5f)
                    .padding(top = CodeTheme.dimens.grid.x12),
                items = currencyCreatorStepperItems(cost = state.totalCost, fee = state.feeAmount),
            )
        }
    }
}

@Composable
private fun currencyCreatorStepperItems(cost: Fiat, fee: Fiat?): List<StepperItem> {
    val receiving = cost - (fee ?: Fiat.Zero)
    val isReceivingAmount = receiving > Fiat.Zero

    return buildList {
        add(
            StepperItem(
                icon = painterResource(R.drawable.ic_currencycreator_name),
                title = stringResource(R.string.title_currencyCreatorStepName),
                description = stringResource(R.string.subtitle_currencyCreatorStepName),
            )
        )
        add(
            StepperItem(
                icon = painterResource(R.drawable.ic_currencycreator_icon),
                title = stringResource(R.string.title_currencyCreatorStepIcon),
                description = stringResource(R.string.subtitle_currencyCreatorStepIcon),
            )
        )
        add(
            StepperItem(
                icon = painterResource(R.drawable.ic_currencycreator_description),
                title = stringResource(R.string.title_currencyCreatorStepDescription),
                description = stringResource(R.string.subtitle_currencyCreatorStepDescription),
            )
        )
        add(
            StepperItem(
                icon = painterResource(R.drawable.ic_currencycreator_cashbill),
                title = stringResource(R.string.title_currencyCreatorStepDesign),
                description = stringResource(R.string.subtitle_currencyCreatorStepDesign),
            )
        )
        add(
            StepperItem(
                icon = painterResource(R.drawable.ic_currencycreator_purchase),
                title = stringResource(
                    R.string.title_currencyCreatorStepPurchase,
                    cost.formatted(
                        rule = Fiat.FormattingRule.Truncated,
                        suffix = stringResource(R.string.subtitle_usdSuffix)
                    )
                ),
                description = stringResource(R.string.subtitle_currencyCreatorStepPurchase),
                showConnector = isReceivingAmount,
            )
        )
        if (isReceivingAmount) {
            add(
                StepperItem(
                    icon = painterResource(R.drawable.ic_currencycreator_gift),
                    title = stringResource(
                        R.string.title_currencyCreatorStepPurchaseFreeGift,
                        receiving.formatted(rule = Fiat.FormattingRule.Truncated)
                    ),
                    description = stringResource(
                        R.string.subtitle_currencyCreatorStepPurchaseFreeGift,
                        receiving.formatted(rule = Fiat.FormattingRule.Truncated)
                    ),
                    showConnector = false,
                )
            )
        }
    }
}