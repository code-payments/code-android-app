package com.flipcash.app.tipping.internal

import android.net.Uri
import com.flipcash.services.models.chat.IdempotencyKey
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.flipcash.services.models.chat.BlobId

/**
 * Everything `StartChat` needs, as the form has it at the moment Create is tapped.
 *
 * Doubles as the identity of a create attempt: two taps carrying the same draft are the same
 * attempt, and a tap after an edit is a new one. See [GroupCreateAttempt].
 */
internal data class GroupDraft(
    val title: String,
    /** The re-encoded local copy, before upload. Null when the group has no picture. */
    val picture: Uri?,
    /** The mint the balance requirement is denominated in. */
    val mint: Mint,
    /** The required balance, in USD — the currency [com.flipcash.shared.chat.GroupAccess] compares in. */
    val amount: Fiat,
)

/**
 * The idempotency key for one create attempt, and the work that key has already paid for.
 *
 * `StartChat` derives a chat's identity from the caller and this key alone — the parameters are not
 * part of it — so the key decides what a second call means. Reused, the server hands back the chat
 * the first call created and reports `OK`; freshly minted, it creates another one. Retrying a failed
 * attempt wants the first; creating a second group wants the second. Neither is something the view
 * model can decide at the call site, because by then it cannot tell the two apart.
 *
 * So the key is minted against the draft it was minted for. [keyFor] returns the held key while the
 * draft is unchanged, which covers every retry of one attempt — including the transport failures
 * where the server may well have created the chat already and the response is what was lost. An
 * edited draft is a different intent and gets a different key, because reusing it would silently
 * return the chat built from the *old* parameters and leave the edit with nowhere to go.
 *
 * [picture] is cached against the same boundary: a blob that is already uploaded and `READY` stays
 * good for as long as the draft pointing at it does, so a retry does not re-upload it.
 */
internal class GroupCreateAttempt(
    private val mintKey: () -> IdempotencyKey = { IdempotencyKey.random() },
) {
    private var draft: GroupDraft? = null
    private var key: IdempotencyKey? = null

    /** The blob id for [draft]'s picture, once uploaded. */
    var picture: BlobId? = null
        private set

    /**
     * The key to send for [draft] — the held one if this is the same attempt, a fresh one if not.
     */
    fun keyFor(draft: GroupDraft): IdempotencyKey {
        val held = key
        if (held != null && this.draft == draft) return held

        return mintKey().also {
            this.draft = draft
            this.key = it
            // A new attempt cannot inherit the old one's upload: the picture is part of the draft,
            // so a draft change may be the picture itself changing.
            this.picture = null
        }
    }

    fun rememberPicture(blobId: BlobId) {
        picture = blobId
    }

    /** Forget the attempt, so the next Create starts a new one. Called once a chat exists. */
    fun clear() {
        draft = null
        key = null
        picture = null
    }
}
