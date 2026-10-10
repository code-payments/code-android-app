package com.flipcash.app.messenger.internal.screens.profile.edit

import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.DescriptionEdit
import com.flipcash.services.models.chat.EditChatParameters

/**
 * The edits the edit screens can make, each named so the "send only what changed" rule is a thing
 * the code states rather than a thing every call site has to remember.
 *
 * `EditChatRequest` leaves a field unchanged when it is unset, so a partial update is the whole
 * mechanism: each of these carries its own field and no other.
 * Building the parameters inline at each call site made that a convention holding by inspection —
 * and an `EditChatParameters(title = ..., picture = state.picture)` written later, in the shape
 * most object updates take, would silently overwrite the other field with whatever the screen
 * happened to be holding.
 */
internal fun titleOnly(title: CharSequence): EditChatParameters =
    EditChatParameters(title = ChatTitle.normalize(title))

internal fun pictureOnly(blobId: BlobId): EditChatParameters =
    EditChatParameters(picture = blobId)

internal fun coverOnly(blobId: BlobId): EditChatParameters =
    EditChatParameters(coverPicture = blobId)

/**
 * A description-only edit. A [DescriptionEdit.Set] is sent trimmed, and one with nothing left of
 * it is sent as [DescriptionEdit.Clear]: the wire tells "leave it" (wrapper unset) from "clear it"
 * (wrapper set, empty value), and an empty set would be the same thing spelled the wrong way.
 */
internal fun descriptionOnly(edit: DescriptionEdit): EditChatParameters {
    val normalized = when (edit) {
        DescriptionEdit.Clear -> edit
        is DescriptionEdit.Set -> edit.value.trim().let {
            if (it.isEmpty()) DescriptionEdit.Clear else DescriptionEdit.Set(it)
        }
    }
    return EditChatParameters(description = normalized)
}
