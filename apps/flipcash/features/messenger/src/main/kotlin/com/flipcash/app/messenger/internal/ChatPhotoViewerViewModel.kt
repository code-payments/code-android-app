package com.flipcash.app.messenger.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.media.ChatPhoto
import com.flipcash.shared.chat.ui.media.photoBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reads the photo behind a viewer route. The route carries only the message's address, so this looks
 * the message up and builds the Coil model the transcript's bubble loads from, which is what lets the
 * viewer open on what the bubble already cached.
 */
@HiltViewModel
internal class ChatPhotoViewerViewModel @Inject constructor(
    private val chatCoordinator: ChatCoordinator,
) : ViewModel() {

    private val _photo = MutableStateFlow<ChatPhoto?>(null)
    val photo: StateFlow<ChatPhoto?> = _photo

    private var loaded: Pair<ChatId, Long>? = null

    fun load(chatId: ChatId, messageId: Long) {
        if (loaded == chatId to messageId) return
        loaded = chatId to messageId
        viewModelScope.launch {
            val message = chatCoordinator.getMessage(chatId, messageId) ?: return@launch
            val rendition = message.content.firstNotNullOfOrNull { it.photoBody() }
                ?.items?.firstOrNull()
                ?.let { item -> item.rendition(com.flipcash.services.models.chat.MediaItemRendition.Role.ORIGINAL) ?: item.renditions.firstOrNull() }
                ?: return@launch
            _photo.value = ChatPhoto(
                chatId = chatId,
                rendition = rendition,
                senderId = message.senderId,
                sealed = message.encryption != null,
                redacted = message.redacted,
            )
        }
    }
}
