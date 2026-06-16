package com.flipcash.app.contacts.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.min
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.flipcash.app.contacts.device.DeviceContact
import com.getcode.theme.CodeTheme

@Composable
fun ContactAvatar(
    contact: DeviceContact?,
    modifier: Modifier = Modifier,
) {
    if (contact == null || contact.isUnknown) {
        Box(
            modifier = modifier
                .background(Brush.linearGradient(CodeTheme.colors.contactAvatar.colors)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Image(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                colorFilter = ColorFilter.tint(CodeTheme.colors.textSecondary),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1.2f
                        scaleX = scale
                        scaleY = scale
                        translationY = size.height * 0.18f
                    },
                contentScale = ContentScale.Fit,
            )
        }
    } else {
        ContactAvatar(
            modifier = Modifier
                .border(
                    CodeTheme.dimens.border,
                    CodeTheme.colors.divider,
                    CircleShape,
                ).then(modifier),
            photoUri = contact.photoUri,
            displayName = contact.displayName,
        )
    }
}

@Composable
fun ContactAvatar(
    photoUri: String?,
    displayName: String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
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
private fun BoxWithConstraintsScope.InitialsText(displayName: String) {
    val initials = remember(displayName) {
        displayName.split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }
    }
    val fontSize = with(LocalDensity.current) {
        (min(maxWidth, maxHeight) * 0.38f).toSp()
    }
    Text(
        modifier = Modifier.align(Alignment.Center),
        text = initials,
        fontSize = fontSize,
        color = CodeTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}