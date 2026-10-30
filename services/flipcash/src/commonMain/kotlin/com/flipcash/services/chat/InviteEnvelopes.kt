package com.flipcash.services.chat

import com.flipcash.services.models.chat.InviteKeyEnvelope
import com.flipcash.services.models.chat.InviteSecretEnvelope
import com.getcode.chatcipher.EncryptedPayload
import com.getcode.chatcipher.InviteKeyEnvelope as SealedInviteKey

/*
 * Moves invite envelopes between the transport models and the chat-cipher types. The cipher
 * implements one scheme per message and leaves checking it to the caller, so an envelope in any
 * other scheme has no cipher form and comes back null.
 */

fun InviteKeyEnvelope.toSealed(): SealedInviteKey? =
    if (scheme == InviteKeyEnvelope.SCHEME_XCHACHA20POLY1305) SealedInviteKey(nonce, ciphertext, signature) else null

fun SealedInviteKey.toEnvelope(): InviteKeyEnvelope =
    InviteKeyEnvelope(InviteKeyEnvelope.SCHEME_XCHACHA20POLY1305, nonce, ciphertext, signature)

fun InviteSecretEnvelope.toPayload(): EncryptedPayload? =
    if (scheme == InviteSecretEnvelope.SCHEME_X25519_XCHACHA20POLY1305) EncryptedPayload(nonce, ciphertext) else null

fun EncryptedPayload.toInviteSecretEnvelope(): InviteSecretEnvelope =
    InviteSecretEnvelope(InviteSecretEnvelope.SCHEME_X25519_XCHACHA20POLY1305, nonce, ciphertext)
