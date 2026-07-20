package com.flipcash.app.currencycreator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberInitialStack
import com.getcode.navigation.flow.rememberFlowNavigator
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.flipcash.app.bill.customization.LocalBillPlaygroundController
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.CurrencyCreatorResult
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.currencycreator.internal.screens.BillCustomizationContent
import com.flipcash.app.currencycreator.internal.components.CurrencyCreatorTopBar
import com.flipcash.app.currencycreator.internal.components.CurrencyCreatorTopBarController
import com.flipcash.app.currencycreator.internal.components.CurrencyCreatorTopBarController.Companion.LocalCurrencyCreatorTopBar
import com.flipcash.app.currencycreator.internal.CurrencyCreatorViewModel
import com.flipcash.app.currencycreator.internal.screens.BillCustomizationScreen
import com.flipcash.app.currencycreator.internal.screens.DescriptionSelectionContent
import com.flipcash.app.currencycreator.internal.screens.DescriptionSelectionScreen
import com.flipcash.app.currencycreator.internal.screens.IconSelectionContent
import com.flipcash.app.currencycreator.internal.screens.IconSelectionScreen
import com.flipcash.app.currencycreator.internal.screens.InfoScreen
import com.flipcash.app.currencycreator.internal.screens.InfoScreenContent
import com.flipcash.app.currencycreator.internal.screens.NameSelectionContent
import com.flipcash.app.currencycreator.internal.screens.NameSelectionScreen
import com.flipcash.app.currencycreator.internal.screens.ProcessingContent
import com.flipcash.app.currencycreator.internal.screens.ProcessingScreen
import com.flipcash.app.currencycreator.internal.screens.ReviewContent
import com.flipcash.app.currencycreator.internal.screens.BillReviewScreen
import com.flipcash.app.currencycreator.internal.screens.FundingSourceSelectionScreen
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.core.R
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.FlowExitReason
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.LocalFlowNavigator
import com.getcode.navigation.flow.PreviewFlowNavigator
import com.getcode.navigation.flow.deliverFlowResult
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.toFiat
import com.getcode.theme.CodeTheme
import com.getcode.theme.rememberDynamicAccent
import com.getcode.ui.core.unboundedClickable

@Composable
fun CurrencyCreatorFlowScreen(
    route: AppRoute.Token.CurrencyCreator,
    resultStateRegistry: NavResultStateRegistry,
) {
    val outerNavigator = LocalCodeNavigator.current

    val initialStack = route.rememberInitialStack<CurrencyCreatorStep>()

    val topBarController = remember { CurrencyCreatorTopBarController() }

    val viewModel = hiltViewModel<CurrencyCreatorViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    val session = LocalSessionController.current

    CompositionLocalProvider(LocalCurrencyCreatorTopBar provides topBarController) {
        Column(modifier = Modifier.fillMaxSize()) {
            CurrencyCreatorTopBar(
                controller = topBarController,
                mainContent = when (state.currentStep) {
                    is CurrencyCreatorStep.Info -> {
                        {
                            Text(
                                text = stringResource(R.string.title_createYourCurrency),
                                style = CodeTheme.typography.textLarge,
                                color = CodeTheme.colors.textMain,
                            )
                        }
                    }

                    // TokenSelectScreen's own top bar is hidden on this step (showTopBar = false)
                    // to avoid stacking two bars; surface its title here instead. Matches the
                    // LaunchFunding title the select screen would show, and like Bill Review this
                    // step shows no progress indicator (title replaces the progress slot).
                    is CurrencyCreatorStep.FundingSourceSelection -> {
                        {
                            Text(
                                text = stringResource(R.string.title_selectPaymentCurrency),
                                style = CodeTheme.typography.textLarge,
                                color = CodeTheme.colors.textMain,
                            )
                        }
                    }

                    is CurrencyCreatorStep.Processing -> {
                        {
                            val text = if (state.processingState.success) {
                                stringResource(R.string.title_success)
                            } else if (state.processingState.error) {
                                stringResource(R.string.title_failed)
                            } else {
                                stringResource(
                                    R.string.title_creatingCurrency,
                                    state.nameFieldState.text.toString()
                                )
                            }

                            Text(
                                text = text,
                                style = CodeTheme.typography.textLarge,
                                color = CodeTheme.colors.textMain,
                            )
                        }
                    }

                    else -> null
                },
                endContent = when (state.currentStep) {
                    is CurrencyCreatorStep.BillCustomization -> {
                        {
                            val (accent, onAccent) = rememberDynamicAccent(
                                fallbackAccent = CodeTheme.colors.secondary,
                                fallbackOnAccent = CodeTheme.colors.onAction,
                            )

                            Text(
                                text = stringResource(R.string.action_next),
                                style = CodeTheme.typography.textMedium,
                                color = onAccent,
                                modifier = Modifier
                                    .background(accent, shape = CircleShape)
                                    .unboundedClickable {
                                        topBarController.onEndAction?.invoke()
                                    }
                                    .padding(
                                        horizontal = CodeTheme.dimens.grid.x2,
                                        vertical = CodeTheme.dimens.grid.x1
                                    ),
                            )
                        }
                    }

                    else -> null
                },
            )

            FlowHost(
                initialStack = initialStack,
                resultStateRegistry = resultStateRegistry,
                onExit = { reason, _ ->
                    val result: CurrencyCreatorResult = when (reason) {
                        is FlowExitReason.Completed -> reason.result
                        FlowExitReason.Canceled,
                        FlowExitReason.BackedOutOfRoot -> CurrencyCreatorResult.Canceled
                    }

                    outerNavigator.deliverFlowResult(
                        route = route,
                        value = NavResultOrCanceled.ReturnValue(result),
                    )

                    if (result is CurrencyCreatorResult.Success) {
                        // The bill is priced through the new token's bonding curve in the
                        // ViewModel (see buildLaunchBill); presenting it lets someone grab the
                        // newly created currency. It's null if pricing failed — just close then.
                        val bill = state.launchBill
                        if (state.purchaseAmount > Fiat.Zero && bill != null) {
                            outerNavigator.hide()
                            session?.showBill(bill)
                        } else {
                            outerNavigator.pop()
                        }
                    } else {
                        outerNavigator.pop()
                    }
                },
                entryProvider = currencyCreatorEntryProvider(),
            )
        }
    }
}

