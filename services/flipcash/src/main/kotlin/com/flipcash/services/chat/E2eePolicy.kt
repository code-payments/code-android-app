package com.flipcash.services.chat

import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.getcode.opencode.model.core.ID

/**
 * The @flipcash account's user id. Chats with it stay plaintext, because the backend sends its
 * onboarding messages in the clear and reads the user's replies.
 *
 * Null until the id is known, which makes the exemption a no-op: every DM with the flag on is
 * treated as encrypted.
 */
// TODO: set @flipcash user id
val FLIPCASH_ACCOUNT_ID: ID? = null

/**
 * Whether new content in a chat should be end-to-end encrypted. The client decides; the server's
 * `use_e2ee` flag is only an input to that decision while E2EE is rolling out.
 *
 * This is the only reader of [ChatMetadata.useE2ee]. Screens follow the policy or the transcript,
 * so the flag can be dropped later by changing [shouldEncrypt] alone.
 *
 * @param flipcashAccountId the @flipcash account to exempt; injectable for tests.
 */
class E2eePolicy(private val flipcashAccountId: ID? = FLIPCASH_ACCOUNT_ID) {

    fun shouldEncrypt(chat: ChatMetadata): Boolean {
        val isDm = chat.type == ChatType.CONTACT_DM || chat.type == ChatType.TIP_DM
        if (!isDm || !chat.useE2ee) return false
        val official = flipcashAccountId ?: return true
        return chat.members.none { it.userId == official }
    }
}
