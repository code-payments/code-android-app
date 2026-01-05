package com.flipcash.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.error
import com.getcode.opencode.model.financial.Token
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R

@Composable
fun TokenIconWithName(
    token: Token,
    imageSize: Dp,
    modifier: Modifier = Modifier,
    displayName: (Token) -> String = { it.name },
    textStyle: TextStyle = CodeTheme.typography.screenTitle,
    textColor: Color = CodeTheme.colors.textMain,
    spacing: Dp = 0.dp,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TokenIcon(
            token = token,
            modifier = Modifier.size(imageSize)
        )
        Text(
            text = displayName(token),
            style = textStyle,
            color = textColor,
        )
    }
}

@Composable
fun TokenIcon(
    token: Token,
    modifier: Modifier
) {
    AsyncImage(
        modifier = modifier.clip(CircleShape),
        model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(token.imageUrl)
            .error(R.drawable.ic_placeholder_user)
            .placeholderMemoryCacheKey(token.symbol)
            .build(),
        contentDescription = null,
    )
}