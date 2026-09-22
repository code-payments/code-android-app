package com.flipcash.app.core.chat

import android.os.Parcelable
import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.core.ID
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * What a report is about, already resolved to the ids the request will be keyed by.
 *
 * Mirrors [com.flipcash.services.models.ReportTarget] rather than reusing it: that one belongs to
 * the service layer and is not a navigation type, and this has to survive process death on the
 * back stack.
 *
 * It carries ids rather than naming a surface ("the chat this was opened on") because reporting is
 * its own flow now — a top-level route — so there is no shared chat view model behind it to ask.
 * Each surface resolves the ids it already holds when it opens the flow.
 *
 * The user case names the person, not the DM: a tip DM's id is derived on the client, so naming
 * the chat would name something the server has never been told about, and a report made from a
 * group member's profile is about the person either way.
 */
@Serializable
sealed interface ReportSubject : Parcelable {
    @Parcelize
    @Serializable
    data class User(val userId: ID) : ReportSubject

    @Parcelize
    @Serializable
    data class Chat(val chatId: ChatId) : ReportSubject

    @Parcelize
    @Serializable
    data class Message(val chatId: ChatId, val messageId: Long) : ReportSubject
}
