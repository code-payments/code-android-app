package com.getcode.ui.components.text

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R
import com.getcode.ui.core.drawWithGradient

@Composable
fun ExpandableText(
    text: String,
    style: TextStyle,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    collapsedMaxLines: Int = 4,
    onToggle: () -> Unit,
) {
    var hasOverflow by remember(text) { mutableStateOf(false) }

    Box(modifier = modifier.animateContentSize()) {
        Column {
            if (isExpanded) {
                Text(
                    modifier = textModifier
                        .padding(contentPadding),
                    text = text,
                    style = style,
                    color = CodeTheme.colors.textSecondary,
                )
            } else {
                Box {
                    Text(
                        modifier = textModifier
                            .padding(contentPadding),
                        text = text,
                        overflow = TextOverflow.Clip,
                        style = style,
                        color = CodeTheme.colors.textSecondary,
                        onTextLayout = { textLayoutResult ->
                            hasOverflow = textLayoutResult.hasVisualOverflow
                        },
                        maxLines = collapsedMaxLines
                    )

                    if (hasOverflow) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .align(Alignment.BottomCenter)
                                .drawWithGradient(
                                    color = CodeTheme.colors.background,
                                    startY = { 0f },
                                    endY = { size.height }

                                )
                        )
                    }
                }
            }

            if (hasOverflow) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CodeTheme.dimens.grid.x1),
                    color = Color.Transparent
                ) {
                    ExpandVisibilityButton(
                        isExpanded = isExpanded,
                        modifier = Modifier
                            .padding(contentPadding),
                        onToggle = onToggle
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandVisibilityButton(
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    Box(modifier = modifier.background(Color.Transparent)) {
        Row(
            modifier = Modifier.clickable { onToggle() },
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
        ) {
            Text(
                text = if (isExpanded) stringResource(R.string.action_showLess) else stringResource(R.string.action_showMore),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = CodeTheme.colors.textMain
            )
        }
    }
}
