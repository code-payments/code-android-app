package com.flipcash.app.purchase.internal

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.ui.BrandedGradientIcon
import com.flipcash.features.purchase.R
import com.flipcash.app.billing.IapProduct
import com.flipcash.app.billing.ProductPrice
import com.flipcash.app.theme.FlipcashPreview
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeButtonSpacer
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import com.getcode.util.getActivity
import com.getcode.util.permissions.LocalPermissionChecker
import com.getcode.view.LoadingSuccessState
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun PurchaseAccountScreen(viewModel: PurchaseAccountViewModel) {
    val navigator = LocalCodeNavigator.current
    val permissions = LocalPermissionChecker.current

    val state by viewModel.stateFlow.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<PurchaseAccountViewModel.Event.OnAccountCreated>()
            .onEach {
                navigator.replaceAll(AppRoute.Main.Scanner())
            }.launchIn(this)
    }

    PurchaseAccountScreenContent(state, viewModel::dispatchEvent)
}

@Composable
private fun PurchaseAccountScreenContent(
    state: PurchaseAccountViewModel.State,
    dispatchEvent: (PurchaseAccountViewModel.Event) -> Unit
) {
    val context = LocalContext.current
    CodeScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x1)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
            ) {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    buttonState = ButtonState.Filled,
                    enabled = state.hasProduct && !state.isPurchasePending,
                    isLoading = state.creatingAccount.loading && !state.isPurchasePending,
                    isSuccess = state.creatingAccount.success,
                    text = if (state.isPurchasePending) {
                        stringResource(R.string.title_purchaseAccountPending)
                    } else {
                        stringResource(R.string.action_purchaseAccount)
                    },
                ) {
                    context.getActivity()?.let { activity ->
                        dispatchEvent(PurchaseAccountViewModel.Event.BuyAccount(activity))
                    }
                }
                CodeButtonSpacer(
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .align(Alignment.Center),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                BrandedGradientIcon(
                    painter = rememberVectorPainter(Icons.Default.CheckCircleOutline)
                )

                if (state.hasProduct) {
                    Text(
                        modifier = Modifier.padding(top = CodeTheme.dimens.grid.x10),
                        text = state.title,
                        style = CodeTheme.typography.textLarge,
                        textAlign = TextAlign.Center,
                        color = CodeTheme.colors.textMain
                    )

                    Text(
                        text = state.subtitle,
                        style = CodeTheme.typography.textMedium,
                        textAlign = TextAlign.Center,
                        color = CodeTheme.colors.textSecondary
                    )
                } else {
                    CodeCircularProgressIndicator()
                }
            }

            if (state.isPurchasePending) {
                CodeCircularProgressIndicator(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = CodeTheme.dimens.inset)
                )
            }
        }
    }
}

@Preview
@Composable
private fun Preview_WelcomeBonus_Usd() {
    FlipcashPreview {
        PurchaseAccountScreenContent(
            state = PurchaseAccountViewModel.State(
                productToBuy = IapProduct.CreateAccountWithWelcomeBonus,
                costOfAccount = ProductPrice(
                    amount = 20.00,
                    currency = CurrencyCode.USD
                ),
                formattedCost = "$20"
            ),
        ) { }
    }
}

@Preview
@Composable
private fun Preview_NoBonus_Usd() {
    FlipcashPreview {
        PurchaseAccountScreenContent(
            state = PurchaseAccountViewModel.State(
                productToBuy = IapProduct.CreateAccount,
                costOfAccount = ProductPrice(
                    amount = 20.00,
                    currency = CurrencyCode.USD
                ),
                formattedCost = "$20"
            ),
        ) { }
    }
}

@Preview
@Composable
private fun Preview_Pending() {
    FlipcashPreview {
        PurchaseAccountScreenContent(
            state = PurchaseAccountViewModel.State(
                productToBuy = IapProduct.CreateAccount,
                costOfAccount = ProductPrice(
                    amount = 20.00,
                    currency = CurrencyCode.USD
                ),
                formattedCost = "$20",
                isPurchasePending = true,
                creatingAccount = LoadingSuccessState(loading = true)
            ),
        ) { }
    }
}
