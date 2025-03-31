package com.getcode.ui.components.text

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.bolded
import com.getcode.ui.components.R


object AmountSizeStore {
    private val cachedSizes = mutableMapOf<String, TextUnit>()
    fun hasCachedSize(amountText: String) = cachedSizes[amountText] != null
    fun remember(amountText: String, size: TextUnit) {
        cachedSizes[amountText] = size
    }

    fun lookup(amountText: String) = cachedSizes[amountText]
}

@Composable
fun AmountText(
    modifier: Modifier = Modifier,
    currencyResId: Int?,
    amountText: String,
    isClickable: Boolean = false,
    textStyle: TextStyle = CodeTheme.typography.displayMedium.bolded(),
) {
    val centeredText = textStyle.copy(textAlign = TextAlign.Center)

    val cachedSize: TextUnit? by remember(amountText) {
        derivedStateOf { AmountSizeStore.lookup(amountText) }
    }

    var scaledTextStyle by remember(amountText, cachedSize) {
        mutableStateOf(
            cachedSize?.let { centeredText.copy(fontSize = it) } ?: centeredText
        )
    }

    var isReadyToDraw by remember(amountText) {
        mutableStateOf(AmountSizeStore.hasCachedSize(amountText))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        horizontalArrangement = Arrangement.Center
    ) {
        if (currencyResId != null && currencyResId > 0) {
            Image(
                modifier = Modifier
                    .align(CenterVertically)
                    .requiredSize(CodeTheme.dimens.staticGrid.x7)
                    .clip(CircleShape),
                painter = painterResource(currencyResId),
                contentDescription = ""
            )
        }
        if (isClickable) {
            Image(
                modifier = Modifier
                    .padding(end = CodeTheme.dimens.grid.x2)
                    .requiredSize(CodeTheme.dimens.grid.x5)
                    .align(CenterVertically),
                painter = painterResource(R.drawable.ic_dropdown),
                contentDescription = ""
            )
        } else {
            Spacer(modifier = Modifier.requiredWidth(CodeTheme.dimens.grid.x3))
        }

        Text(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight()
                .padding(vertical = CodeTheme.dimens.grid.x3)
                .drawWithCache {
                    onDrawWithContent {
                        if (isReadyToDraw) drawContent()
                    }
                },
            text = amountText,
            color = White,
            style = scaledTextStyle,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { textLayoutResult: TextLayoutResult ->
                if (!isReadyToDraw) {
                    if (textLayoutResult.didOverflowWidth) {
                        scaledTextStyle =
                            scaledTextStyle.copy(fontSize = scaledTextStyle.fontSize * 0.9)
                    } else {
                        AmountSizeStore.remember(amountText, scaledTextStyle.fontSize)
                        isReadyToDraw = true
                    }
                }
            }
        )
    }
}