package com.flipcash.app.scanner.internal

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.core.money.formatted
import com.flipcash.features.scanner.R
import com.getcode.opencode.compose.LocalExchange
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.ui.components.Modal
import com.getcode.ui.components.text.AmountArea
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun ReceivedFundsConfirmation(
    bill: Bill.Cash,
    onClaim: () -> Unit,
) {
    val exchange = LocalExchange.current
    Modal {
        Text(
            style = CodeTheme.typography.textLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = White,
            text = stringResource(id = R.string.subtitle_youReceived)
        )

        Row {
            AmountArea(
                amountText = bill.amount.formatted(),
                currencyResId = exchange.getFlagByCurrency(bill.amount.rate.currency.name),
                isClickable = false
            )

        }
        CodeButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClaim,
            buttonState = ButtonState.Filled,
            text = stringResource(id = R.string.action_putInWallet)
        )
    }
}