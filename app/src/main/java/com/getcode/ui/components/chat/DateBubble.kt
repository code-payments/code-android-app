package com.getcode.ui.components.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.getcode.theme.BrandDark
import com.getcode.ui.components.Pill

@Composable
internal fun DateBubble(
    modifier: Modifier = Modifier,
    date: String,
) = Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Pill(
        text = date,
        backgroundColor = BrandDark
    )
}