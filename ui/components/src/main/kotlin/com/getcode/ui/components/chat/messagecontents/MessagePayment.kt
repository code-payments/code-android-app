package com.getcode.ui.components.chat.messagecontents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.MessageStatus
import com.getcode.model.chat.Verb
import com.getcode.theme.CodeTheme
import com.getcode.utils.FormatUtils
import com.getcode.utils.flagResId
import com.getcode.ui.components.PriceWithFlag
import com.getcode.ui.components.chat.utils.localizedText
import kotlin.time.Instant

@Composable
internal fun MessagePayment(
    modifier: Modifier = Modifier,
    contents: MessageContent.Exchange,
    date: Instant,
    status: MessageStatus = MessageStatus.Unknown,
    showStatus: Boolean = true,
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
        // TODO(chat-v2): reimplement payment formatting with OCP Fiat
        val formattedAmount = FormatUtils.formatCurrency(contents.amount, contents.currencyCode)

        Column(
            modifier = Modifier
                .padding(top = CodeTheme.dimens.grid.x3)
                .padding(horizontal = CodeTheme.dimens.grid.x6),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (contents.verb == Verb.Returned) {
                PriceWithFlag(
                    currencyCode = contents.currencyCode.name,
                    amount = formattedAmount,
                    flag = contents.currencyCode.flagResId,
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
                    currencyCode = contents.currencyCode.name,
                    amount = formattedAmount,
                    flag = contents.currencyCode.flagResId,
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

        DateWithStatus(
            modifier = Modifier
                .align(Alignment.End),
            date = date,
            status = status,
            isFromSelf = contents.isFromSelf,
            showStatus = showStatus
        )
    }
}