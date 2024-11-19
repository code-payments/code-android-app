package com.getcode.ui.components.chat.messagecontents

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.MessageNodeDefaults

@Composable
internal fun AnnouncementMessage(
    modifier: Modifier = Modifier,
    text: String,
) {
    BoxWithConstraints(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = maxWidth * 0.78f)
                .border(
                    width = CodeTheme.dimens.border,
                    color = CodeTheme.colors.tertiary,
                    shape = MessageNodeDefaults.DefaultShape
                )
                .padding(CodeTheme.dimens.grid.x2),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                style = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.W500)
            )
        }
    }
}