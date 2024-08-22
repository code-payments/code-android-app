package com.getcode.ui.components

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import com.getcode.theme.CodeTheme


@Preview
@Composable
fun CodeSeedView(
    modifier: Modifier = Modifier,
    words: Array<String> = arrayOf(),
    isVisible: Boolean = true
) {
    val totalRows = 6
    Row(modifier = modifier) {
        for (r in 0..1) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .wrapContentWidth(CenterHorizontally)
            ) {
                for (c in 1..totalRows) {
                    val number = c + (r * totalRows)
                    val index = number - 1
                    val text = if (index < words.size) words[index] else ""

                    SeedItem(number = number, text = text, isVisible = isVisible)
                }
            }
        }
    }
}

@Composable
private fun SeedItem(
    number: Int,
    text: String,
    isVisible: Boolean
) {
    Row(modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x2)) {
        Text(
            text = "$number.",
            modifier = Modifier.padding(end = CodeTheme.dimens.grid.x3)
                .width(CodeTheme.dimens.grid.x6),
            style = CodeTheme.typography.textMedium.copy(
                textAlign = TextAlign.End
            ),
            color = CodeTheme.colors.textSecondary
        )
        Box {
            Text(
                modifier = Modifier.alpha(if (isVisible) 1f else 0f),
                text = text,
                style = CodeTheme.typography.textMedium
            )
            Text(
                modifier = Modifier.alpha(if (!isVisible) 1f else 0f),
                text = "-".repeat(text.length),
                style = CodeTheme.typography.textMedium
            )
        }
    }
}
