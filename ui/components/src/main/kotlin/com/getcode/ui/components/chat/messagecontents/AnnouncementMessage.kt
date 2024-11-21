package com.getcode.ui.components.chat.messagecontents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
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
            contentPadding = PaddingValues(CodeTheme.dimens.grid.x1),
            backgroundColor = CodeTheme.colors.surfaceVariant,
            contentColor = CodeTheme.colors.textSecondary
        )
//        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
//            Pill(
//                text = date,
//                backgroundColor = CodeTheme.colors.surfaceVariant
//            )
//        }
//        Column(
//            modifier = Modifier
//                .align(Alignment.Center)
//                .widthIn(max = maxWidth * 0.78f),
//            verticalArrangement = Arrangement.Center
//        ) {
//            Text(
//                text = text,
//                textAlign = TextAlign.Center,
//                style = CodeTheme.typography.caption,
//                color = CodeTheme.colors.textSecondary
//            )
//        }
    }
}