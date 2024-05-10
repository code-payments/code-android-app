package com.getcode.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.getcode.theme.BrandDark
import com.getcode.theme.CodeTheme

@Composable
fun AnnouncementMessage(
    modifier: Modifier = Modifier,
    text: String,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .background(
                    color = BrandDark,
                    shape = MessageNodeDefaults.DefaultShape
                )
                .padding(CodeTheme.dimens.grid.x2),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                style = CodeTheme.typography.body1.copy(fontWeight = FontWeight.W500)
            )
        }
    }
}