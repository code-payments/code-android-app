package com.flipcash.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.getcode.opencode.model.financial.Token
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.R

@Composable
fun TokenSelectionPill(
    token: Token?,
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = CodeTheme.dimens.grid.x3,
        vertical = CodeTheme.dimens.grid.x2,
    ),
    textStyle: TextStyle = CodeTheme.typography.screenTitle,
    imageSize: Dp = CodeTheme.dimens.staticGrid.x5,
    onClick: () -> Unit
) {
    // [contentPadding] only applies when a background is supplied, so call sites without one
    // (e.g. app-bar titles) keep their original wrap-content layout.
    val hasBackground = background != Color.Transparent
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color = background, shape = CircleShape)
            .clickable { onClick() }
            .then(if (hasBackground) Modifier.padding(contentPadding) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            space = CodeTheme.dimens.grid.x1,
            alignment = Alignment.CenterHorizontally
        )
    ) {
        token?.let {
            TokenIconWithName(
                token = token,
                imageSize = imageSize,
                spacing = CodeTheme.dimens.grid.x1,
                textStyle = textStyle,
            )

            Image(
                modifier = Modifier
                    .width(CodeTheme.dimens.grid.x4),
                painter = painterResource(R.drawable.ic_dropdown),
                contentDescription = ""
            )
        }
    }
}

@Composable
fun TokenSelectionPill(
    tokenName: String,
    tokenImageUrl: String?,
    tokenSymbol: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                space = CodeTheme.dimens.grid.x1,
                alignment = Alignment.CenterHorizontally
            )
        ) {
            TokenIconWithName(
                tokenName = tokenName,
                tokenImage = tokenImageUrl,
                imageSize = CodeTheme.dimens.staticGrid.x5,
                spacing = CodeTheme.dimens.grid.x1,
            )

            Image(
                modifier = Modifier
                    .width(CodeTheme.dimens.grid.x4),
                painter = painterResource(R.drawable.ic_dropdown),
                contentDescription = ""
            )
        }
    }
}