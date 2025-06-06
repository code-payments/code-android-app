package com.flipcash.app.balance.internal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight.Companion.W600
import androidx.compose.ui.text.style.TextOverflow
import com.flipcash.app.balance.internal.BalanceViewModel
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.unboundedClickable
import com.getcode.util.format
import com.getcode.utils.base58

@Composable
internal fun FeedItemDetails(
    message: ActivityFeedMessage,
    modifier: Modifier = Modifier,
    dispatch: (BalanceViewModel.Event) -> Unit
) {
    Column(
        modifier = modifier
            .wrapContentHeight()
            .background(
                CodeTheme.colors.background
                    .copy(.93f).compositeOver(Color.White)
            )
            .padding(
                horizontal = CodeTheme.dimens.inset,
                vertical = CodeTheme.dimens.grid.x2,
            ),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
    ) {
        DetailItem(
            modifier = Modifier.fillMaxWidth(),
            label = "ID",
            value = message.id.base58,
            valueOverflow = TextOverflow.MiddleEllipsis,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            DetailItem(
                label = "Status",
                value = message.state.name.lowercase().capitalize(),
                modifier = Modifier.weight(1f)
            )
            DetailItem(
                label = "Date",
                value = message.timestamp.format("M/d/yyyy h:mm a"),
            )
        }
        message.amount?.let { amount ->
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                DetailItem(
                    label = "Exchange Rate",
                    value = String.format("%.6f", amount.rate.fx),
                    modifier = Modifier.weight(1f)
                )
                DetailItem(
                    label = "Currency",
                    value = amount.converted.currencyCode.name,
                    modifier = Modifier.weight(1f)
                )
                DetailItem(
                    label = "USDC",
                    value = amount.usdc.formatted(formatting = Fiat.Formatting.Length(6)),
                )
            }
        }

        if (message.canCancel) {
            Text(
                modifier = Modifier
                    .padding(vertical = CodeTheme.dimens.grid.x3)
                    .align(Alignment.End)
                    .unboundedClickable {
                        dispatch(BalanceViewModel.Event.OnCancelRequested(message))
                    },
                text = "Cancel",
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}


@Composable
private fun DetailItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueOverflow: TextOverflow = TextOverflow.Visible
) {
    Column(
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                maxLines = 1,
                style = CodeTheme.typography.caption.copy(fontWeight = W600),
                color = CodeTheme.colors.textSecondary
            )
        }
        Text(
            text = value,
            style = CodeTheme.typography.caption,
            color = CodeTheme.colors.textMain,
            maxLines = 1,
            overflow = valueOverflow
        )
    }
}