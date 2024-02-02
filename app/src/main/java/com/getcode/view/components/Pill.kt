package com.getcode.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.getcode.theme.Black50
import com.getcode.theme.CodeTheme
import com.getcode.theme.xxl

@Composable
fun Pill(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Black50,
    contentColor: Color = Color.White,
    text: String,
    textStyle: TextStyle = CodeTheme.typography.body2
) {
    Row(
        modifier = modifier
            .wrapContentSize()
            .clip(CodeTheme.shapes.xxl)
            .background(backgroundColor)
            .padding(
                horizontal = CodeTheme.dimens.grid.x2,
                vertical = CodeTheme.dimens.grid.x1
            ),
    ) {
        Text(
            text = text,
            style = textStyle,
            color = contentColor,
        )
    }
}