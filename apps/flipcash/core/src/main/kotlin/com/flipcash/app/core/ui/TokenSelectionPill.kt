package com.flipcash.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.getcode.opencode.model.financial.Token
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.R
import com.getcode.ui.core.rememberedClickable

@Composable
fun TokenSelectionPill(token: Token?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .rememberedClickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                space = CodeTheme.dimens.grid.x1,
                alignment = Alignment.CenterHorizontally
            )
        ) {
            token?.let {
                TokenIconWithName(
                    token = token,
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
}

@Composable
fun TokenSelectionPill(
    tokenName: String,
    tokenImageUrl: String?,
    tokenSymbol: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .rememberedClickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                space = CodeTheme.dimens.grid.x1,
                alignment = Alignment.CenterHorizontally
            )
        ) {
            TokenIconWithName(
                tokenName = tokenName,
                tokenImageUrl = tokenImageUrl,
                tokenSymbol = tokenSymbol,
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