package com.getcode.view.main.giveKin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.getcode.theme.White
import com.getcode.util.WindowSize

@Composable
fun AmountText(
    windowSize: WindowSize,
    currencyResId: Int?,
    amountText: String
) {
    val textStyleH1 = MaterialTheme.typography.h1.copy(textAlign = TextAlign.Center)
    var scaledTextStyle by remember { mutableStateOf(textStyleH1) }
    var isReadyToDraw by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        if (currencyResId != null && currencyResId > 0) {
            Image(
                modifier = Modifier
                    .align(CenterVertically)
                    .size(25.dp)
                    .clip(RoundedCornerShape(15.dp)),
                painter = painterResource(currencyResId),
                contentDescription = ""
            )
        }
        Text(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight()
                .padding(start = 15.dp)
                .padding(
                    vertical =
                    when (windowSize) {
                        WindowSize.SMALL -> 5.dp
                        WindowSize.REGULAR -> 15.dp
                    }
                )
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