package com.flipcash.shared.chat.share

import com.flipcash.app.blob.PickedPhoto
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.media.ChatMediaUploads
import com.flipcash.shared.chat.media.MediaSendProgress
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * [ShareGateway] over the calls the chat screen itself makes: [ChatCoordinator.sendMessage] for
 * text, [ChatMediaUploads.stage] and [ChatCoordinator.sendMedia] for photos. Both consult
 * `OutgoingEncryption`, so a chat that encrypts gets sealed messages and sealed photo uploads here
 * exactly as when the same content is typed in it.
 */
class ChatShareGateway @Inject constructor(
    private val coordinator: ChatCoordinator,
    private val uploads: ChatMediaUploads,
) : ShareGateway {

    override suspend fun sendText(chatId: ChatId, text: String): Result<Unit> =
        coordinator.sendMessage(chatId, text).map { }

    override fun stagePhoto(chatId: ChatId, photo: PickedPhoto): String = uploads.stage(chatId, photo)

    override suspend fun sendPhotos(chatId: ChatId, chipIds: List<String>, caption: String): Result<List<String>> =
        coordinator.sendMedia(chatId, chipIds, caption, replyToMessageId = null)

    override suspend fun awaitPhotos(messageIds: List<String>): Map<String, PhotoOutcome> {
        val settled = coordinator.observeMediaSendProgress().first { progress ->
            messageIds.all { progress[it] is MediaSendProgress.Sent || progress[it] is MediaSendProgress.Failed }
        }
        return messageIds.associateWith { id ->
            when (val state = settled[id]) {
                is MediaSendProgress.Failed -> PhotoOutcome.Failed(state.retryable)
                else -> PhotoOutcome.Sent
            }
        }
    }

    override suspend fun retryPhoto(chatId: ChatId, messageId: String): Result<Unit> =
        coordinator.retryMedia(chatId, messageId)

    override fun discardPhotos(chipIds: List<String>) = chipIds.forEach { uploads.remove(it) }
}
