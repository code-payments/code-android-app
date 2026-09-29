package com.flipcash.services.chat

import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.getcode.chatcipher.ChatEncryptionPolicy
import javax.inject.Inject

/**
 * Whether new content in a chat should be end-to-end encrypted. The client decides; the server's
 * `use_e2ee` flag is only an input to that decision while E2EE is rolling out.
 *
 * This is the only reader of [ChatMetadata.useE2ee] in the app. It maps a chat onto
 * [ChatEncryptionPolicy], which both apps share, so the two platforms can't disagree on when a DM
 * is encrypted. The @flipcash exemption lives there.
 */
class E2eePolicy @Inject constructor() {

    fun shouldEncrypt(chat: ChatMetadata): Boolean {
        // CONTACT_DM and TIP_DM, matching the server's IsDmChatType.
        val isDm = chat.type == ChatType.CONTACT_DM || chat.type == ChatType.TIP_DM
        // Every member is checked rather than picking out the peer, so the answer doesn't depend
        // on knowing which entry is the viewer. The viewer is never @flipcash.
        val members = chat.members.map { it.userId.toByteArray() }.ifEmpty { listOf(ByteArray(0)) }
        return members.all { userId ->
            ChatEncryptionPolicy.shouldEncrypt(
                isDirectMessage = isDm,
                useE2ee = chat.useE2ee,
                peerUserId = userId,
            )
        }
    }
}
