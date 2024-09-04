package com.getcode.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.LocalExchange
import com.getcode.model.chat.MessageContent
import com.getcode.model.MessageStatus
import com.getcode.model.chat.Verb
import com.getcode.model.orOneToOne
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.view.main.scanner.components.PriceWithFlag
import kotlinx.datetime.Instant

@Composable
internal fun MessagePayment(
    modifier: Modifier = Modifier,
    contents: MessageContent.Exchange,
    date: Instant,
    status: MessageStatus = MessageStatus.Unknown,
    showTipActions: Boolean = true,
    openMessageChat: () -> Unit,
) {
    Column(
        modifier = modifier
            // payments have an extra 10.dp inner padding
            .padding(CodeTheme.dimens.grid.x1)
            .background(CodeTheme.colors.background, RoundedCornerShape(3.dp)) // small - padding
            .padding(CodeTheme.dimens.grid.x2),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val exchange = LocalExchange.current
        val rate by remember(contents.amount.currencyCode) {
            derivedStateOf {
                exchange.rateFor(contents.amount.currencyCode).orOneToOne()
            }
        }
        val amount by remember(rate) {
            derivedStateOf { contents.amount.amountUsing(rate) }
        }

        Column(
            modifier = Modifier
                .padding(top = CodeTheme.dimens.grid.x3)
                .padding(horizontal = CodeTheme.dimens.grid.x6),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (contents.verb == Verb.Returned) {
                PriceWithFlag(
                    currencyCode = amount.rate.currency,
                    amount = amount,
                    text = { price ->
                        Text(
                            text = price,
                            color = Color.White,
                            style = CodeTheme.typography.displaySmall
                        )
                    }
                )
                Text(
                    text = contents.verb.localizedText,
                    style = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.W500)
                )
            } else {
                Text(
                    text = contents.verb.localizedText,
                    style = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.W500)
                )
                PriceWithFlag(
                    currencyCode = amount.rate.currency,
                    amount = amount,
                    text = { price ->
                        Text(
                            text = price,
                            color = Color.White,
                            style = CodeTheme.typography.displaySmall
                        )
                    }
                )
            }
        }

        TipChatActions(
            contents = contents,
            showTipActions = showTipActions,
            openMessageChat = openMessageChat
        )

        DateWithStatus(
            modifier = Modifier
                .align(Alignment.End),
            date = date,
            status = status,
            isFromSelf = contents.isFromSelf,
        )
    }
}