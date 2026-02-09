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
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.error
import com.getcode.opencode.model.financial.Token
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R

@Composable
fun TokenIconWithName(
    tokenName: String,
    tokenImageUrl: String?,
    tokenSymbol: String,
    imageSize: Dp,
    modifier: Modifier = Modifier,
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
            imageUrl = tokenImageUrl,
            symbol = tokenSymbol,
            modifier = Modifier.size(imageSize)
        )
        Text(
            text = tokenName,
            style = textStyle,
            color = textColor,
        )
    }
}

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
    TokenIconWithName(
        tokenName = displayName(token),
        tokenImageUrl = token.imageUrl,
        tokenSymbol = token.symbol,
        imageSize = imageSize,
        modifier = modifier,
        textStyle = textStyle,
        textColor = textColor,
        spacing = spacing
    )
}

@Composable
fun TokenIcon(
    token: Token,
    modifier: Modifier = Modifier
) {
    TokenIcon(
        imageUrl = token.imageUrl,
        symbol = token.symbol,
        modifier = modifier
    )
}

@Composable
fun TokenIcon(
    imageUrl: String?,
    symbol: String,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        modifier = modifier.clip(CircleShape),
        model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(imageUrl)
            .crossfade(false)
            .error(R.drawable.ic_placeholder_user)
            .memoryCacheKey(symbol)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCacheKey(symbol)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build(),
        contentDescription = null,
    )
}