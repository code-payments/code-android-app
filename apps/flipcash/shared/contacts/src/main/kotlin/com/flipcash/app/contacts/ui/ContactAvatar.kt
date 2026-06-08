package com.flipcash.app.contacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.getcode.theme.CodeTheme


@Composable
fun ContactAvatar(
    photoUri: String?,
    displayName: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(CodeTheme.colors.contactAvatar.colors)
        )
    ) {
        if (photoUri != null) {
            var isError by rememberSaveable(photoUri) { mutableStateOf(false) }
            if (!isError) {
                val context = LocalContext.current
                val request = remember(photoUri) {
                    ImageRequest.Builder(context)
                        .crossfade(true)
                        .data(photoUri.toUri())
                        .build()
                }
                AsyncImage(
                    modifier = Modifier.matchParentSize(),
                    model = request,
                    contentDescription = null,
                    onError = { isError = true },
                )
            }
            if (isError) {
                InitialsText(displayName)
            }
        } else {
            InitialsText(displayName)
        }
    }
}

@Composable
private fun BoxScope.InitialsText(displayName: String) {
    val initials = remember(displayName) {
        displayName.split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }
    }
    Text(
        modifier = Modifier.align(Alignment.Center),
        text = initials,
        style = CodeTheme.typography.textSmall,
        color = CodeTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}