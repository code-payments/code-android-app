package com.flipcash.services.models

import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.core.ID

/**
 * What a report names as its subject. Mirrors the reporting `ReportRequest` oneof: a report is
 * keyed by exactly one of a user, a chat, a message within a chat, or a blob.
 */
sealed interface ReportTarget {
    data class User(val userId: ID) : ReportTarget
    data class Chat(val chatId: ChatId) : ReportTarget
    data class Message(val chatId: ChatId, val messageId: Long) : ReportTarget
    data class Blob(val blobId: BlobId) : ReportTarget
}
