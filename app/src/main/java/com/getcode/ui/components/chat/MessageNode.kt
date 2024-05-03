package com.getcode.ui.components.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.getcode.model.orOneToOne
import com.getcode.theme.Alert
import com.getcode.theme.BrandDark
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraLarge
import com.getcode.theme.extraSmall
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.ui.utils.unboundedClickable
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
    thankUser: () -> Unit,
    openMessageChat: () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(vertical = CodeTheme.dimens.grid.x1)
    ) {
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
                    MessagePayment(
                        contents = contents,
                        thankUser = thankUser,
                        date = date,
                        openMessageChat = openMessageChat
                    )
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
    contents: MessageContent.Exchange,
    date: Instant,
    thankUser: () -> Unit,
    openMessageChat: () -> Unit,
) {
    Column(
        modifier = modifier
            // payments have an extra 10.dp inner padding
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

        val tipChatsEnabled = LocalBetaFlags.current.tipsChatEnabled
        var thanked by remember(contents.thanked) {
            mutableStateOf(contents.thanked)
        }

        val sendThanks = {
            thanked = true
            thankUser()
        }

        if (tipChatsEnabled && contents.verb is Verb.ReceivedTip) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
            ) {
                CodeButton(
                    modifier = Modifier.weight(1f),
                    enabled = !thanked,
                    buttonState = if (thanked) ButtonState.Bordered else ButtonState.Filled,
                    onClick = sendThanks,
                    shape = CodeTheme.shapes.extraSmall,
                    text = if (thanked) stringResource(R.string.action_thanked) else stringResource(R.string.action_thank)
                )
                CodeButton(
                    modifier = Modifier.weight(1f),
                    buttonState = ButtonState.Filled,
                    onClick = openMessageChat,
                    shape = CodeTheme.shapes.extraSmall,
                    text = stringResource(R.string.action_message)
                )
            }
            Text(
                modifier = Modifier.align(Alignment.End),
                text = date.formatTimeRelatively(),
                style = CodeTheme.typography.caption,
                color = BrandLight,
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
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