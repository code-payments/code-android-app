package com.getcode.ui.emojis

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.libs.emojis.generated.Emojis
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem

class EmojiModal(private val onSelected: (String) -> Unit) : Screen {
    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(CodeTheme.dimens.modalHeightRatio)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
            ) {
                EmojiGarden(onClick = onSelected)
            }
        }
    }
}

@Composable
private fun EmojiGarden(onClick: (String) -> Unit) {
    val emojis = remember { Emojis.categorizedNoSkinTones }
    LazyVerticalGrid(
        modifier = Modifier.navigationBarsPadding(),
        columns = GridCells.Adaptive(40.dp)
    ) {
        emojis.entries.toList().fastForEach { (category, emojis) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x2),
                    text = category,
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary
                )
            }
            items(emojis.values.flatten()) { (emoji, name) ->
                EmojiRender(
                    emoji = emoji,
                    showBackground = false,
                    onClick = { onClick(emoji) }
                )
            }
        }
    }
}

@Preview
@Composable
private fun Preview_EmojiGarden() {
    DesignSystem {
        EmojiGarden { }
    }
}