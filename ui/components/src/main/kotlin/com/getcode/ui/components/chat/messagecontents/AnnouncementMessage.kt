package com.getcode.ui.components.chat.messagecontents

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Pill

@Composable
internal fun AnnouncementMessage(
    modifier: Modifier = Modifier,
    text: String,
) {
    BoxWithConstraints(modifier = modifier) {
        Pill(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = maxWidth * 0.78f),
            text = text,
            textStyle = CodeTheme.typography.caption.copy(textAlign = TextAlign.Center),
            backgroundColor = CodeTheme.colors.surfaceVariant,
            contentColor = CodeTheme.colors.textSecondary
        )
    }
}