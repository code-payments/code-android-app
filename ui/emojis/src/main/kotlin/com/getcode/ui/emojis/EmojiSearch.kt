package com.getcode.ui.emojis

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import com.getcode.libs.emojis.generated.Emoji
import com.getcode.libs.search.levenshtein
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.verticalScrollStateGradient

fun List<Emoji>.fuzzySearch(query: String): List<Emoji> {
    return map { emoji ->
        emoji to (emoji.keywords.minOfOrNull { keyword ->
            if (keyword.lowercase().startsWith(query.lowercase())) return@minOfOrNull 0
            if (keyword.lowercase().contains(query.lowercase())) return@minOfOrNull 0
            levenshtein(query, keyword)
        } ?: Int.MAX_VALUE)
    }.filter { it.second < 3 }.sortedBy { it.second }.map { it.first }
}

@Composable
fun EmojiSearchResults(
    results: List<Emoji>?,
    onSelected: (String) -> Unit,
) {
    val state = rememberLazyListState()
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScrollStateGradient(state, CodeTheme.colors.background),
        state = state,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        contentPadding = PaddingValues(
            top = CodeTheme.dimens.inset,
            start = CodeTheme.dimens.inset,
            end = CodeTheme.dimens.inset,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + CodeTheme.dimens.grid.x2
        )
    ) {
        items(results.orEmpty()) { emoji ->
            Row(
                modifier = Modifier
                    .clickable { onSelected(emoji.unicode) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
            ) {
                val emojiText = if (LocalInspectionMode.current) {
                    emoji.unicode
                } else {
                    processEmoji(emoji.unicode).toString()
                }

                Text(
                    text = emojiText,
                    color = CodeTheme.colors.textMain,
                    style = CodeTheme.typography.textLarge.copy(fontWeight = FontWeight.W700),
                )

                Text(
                    text = emoji.name,
                    color = CodeTheme.colors.textSecondary,
                    style = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.W500)
                )
            }
        }
    }
}