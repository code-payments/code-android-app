package com.flipcash.app.messenger.internal.screens.profile.edit

import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.EditChatParameters

/**
 * The two edits this screen can make, each named so the "send only what changed" rule is a thing
 * the code states rather than a thing every call site has to remember.
 *
 * `EditChatRequest` leaves a field unchanged when it is unset, so a partial update is the whole
 * mechanism: [titleOnly] must not carry a picture and [pictureOnly] must not carry a title.
 * Building the parameters inline at each call site made that a convention holding by inspection —
 * and an `EditChatParameters(title = ..., picture = state.picture)` written later, in the shape
 * most object updates take, would silently overwrite the other field with whatever the screen
 * happened to be holding.
 */
internal fun titleOnly(title: CharSequence): EditChatParameters =
    EditChatParameters(title = ChatTitle.normalize(title))

internal fun pictureOnly(blobId: BlobId): EditChatParameters =
    EditChatParameters(picture = blobId)
