package com.flipcash.app.withdrawal.internal.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.withdrawal.WithdrawalResult
import com.flipcash.app.core.withdrawal.WithdrawalStep
import com.flipcash.core.R
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun UsdcWithdrawalInformationScreen() {
    val flowNavigator = rememberFlowNavigator<WithdrawalStep, WithdrawalResult>()

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                // Reached mid-flow (picker → Dollars), where the screen's own heading already names
                // it — so the bar carries no title of its own.
                title = "",
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
                    ConversionGraphic()
                }

                Column(
                    modifier = Modifier.fillMaxWidth(0.80f),
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.title_withdrawUsdfAsUsdc),
                        style = CodeTheme.typography.textLarge,
                        color = CodeTheme.colors.textMain,
                    )
                    Text(
                        text = stringResource(R.string.description_withdrawDollarsAsUsdc),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * The "Dollars → USDC" graphic (Figma node 9216:19798). The gold Dollars coin and the USDC-on-Solana
 * mark are composed here rather than shipped as one flattened asset.
 */
@Composable
private fun ConversionGraphic() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // 14 dp between each pair, matching the 278x124 Figma frame's coin/arrow gaps.
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            modifier = Modifier.size(100.dp),
            painter = painterResource(R.drawable.ic_coin_dollars),
            contentDescription = null,
        )
        Image(
            modifier = Modifier.size(28.dp),
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
        )
        // Natural size (111x112): the 100 dp coin plus the Solana badge overhanging its corner.
        Image(
            painter = painterResource(R.drawable.ic_usdc_on_solana),
            contentDescription = null,
        )
    }
}
