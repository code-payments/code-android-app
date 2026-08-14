package com.flipcash.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall

/**
 * Controls how a [TileButton] arranges its icon and label.
 */
enum class TileButtonStyle {
    /** Icon centered above the label — the default compact tile. */
    Centered,

    /**
     * Icon pinned to the top-start and the label to the bottom-start, spread across the
     * height of the tile. Used for the larger call-to-action tiles.
     */
    Spread,
}

@Composable
fun TileButton(
    text: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    style: TileButtonStyle = TileButtonStyle.Centered,
    contentPadding: PaddingValues = when (style) {
        TileButtonStyle.Centered -> PaddingValues(vertical = CodeTheme.dimens.grid.x6)
        TileButtonStyle.Spread -> PaddingValues(CodeTheme.dimens.grid.x4)
    },
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(CodeTheme.shapes.extraSmall)
            .background(
                color = CodeTheme.colors.surfaceVariant,
                shape = CodeTheme.shapes.extraSmall,
            )
            .clickable(onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        when (style) {
            TileButtonStyle.Centered -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TileButtonIcon(icon)
                    Text(
                        text = text,
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textMain,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            TileButtonStyle.Spread -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.Start,
                ) {
                    TileButtonIcon(icon, style)
                    Text(
                        text = text,
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textMain,
                    )
                }
            }
        }
    }
}

@Composable
private fun TileButtonIcon(icon: Painter, style: TileButtonStyle = TileButtonStyle.Centered) {
    val size = when (style) {
        TileButtonStyle.Centered -> 32.dp
        TileButtonStyle.Spread -> 24.dp
    }
    Image(
        modifier = Modifier.size(size),
        painter = icon,
        contentDescription = null,
        colorFilter = ColorFilter.tint(CodeTheme.colors.textMain),
    )
}
