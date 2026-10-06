package com.flipcash.app.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.messenger.internal.ChatPhotoViewerViewModel
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ui.media.ChatMediaViewer
import com.flipcash.shared.chat.ui.media.rememberSharePhoto
import com.getcode.navigation.core.LocalCodeNavigator

/** The full-screen photo, behind `AppRoute.Messaging.PhotoViewer`. Black, dismissed by back or a pull. */
@Composable
fun ChatPhotoViewerScreen(chatId: ChatId, messageId: Long) {
    val viewModel = hiltViewModel<ChatPhotoViewerViewModel>()
    LaunchedEffect(chatId, messageId) { viewModel.load(chatId, messageId) }
    val photo by viewModel.photo.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current
    var fullImageLoaded by remember { mutableStateOf(false) }
    val share = rememberSharePhoto(photo)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        ChatMediaViewer(
            model = photo,
            fullImageLoaded = fullImageLoaded,
            onFullImageLoaded = { fullImageLoaded = true },
            onShare = share,
            onDismiss = { navigator.pop() },
        )
    }
}
