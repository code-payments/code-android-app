package com.getcode.ui.components

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
import com.getcode.theme.CodeTheme
import com.getcode.theme.WindowSizeClass
import com.getcode.ui.utils.rememberedClickable

@Composable
fun OtpBox(
    character: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
) {

    val height = when (CodeTheme.dimens.heightWindowSizeClass) {
        WindowSizeClass.COMPACT -> CodeTheme.dimens.grid.x9
        else -> CodeTheme.dimens.grid.x11
    }

    Box(
        modifier = modifier
            .padding(CodeTheme.dimens.grid.x1)
            .height(height)
            .width(CodeTheme.dimens.grid.x7)
            .clip(CodeTheme.shapes.small)
            .rememberedClickable(onClick = onClick)
            .border(
                border = if (isHighlighted)
                    BorderStroke(CodeTheme.dimens.thickBorder, color = CodeTheme.colors.brandLight.copy(alpha = 0.7f))
                else
                    BorderStroke(CodeTheme.dimens.border, color = CodeTheme.colors.brandLight.copy(alpha = 0.3f)),
                shape = CodeTheme.shapes.small
            )
            .background(Color.White.copy(alpha = 0.1f)),
    ) {
        Text(
            text = character,
            modifier = Modifier
                .align(Alignment.Center),
            style = CodeTheme.typography.displayExtraSmall,
            color = Color.White,
        )
    }
}