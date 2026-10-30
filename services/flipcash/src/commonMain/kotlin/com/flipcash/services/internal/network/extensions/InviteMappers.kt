package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.chat.v1.ChatService as RpcChatService
import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.flipcash.services.models.chat.Invite
import com.flipcash.services.models.chat.InviteId
import com.flipcash.services.models.chat.InviteKeyBundle
import com.flipcash.services.models.chat.InviteKeyEnvelope
import com.flipcash.services.models.chat.InviteParameters
import com.flipcash.services.models.chat.InviteSecretEnvelope
import com.flipcash.services.models.chat.InviteToken
import com.getcode.utils.toByteString
import kotlin.time.Instant

internal fun InviteId.asProtoInviteId(): ChatModel.InviteId =
    ChatModel.InviteId.newBuilder().setValue(bytes.toByteString()).build()

internal fun InviteToken.asProtoInviteToken(): ChatModel.InviteToken =
    ChatModel.InviteToken.newBuilder().setValue(bytes.toByteString()).build()

internal fun InviteKeyEnvelope.asProtoInviteKeyEnvelope(): ChatModel.InviteKeyEnvelope =
    ChatModel.InviteKeyEnvelope.newBuilder()
        .setSchemeValue(scheme)
        .setNonce(nonce.toByteString())
        .setCiphertext(ciphertext.toByteString())
        .setSignature(signature.toByteString())
        .build()

internal fun InviteSecretEnvelope.asProtoInviteSecretEnvelope(): ChatModel.InviteSecretEnvelope =
    ChatModel.InviteSecretEnvelope.newBuilder()
        .setSchemeValue(scheme)
        .setNonce(nonce.toByteString())
        .setCiphertext(ciphertext.toByteString())
        .build()

internal fun InviteParameters.asProtoInviteParameters(): RpcChatService.JoinChatRequest.InviteParameters =
    RpcChatService.JoinChatRequest.InviteParameters.newBuilder()
        .setInviteToken(token.asProtoInviteToken())
        .setSecretEnvelope(secretEnvelope.asProtoInviteSecretEnvelope())
        .build()

internal fun ChatModel.InviteId.toInviteId(): InviteId = InviteId(value.toByteArray())

internal fun ChatModel.InviteKeyEnvelope.toInviteKeyEnvelope(): InviteKeyEnvelope = InviteKeyEnvelope(
    scheme = schemeValue,
    nonce = nonce.toByteArray(),
    ciphertext = ciphertext.toByteArray(),
    signature = signature.toByteArray(),
)

internal fun ChatModel.InviteSecretEnvelope.toInviteSecretEnvelope(): InviteSecretEnvelope = InviteSecretEnvelope(
    scheme = schemeValue,
    nonce = nonce.toByteArray(),
    ciphertext = ciphertext.toByteArray(),
)

internal fun ChatModel.Invite.Status.toInviteStatus(): Invite.Status = when (this) {
    ChatModel.Invite.Status.ACTIVE -> Invite.Status.ACTIVE
    ChatModel.Invite.Status.EXHAUSTED -> Invite.Status.EXHAUSTED
    ChatModel.Invite.Status.EXPIRED -> Invite.Status.EXPIRED
    ChatModel.Invite.Status.REVOKED -> Invite.Status.REVOKED
    else -> Invite.Status.UNKNOWN
}

internal fun ChatModel.Invite.toInvite(): Invite = Invite(
    id = inviteId.toInviteId(),
    chatId = chatId.toChatId(),
    createdBy = createdBy.toId(),
    maxUses = Integer.toUnsignedLong(maxUses),
    useCount = Integer.toUnsignedLong(useCount),
    createdAt = Instant.fromEpochSeconds(createdAt.seconds, createdAt.nanos),
    expiresAt = if (hasExpiresAt()) Instant.fromEpochSeconds(expiresAt.seconds, expiresAt.nanos) else null,
    status = status.toInviteStatus(),
    secretEnvelope = if (hasSecretEnvelope()) secretEnvelope.toInviteSecretEnvelope() else null,
)

internal fun RpcChatService.GetKeyEnvelopeResponse.toInviteKeyBundle(): InviteKeyBundle = InviteKeyBundle(
    inviteId = inviteKeyBundle.inviteId.toInviteId(),
    keyEnvelope = inviteKeyBundle.keyEnvelope.toInviteKeyEnvelope(),
    secretEnvelope = inviteKeyBundle.secretEnvelope.toInviteSecretEnvelope(),
    wrappedBy = if (hasWrappedBy()) wrappedBy.toId() else null,
    wrappedByPublicKey = if (hasWrappedByPublicKey()) wrappedByPublicKey.toPublicKey() else null,
)
