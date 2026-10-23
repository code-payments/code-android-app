package com.flipcash.app.bills.modals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.app.core.ui.inDisplayCurrency
import com.flipcash.shared.bills.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.ui.components.Modal
import com.getcode.ui.components.text.AmountArea
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun ReceivedFundsConfirmation(
    bill: Scannable.Payable,
    onClaim: () -> Unit,
) {
    Modal {
        Text(
            style = CodeTheme.typography.textLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = White,
            text = stringResource(id = R.string.subtitle_youReceived)
        )

        AmountArea(
            // The bill's face reads as it was sent; what lands is restated in the viewer's currency,
            // the same figure chat shows for the same payment.
            amountText = bill.amount.inDisplayCurrency().formatted(),
            isClickable = false
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            Text(
                text = stringResource(R.string.of),
                style = CodeTheme.typography.screenTitle,
                color = CodeTheme.colors.textSecondary,
            )
            TokenIconWithName(
                token = bill.token,
                imageSize = CodeTheme.dimens.staticGrid.x4,
                textStyle = CodeTheme.typography.screenTitle,
                textColor = CodeTheme.colors.textSecondary,
                spacing = CodeTheme.dimens.grid.x1,
            )
        }

        CodeButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = CodeTheme.dimens.grid.x6,
                    bottom = CodeTheme.dimens.inset,
                ),
            onClick = onClaim,
            buttonState = ButtonState.Filled,
            text = stringResource(id = R.string.action_putInWallet)
        )
    }
}