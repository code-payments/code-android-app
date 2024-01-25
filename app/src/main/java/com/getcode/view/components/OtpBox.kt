package com.getcode.view.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.WindowSizeClass
import com.getcode.util.rememberedClickable

@Composable
fun OtpBox(
    character: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
) {

    val height = when (CodeTheme.dimens.heightWindowSizeClass) {
        WindowSizeClass.COMPACT -> 45
        else -> 60
    }
    val width = when (CodeTheme.dimens.widthWindowSizeClass) {
        WindowSizeClass.COMPACT -> 30
        else -> 42
    }

    Box(
        modifier = modifier
            .padding(CodeTheme.dimens.grid.x1)
            .height(height.dp)
            .width(width.dp)
            .clip(CodeTheme.shapes.small)
            .rememberedClickable(onClick = onClick)
            .border(
                border = if (isHighlighted)
                    BorderStroke(CodeTheme.dimens.thickBorder, color = BrandLight.copy(alpha = 0.8f))
                else
                    BorderStroke(CodeTheme.dimens.border, color = BrandLight.copy(alpha = 0.4f)),
                shape = CodeTheme.shapes.small
            )
            .background(Color.White.copy(alpha = 0.1f)),
    ) {
        Text(
            text = character,
            modifier = Modifier
                .align(Alignment.Center),
            style = CodeTheme.typography.h6.copy(fontWeight = FontWeight.Normal),
            color = Color.White,
        )
    }
}