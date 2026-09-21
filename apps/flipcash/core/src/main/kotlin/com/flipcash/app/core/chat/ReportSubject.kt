package com.flipcash.app.core.chat

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * What a report opened from the chat flow is about.
 *
 * Not [com.flipcash.services.models.ReportTarget], which is what finally goes on the wire: that one
 * names a chat by id, and the id is something the flow's view model holds rather than something a
 * navigation step should carry a second copy of. This says which *surface* asked, and
 * `ChatViewModel` turns it into a target with the ids it already has.
 *
 * The user case carries its participant for the same reason [ChatStep.Profile] does — a member's
 * profile is opened from a bubble, so the person is not the one the flow was opened on.
 */
@Serializable
sealed interface ReportSubject : Parcelable {
    @Parcelize
    @Serializable
    data class User(val participant: ChatParticipant) : ReportSubject

    /** The conversation the flow is already open on. */
    @Parcelize
    @Serializable
    data object Chat : ReportSubject

    @Parcelize
    @Serializable
    data class Message(val messageId: Long) : ReportSubject
}
