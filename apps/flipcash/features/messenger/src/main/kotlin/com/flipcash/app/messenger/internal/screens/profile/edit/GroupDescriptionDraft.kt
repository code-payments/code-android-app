package com.flipcash.app.messenger.internal.screens.profile.edit

import com.flipcash.services.models.chat.DescriptionEdit

/**
 * The description being edited. [original] is the stored one an edit is measured against; [moderated]
 * is the server's refusal of exactly this [text].
 *
 * `EditChatRequest.Description.value` caps at [MAX], counted in code points as the server counts
 * runes. An empty description is valid: it is the way to clear one.
 */
data class GroupDescriptionDraft(
    val original: String,
    val text: String,
    val moderated: Boolean = false,
) {
    val remaining: Int get() = MAX - text.codePointCount(0, text.length)

    /** Within the cap and different from what the group already carries, whitespace aside. */
    val canSave: Boolean get() = remaining >= 0 && text.trim() != original.trim()

    /** What to send: a description with nothing left of it is a [DescriptionEdit.Clear]. */
    val edit: DescriptionEdit
        get() = text.trim().let { if (it.isEmpty()) DescriptionEdit.Clear else DescriptionEdit.Set(it) }

    companion object {
        const val MAX = 160
    }
}
