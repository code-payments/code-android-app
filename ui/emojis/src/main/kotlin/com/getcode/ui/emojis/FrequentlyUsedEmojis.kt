package com.getcode.ui.emojis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import com.getcode.theme.CodeTheme

@Composable
fun FrequentlyUsedEmojis(
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
    onViewAll: () -> Unit,
) {
    val usageController = LocalEmojiUsageController.current
    val frequentlyUsedEmojis = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        frequentlyUsedEmojis.addAll(usageController.mostUsedEmojis())
    }

    LazyHorizontalGrid(
        rows = GridCells.Adaptive(CodeTheme.dimens.grid.x8),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .padding(vertical = CodeTheme.dimens.grid.x2)
            .height(CodeTheme.dimens.grid.x9),
        contentPadding = PaddingValues(horizontal = CodeTheme.dimens.inset)
    ) {
        items(frequentlyUsedEmojis) { emoji ->
            EmojiRender(
                emoji = emoji,
                onClick = { onSelect(emoji) }
            )
        }
        item {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(
                        width = CodeTheme.dimens.grid.x9,
                        height = CodeTheme.dimens.grid.x8,
                    )
                    .clip(CircleShape)
                    .clickable { onViewAll() }
                    .background(
                        color = CodeTheme.colors.brandDark,
                        shape = CircleShape
                    ).padding(CodeTheme.dimens.grid.x2)
            ) {
                Image(
                    modifier = Modifier.matchParentSize(),
                    imageVector = Icons.Default.AddReaction,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(CodeTheme.colors.textSecondary)
                )
            }
        }
    }
}
