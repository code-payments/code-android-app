package com.getcode.view.main.giveKin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.displayLarge


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
    amountText: String
) {
    val displayLarge = CodeTheme.typography.displayLarge.copy(textAlign = TextAlign.Center)
    var scaledTextStyle by remember(amountText) {
        val cachedSize = AmountSizeStore.lookup(amountText)
        val style = displayLarge.copy(fontSize = cachedSize ?: displayLarge.fontSize)
        mutableStateOf(style)
    }
    var isReadyToDraw by rememberSaveable(amountText) { mutableStateOf(AmountSizeStore.hasCachedSize(amountText)) }

    Row(
        modifier = Modifier.fillMaxWidth().then(modifier),
        horizontalArrangement = Arrangement.Center
    ) {
        if (currencyResId != null && currencyResId > 0) {
            Image(
                modifier = Modifier
                    .align(CenterVertically)
                    .size(CodeTheme.dimens.staticGrid.x5)
                    .clip(RoundedCornerShape(CodeTheme.dimens.staticGrid.x3)),
                painter = painterResource(currencyResId),
                contentDescription = ""
            )
        }
        Text(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight()
                .padding(start = CodeTheme.dimens.grid.x3)
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