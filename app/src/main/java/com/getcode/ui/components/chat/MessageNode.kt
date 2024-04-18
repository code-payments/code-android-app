package com.getcode.ui.components.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.LocalBetaFlags
import com.getcode.LocalExchange
import com.getcode.R
import com.getcode.model.KinAmount
import com.getcode.model.MessageContent
import com.getcode.model.Rate
import com.getcode.model.Verb
import com.getcode.theme.BrandDark
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.util.formatTimeRelatively
import com.getcode.utils.FormatUtils
import com.getcode.view.main.giveKin.KinValueHint
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
    openTipChat: () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(vertical = CodeTheme.dimens.grid.x1)
    ) {
        val exchange = LocalExchange.current

        Box(
            modifier = Modifier
                .fillMaxWidth(0.895f)
                .background(
                    color = BrandDark,
                    shape = when {
                        isPreviousSameMessage && isNextSameMessage -> MessageNodeDefaults.MiddleSameShape
                        isPreviousSameMessage -> MessageNodeDefaults.PreviousSameShape
                        isNextSameMessage -> MessageNodeDefaults.NextSameShape
                        else -> MessageNodeDefaults.DefaultShape
                    }
                )
                .padding(CodeTheme.dimens.grid.x2),
            contentAlignment = Alignment.Center
        ) {
            when (contents) {
                is MessageContent.Exchange -> {
                    val rate = exchange.rateFor(contents.amount.currencyCode)
                    val isV2Enabled = LocalBetaFlags.current.chatMessageV2Enabled
                    if (rate != null) {
                        if (isV2Enabled) {
                            MessagePaymentV2(
                                verb = contents.verb,
                                amount = contents.amount.amountUsing(rate),
                                caption = contents.localizedText,
                                date = date,
                                openTipChat = openTipChat
                            )
                        } else {
                            MessagePayment(
                                verb = contents.verb,
                                amount = contents.amount.amountUsing(rate),
                            )
                        }
                    } else {
                        if (isV2Enabled) {
                            MessagePaymentV2(
                                verb = Verb.Unknown,
                                amount = KinAmount.newInstance(0, Rate.oneToOne),
                                caption = contents.localizedText,
                                date = date,
                                openTipChat = openTipChat
                            )
                        } else {
                            MessagePayment(
                                verb = Verb.Unknown,
                                amount = KinAmount.newInstance(0, Rate.oneToOne)
                            )
                        }
                    }
                }

                is MessageContent.Localized -> {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = contents.localizedText,
                        date = date
                    )
                }

                is MessageContent.SodiumBox -> {
                    MessageEncrypted(
                        modifier = Modifier.fillMaxWidth(),
                        date = date
                    )
                }

                is MessageContent.Decrypted -> {
                    MessageText(
                        modifier = Modifier.fillMaxWidth(),
                        text = contents.data,
                        date = date
                    )
                }
            }
        }
    }
}

@Composable
private fun MessagePayment(
    modifier: Modifier = Modifier,
    verb: Verb,
    amount: KinAmount,
) {
    Column(
        modifier = modifier
            // payments have an extra 10.dp inner padding
            .padding(CodeTheme.dimens.grid.x2),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (verb == Verb.Returned) {
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
                text = verb.localizedText,
                style = CodeTheme.typography.body1.copy(fontWeight = FontWeight.W500)
            )
        } else {
            Text(
                text = verb.localizedText,
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
}

@Composable
private fun MessagePaymentV2(
    modifier: Modifier = Modifier,
    verb: Verb,
    amount: KinAmount,
    caption: String,
    date: Instant,
    openTipChat: () -> Unit,
) {
    val tipChatsEnabled = LocalBetaFlags.current.tipsChatEnabled
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        CodeTransactionDisplay(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            amount = amount,
            verb = verb
        )
        
        Text(
            text = caption,
            style = CodeTheme.typography.body1.copy(fontWeight = FontWeight.W500)
        )

        if (tipChatsEnabled && verb is Verb.ReceivedTip) {
            CodeButton(
                modifier = Modifier.fillMaxWidth(),
                buttonState = ButtonState.Filled,
                onClick = openTipChat,
                text = stringResource(R.string.action_sayThankYou)
            )
        }

        Text(
            modifier = Modifier.align(Alignment.End),
            text = date.formatTimeRelatively(),
            style = CodeTheme.typography.caption,
            color = BrandLight,
        )
    }
}
@Composable
private fun CodeTransactionDisplay(
    modifier: Modifier = Modifier,
    amount: KinAmount,
    verb: Verb,
) {
    Column(
        modifier = modifier
            .background(CodeTheme.colors.background)
            .padding(vertical = CodeTheme.dimens.grid.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PriceWithFlag(
            currencyCode = amount.rate.currency,
            amount = amount,
            text = { price ->
                val prefix = if (verb.increasesBalance) "+" else "-"
                Text(
                    text = "$prefix$price",
                    color = Color.White,
                    style = CodeTheme.typography.h3
                )
            }
        )
        KinValueHint(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            captionText = FormatUtils.formatWholeRoundDown(amount.kin.toKinValueDouble()),
        )
    }
}

@Composable
private fun MessageText(modifier: Modifier = Modifier, text: String, date: Instant) {
    Column(
        modifier = modifier
            // add top padding to accommodate ascents
            .padding(top = CodeTheme.dimens.grid.x1),
    ) {
        Text(
            text = text,
            style = CodeTheme.typography.body1.copy(fontWeight = FontWeight.W500)
        )
        Text(
            modifier = Modifier.align(Alignment.End),
            text = date.formatTimeRelatively(),
            style = CodeTheme.typography.caption,
            color = BrandLight,
        )
    }
}

@Composable
private fun MessageEncrypted(modifier: Modifier = Modifier, date: Instant) {
    Column(
        modifier = modifier
            // add top padding to accommodate ascents
            .padding(top = CodeTheme.dimens.grid.x1),
    ) {
        Image(
            modifier = Modifier
                .padding(CodeTheme.dimens.grid.x2)
                .size(CodeTheme.dimens.staticGrid.x6)
                .align(Alignment.CenterHorizontally),
            painter = painterResource(id = R.drawable.lock_app_dashed),
            colorFilter = ColorFilter.tint(CodeTheme.colors.onBackground),
            contentDescription = null
        )
        Text(
            modifier = Modifier.align(Alignment.End),
            text = date.formatTimeRelatively(),
            style = CodeTheme.typography.caption,
            color = BrandLight,
        )
    }
}