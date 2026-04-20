package com.flipcash.app.balance.internal.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.money.formatted
import com.flipcash.features.balance.R
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.text.AnimatedNumberText
import com.getcode.ui.core.rememberedClickable

@Composable
internal fun CashReservesRow(
    reserves: LocalFiat,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .rememberedClickable {
                onClick()
            }
            .padding(
                vertical = CodeTheme.dimens.grid.x3,
                horizontal = CodeTheme.dimens.inset
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.title_cashReserves),
            style = CodeTheme.typography.screenTitle,
            color = CodeTheme.colors.textSecondary,
        )

        Icon(
            modifier = Modifier
                .padding(start = CodeTheme.dimens.grid.x2),
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = CodeTheme.colors.textSecondary,
        )

        Spacer(Modifier.weight(1f))

        AnimatedNumberText(
            value = reserves.formatted(),
            style = CodeTheme.typography.screenTitle,
            color = CodeTheme.colors.textMain,
        )
    }
}