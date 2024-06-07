package com.getcode.view.main.home.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.getcode.R
import com.getcode.models.Bill
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.Modal
import com.getcode.util.flagResId
import com.getcode.util.formatted
import com.getcode.view.main.giveKin.AmountArea

@Composable
internal fun ReceivedKinConfirmation(
    bill: Bill.Cash,
    onClaim: () -> Unit,
) {
    Modal(backgroundColor = Brand) {
        Text(
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x3),
            style = CodeTheme.typography.textLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = White,
            text = stringResource(id = R.string.subtitle_youReceived)
        )

        Row {
            AmountArea(
                amountText = bill.amount.formatted(),
                currencyResId = bill.amount.rate.currency.flagResId,
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