package com.flipcash.app.userprofile.internal.bio

/**
 * The bio being edited. [original] is the stored bio an edit is measured against; [error] is the
 * server's last refusal of [text] and goes away with the next edit.
 */
internal data class BioDraft(
    val original: String,
    val text: String,
    val error: BioError? = null,
) {
    /** Counted in code points, which tracks the server's rune count better than `String.length`. */
    val remaining: Int get() = MAX - text.codePointCount(0, text.length)

    /** A blank draft over a non-blank original is saveable: it clears the bio. */
    val canSave: Boolean get() = remaining >= 0 && text.trim() != original.trim()

    fun edited(newText: String): BioDraft = copy(text = newText, error = null)

    fun failed(error: BioError): BioDraft = copy(error = error)

    companion object {
        const val MAX = 160
    }
}

internal enum class BioError { Moderated, Invalid }
