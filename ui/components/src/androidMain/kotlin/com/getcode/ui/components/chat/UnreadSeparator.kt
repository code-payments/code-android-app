package com.getcode.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R

@Composable
internal fun UnreadSeparator(count: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(CodeTheme.colors.divider)) {
        Text(
            modifier = Modifier
                .padding(vertical = CodeTheme.dimens.grid.x2)
                .align(Alignment.Center),
            text = pluralStringResource(R.plurals.title_conversationUnreadCount, count, count),
            style = CodeTheme.typography.caption.copy(fontWeight = FontWeight.W700),
            color = CodeTheme.colors.textSecondary
        )
    }
}