private fun currencyCreatorEntryProvider(): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<CurrencyCreatorStep.Info> { SyncTopBar(it); InfoScreen() }
    annotatedEntry<CurrencyCreatorStep.NameSelection> { SyncTopBar(it); NameSelectionScreen() }
    annotatedEntry<CurrencyCreatorStep.IconSelection> { SyncTopBar(it); IconSelectionScreen() }
    annotatedEntry<CurrencyCreatorStep.DescriptionSelection> { SyncTopBar(it); DescriptionSelectionScreen() }
    annotatedEntry<CurrencyCreatorStep.BillCustomization> { SyncTopBar(it); BillCustomizationScreen() }
    annotatedEntry<CurrencyCreatorStep.BillReview> { SyncTopBar(it); BillReviewScreen() }
    annotatedEntry<CurrencyCreatorStep.FundingSourceSelection> { SyncTopBar(it); FundingSourceSelectionScreen() }
    annotatedEntry<CurrencyCreatorStep.Processing> { SyncTopBar(it); ProcessingScreen() }
}

/**
 * Syncs the current step to the ViewModel, which in turn pushes progress and
 * visibility to the top bar controller. Also wires up the back button.
 */
@Composable
private fun SyncTopBar(step: CurrencyCreatorStep) {
    val topBar = LocalCurrencyCreatorTopBar.current
    val billPlaygroundController = LocalBillPlaygroundController.current
    val flowNavigator = rememberFlowNavigator<CurrencyCreatorStep, CurrencyCreatorResult>()
    val viewModel = flowSharedViewModel<CurrencyCreatorViewModel>()
    LaunchedEffect(viewModel, topBar) {
        viewModel.connectTopBar(topBar)
    }

    LaunchedEffect(step) {
        viewModel.dispatchEvent(CurrencyCreatorViewModel.Event.OnStepChanged(step))
        if (step is CurrencyCreatorStep.Processing) {
            topBar.onBack = null
        } else {
            topBar.onBack = { flowNavigator.back() }
        }
        topBar.onEndAction = {
            val bill = billPlaygroundController.state.value.customizedBill
            viewModel.dispatchEvent(CurrencyCreatorViewModel.Event.OnBillConfirmed(bill))
            flowNavigator.navigateTo(CurrencyCreatorStep.BillReview)
        }
    }
}


@Composable
private fun CurrencyCreatorPreview(
    feeAmount: Fiat = 15.toFiat(),
    content: @Composable (state: CurrencyCreatorViewModel.State) -> Unit
) {
    CompositionLocalProvider(
        LocalFlowNavigator provides PreviewFlowNavigator<CurrencyCreatorStep, CurrencyCreatorResult>(),
        LocalCurrencyCreatorTopBar provides remember { CurrencyCreatorTopBarController() },
    ) {
        val state = CurrencyCreatorViewModel.State(feeAmount = feeAmount)
        content(state)
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_Info_15Fee() {
    CurrencyCreatorPreview { InfoScreenContent(it) }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_Info_NoFee() {
    CurrencyCreatorPreview(feeAmount = Fiat.Zero) { InfoScreenContent(it) }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_Info_5Fee() {
    CurrencyCreatorPreview(feeAmount = 5.toFiat()) { InfoScreenContent(it) }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_Name() {
    CurrencyCreatorPreview { NameSelectionContent(it) {} }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_Icon() {
    CurrencyCreatorPreview { IconSelectionContent(it) {} }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_Description() {
    CurrencyCreatorPreview { DescriptionSelectionContent(it) {} }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_Bill() {
    CurrencyCreatorPreview { BillCustomizationContent(it) {} }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_Review() {
    CurrencyCreatorPreview { ReviewContent(it) {} }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_Processing() {
    CurrencyCreatorPreview { ProcessingContent(it) {} }
}
