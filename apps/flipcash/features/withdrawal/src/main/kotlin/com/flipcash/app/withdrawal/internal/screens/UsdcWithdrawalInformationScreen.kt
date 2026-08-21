package com.flipcash.app.withdrawal.internal.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.ui.ConversionCoin
import com.flipcash.app.core.ui.ConversionGraphic
import com.flipcash.app.core.withdrawal.WithdrawalResult
import com.flipcash.app.core.withdrawal.WithdrawalStep
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.core.R
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun UsdcWithdrawalInformationScreen(showOtherOptions: Boolean) {
    val flowNavigator = rememberFlowNavigator<WithdrawalStep, WithdrawalResult>()
    val isNewUi by LocalFeatureFlags.current.observe(FeatureFlag.NewUi)
        .collectAsStateWithLifecycle()

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                // v2 reaches this screen mid-flow (picker → Dollars), where the screen's own heading
                // already names it; v1 enters here, so it keeps the "Withdraw" title.
                title = if (isNewUi) "" else stringResource(R.string.title_withdraw),
                onBackIconClicked = { flowNavigator.back() },
                titleAlignment = Alignment.CenterHorizontally,
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .navigationBarsPadding(),
            ) {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    buttonState = ButtonState.Filled,
                    text = stringResource(R.string.action_next),
                ) {
                    flowNavigator.navigateTo(WithdrawalStep.Amount(Mint.usdc))
                }

                // The escape hatch is a v1 affordance: v2 enters the flow on the picker, so every
                // other currency is already one step back.
                if (showOtherOptions && !isNewUi) {
                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth(),
                        buttonState = ButtonState.Subtle,
                        text = stringResource(R.string.action_withdrawOtherCurrencies),
                    ) {
                        flowNavigator.navigateTo(WithdrawalStep.SelectToken)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x11),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isNewUi) {
                        ConversionGraphic(
                            from = ConversionCoin.Dollars,
                            to = ConversionCoin.UsdcOnSolana,
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_withdraw_usdf_as_usdc),
                            contentDescription = null,
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(if (isNewUi) 0.80f else 0.60f),
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.title_withdrawUsdfAsUsdc),
                        style = CodeTheme.typography.textLarge,
                        color = CodeTheme.colors.textMain,
                    )
                    Text(
                        text = stringResource(
                            if (isNewUi) {
                                R.string.description_withdrawDollarsAsUsdc
                            } else {
                                R.string.description_withdrawUsdfAsUsdc
                            }
                        ),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
