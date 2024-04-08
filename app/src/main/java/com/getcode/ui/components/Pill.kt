package com.getcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    shape: CornerBasedShape = CircleShape,
    text: String,
    textStyle: TextStyle = CodeTheme.typography.caption,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = CodeTheme.dimens.grid.x2,
        vertical = CodeTheme.dimens.grid.x1
    )
) {
    Pill(
        modifier = modifier,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        contentPadding = contentPadding,
        shape = shape,
        content = {
            Text(
                text = text,
                style = textStyle,
            )
        }
    )
}

@Composable
fun Pill(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Black50,
    contentColor: Color = Color.White,
    shape: CornerBasedShape = CircleShape,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = CodeTheme.dimens.grid.x2,
        vertical = CodeTheme.dimens.grid.x1
    ),
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .wrapContentSize()
            .clip(shape)
            .background(backgroundColor)
            .then(modifier)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}