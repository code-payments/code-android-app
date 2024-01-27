package com.getcode.view.main.giveKin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.displayLarge

@Composable
fun AmountText(
    currencyResId: Int?,
    amountText: String,
    textStyle: TextStyle = CodeTheme.typography.h1,
) {
    val displayLarge = textStyle.copy(textAlign = TextAlign.Center)
    var scaledTextStyle by remember { mutableStateOf(displayLarge) }
    var isReadyToDraw by remember { mutableStateOf(false) }


    Row(
        modifier = Modifier.fillMaxWidth(),
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
                .drawWithContent {
                    if (isReadyToDraw) drawContent()
                },
            text = amountText,
            color = White,
            style = scaledTextStyle,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { textLayoutResult: TextLayoutResult ->
                if (textLayoutResult.didOverflowWidth) {
                    scaledTextStyle =
                        scaledTextStyle.copy(fontSize = scaledTextStyle.fontSize * 0.9)
                } else {
                    isReadyToDraw = true
                }
            }
        )
    }
}