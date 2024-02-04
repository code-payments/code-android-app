package com.getcode.view.components.chat

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.BuildConfig
import com.getcode.LocalExchange
import com.getcode.R
import com.getcode.model.KinAmount
import com.getcode.model.MessageContent
import com.getcode.model.Rate
import com.getcode.model.Verb
import com.getcode.theme.BrandDark
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.util.formatTimeRelatively
import com.getcode.view.main.home.components.PriceWithFlag
import kotlinx.datetime.Instant

object MessageNodeDefaults {

    @Composable
    fun verticalPadding(
        isPreviousSameMessage: Boolean,
        isNextSameMessage: Boolean
    ): PaddingValues {
        return when {
            isPreviousSameMessage && isNextSameMessage -> PaddingValues(vertical = CodeTheme.dimens.grid.x1 / 2)
            isPreviousSameMessage -> PaddingValues(top = CodeTheme.dimens.grid.x1 / 2)
            isNextSameMessage -> PaddingValues(bottom = CodeTheme.dimens.grid.x1 / 2)
            else -> PaddingValues(vertical = CodeTheme.dimens.grid.x1)
        }
    }

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

private val MessageContent.widthFraction: Float
    get() = when (this) {
        is MessageContent.Exchange -> 0.895f
        is MessageContent.Localized -> 0.8358f
        MessageContent.SodiumBox -> 0.8358f
    }

@Composable
fun MessageNode(
    modifier: Modifier = Modifier,
    contents: MessageContent,
    date: Instant,
    isPreviousSameMessage: Boolean,
    isNextSameMessage: Boolean
) {
    Box(
        modifier = modifier
            .padding(
                MessageNodeDefaults.verticalPadding(
                    isPreviousSameMessage = isPreviousSameMessage,
                    isNextSameMessage = isNextSameMessage
                )
            )
    ) {
        val exchange = LocalExchange.current

        Box(
            modifier = Modifier
                .fillMaxWidth(contents.widthFraction)
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
                    if (rate != null) {
                        MessagePayment(
                            verb = contents.verb,
                            amount = contents.amount.amountUsing(rate)
                        )
                    } else {
                        MessagePayment(
                            verb = Verb.Unknown,
                            amount = KinAmount.newInstance(0, Rate.oneToOne)
                        )
                    }
                }

                is MessageContent.Localized -> {
                    MessageText(text = contents.localizedText, date = date)
                }

                MessageContent.SodiumBox -> {
                    MessageText(text = contents.localizedText, date = date)
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

private val Verb.localizedText: String
    @SuppressLint("DiscouragedApi")
    @Composable get() = with(LocalContext.current) context@{
        if (this@localizedText == Verb.Unknown) stringResource(id = R.string.title_unknown)
        val resId = resources.getIdentifier(
            "subtitle_verb_${this@localizedText.toString().lowercase()}",
            "string",
            BuildConfig.APPLICATION_ID
        ).let { if (it == 0) null else it }

        resId?.let { getString(it) }.orEmpty()
    }