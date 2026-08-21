package com.flipcash.app.deposit.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.flipcash.app.core.deposit.DepositResult
import com.flipcash.app.core.deposit.DepositStep
import com.flipcash.app.core.ui.ConversionCoin
import com.flipcash.app.core.ui.ConversionGraphic
import com.flipcash.core.R
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun UsdcDepositInformationScreen(showOtherOptions: Boolean) {
    val flowNavigator = rememberFlowNavigator<DepositStep, DepositResult>()
    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_deposit),
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
                    flowNavigator.navigateTo(DepositStep.Destination(Mint.usdc))
                }

                if (showOtherOptions) {
                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth(),
                        buttonState = ButtonState.Subtle,
                        text = stringResource(R.string.action_depositOtherCurrencies),
                    ) {
                        flowNavigator.navigateTo(DepositStep.SelectToken)
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
                    ConversionGraphic(
                        from = ConversionCoin.UsdcOnSolana,
                        to = ConversionCoin.Dollars,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(0.80f),
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.title_depositUsdcAsDollars),
                        style = CodeTheme.typography.textLarge,
                        color = CodeTheme.colors.textMain,
                    )
                    Text(
                        text = stringResource(R.string.description_depositUsdcAsDollars),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}