package com.getcode.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
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
import com.getcode.model.MessageContent
import com.getcode.model.MessageStatus
import com.getcode.model.Verb
import com.getcode.model.orOneToOne
import com.getcode.theme.BrandDark
import com.getcode.theme.ChatOutgoing
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.ui.utils.addIf
import com.getcode.view.main.home.components.PriceWithFlag
import kotlinx.datetime.Instant

object MessageNodeDefaults {

    val DefaultShape: CornerBasedShape
        @Composable get() = CodeTheme.shapes.small
    val PreviousSameShape: CornerBasedShape
        @Composable get() = DefaultShape.copy(topStart = CornerSize(3.dp))

    val NextSameShape: CornerBasedShape
        @Composable get() = DefaultShape.copy(bottomStart = CornerSize(3.dp))

    val MiddleSameShape: CornerBasedShape
        @Composable get() = DefaultShape.copy(
            topStart = CornerSize(3.dp),
            bottomStart = CornerSize(3.dp)
        )
}

@Composable
fun MessageNode(
    modifier: Modifier = Modifier,
    contents: MessageContent,
    date: Instant,
    isPreviousSameMessage: Boolean,
    isNextSameMessage: Boolean,
    showTipActions: Boolean = true,
    thankUser: () -> Unit = { },
    openMessageChat: () -> Unit = { },
) {
    Box(
        modifier = modifier
            .padding(vertical = CodeTheme.dimens.grid.x1)
    ) {
        val color = if (contents is MessageContent.Exchange && !contents.verb.increasesBalance) {
            ChatOutgoing
        } else {
            BrandDark
        }

        val isAnnouncement =
            remember { (contents as? MessageContent.Localized)?.isAnnouncement ?: false }

        when (contents) {
            is MessageContent.Exchange -> {
                MessagePayment(
                    modifier = Modifier
                        .fillMaxWidth(0.895f)
                        .background(
                            color = color,
                            shape = when {
                                isAnnouncement -> MessageNodeDefaults.DefaultShape
                                isPreviousSameMessage && isNextSameMessage -> MessageNodeDefaults.MiddleSameShape
                                isPreviousSameMessage -> MessageNodeDefaults.PreviousSameShape
                                isNextSameMessage -> MessageNodeDefaults.NextSameShape
                                else -> MessageNodeDefaults.DefaultShape
                            }
                        ),
                    contents = contents,
                    showTipActions = showTipActions,
                    thankUser = thankUser,
                    date = date,
                    openMessageChat = openMessageChat
                )
            }

            is MessageContent.Localized -> {
                if (contents.isAnnouncement) {
                    AnnouncementMessage(
                        modifier = Modifier
                            .align(Alignment.Center),
                        text = contents.localizedText
                    )
                } else {
                    MessageText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CodeTheme.dimens.grid.x2),
                        content = contents.localizedText,
                        date = date,
                        status = contents.status,
                        isFromSelf = contents.status.isOutgoing()
                    )
                }
            }

            is MessageContent.SodiumBox -> {
                EncryptedContent(
                    modifier = Modifier
                        .fillMaxWidth(0.895f)
                        .background(
                            color = color,
                            shape = when {
                                isAnnouncement -> MessageNodeDefaults.DefaultShape
                                isPreviousSameMessage && isNextSameMessage -> MessageNodeDefaults.MiddleSameShape
                                isPreviousSameMessage -> MessageNodeDefaults.PreviousSameShape
                                isNextSameMessage -> MessageNodeDefaults.NextSameShape
                                else -> MessageNodeDefaults.DefaultShape
                            }
                        )
                        .padding(CodeTheme.dimens.grid.x2),
                    date = date
                )
            }

            is MessageContent.Decrypted -> {
                MessageText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CodeTheme.dimens.grid.x2),
                    content = contents.data,
                    date = date,
                    status = contents.status,
                    isFromSelf = contents.status.isOutgoing()
                )
            }
        }
    }
}

@Composable
private fun MessagePayment(
    modifier: Modifier = Modifier,
    contents: MessageContent.Exchange,
    date: Instant,
    status: MessageStatus = MessageStatus.Unknown,
    showTipActions: Boolean = true,
    thankUser: () -> Unit,
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
                            style = CodeTheme.typography.h3
                        )
                    }
                )
                Text(
                    text = contents.verb.localizedText,
                    style = CodeTheme.typography.body1.copy(fontWeight = FontWeight.W500)
                )
            } else {
                Text(
                    text = contents.verb.localizedText,
                    style = CodeTheme.typography.body1.copy(fontWeight = FontWeight.W500)
                )
                PriceWithFlag(
                    currencyCode = amount.rate.currency,
                    amount = amount,
                    text = { price ->
                        Text(
                            text = price,
                            color = Color.White,
                            style = CodeTheme.typography.h3
                        )
                    }
                )
            }
        }

        TipChatActions(
            contents = contents,
            showTipActions = showTipActions,
            thankUser = thankUser,
            openMessageChat = openMessageChat
        )

        DateWithStatus(
            modifier = Modifier
                .align(Alignment.End),
            date = date,
            status = status
        )
    }
}