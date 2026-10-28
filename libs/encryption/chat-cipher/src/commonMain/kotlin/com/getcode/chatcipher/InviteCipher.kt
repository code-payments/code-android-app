package com.getcode.chatcipher

import com.getcode.ed25519kmp.KeyPair

/**
 * An invite's copy of a private group's chat key, as carried by `chat.v1.InviteKeyEnvelope`
 * scheme `XCHACHA20POLY1305`: the 24-byte nonce, the 48-byte ciphertext with its tag, and the
 * inviter's 64-byte Ed25519 signature over both.
 */
class InviteKeyEnvelope(val nonce: ByteArray, val ciphertext: ByteArray, val signature: ByteArray)

/** The step at which [InviteCipher.openInviteKeyBundle] rejected a bundle, in the order they run. */
enum class InviteBundleFailure {
    /** The bundle's `wrapped_by` is not the chat's creator. */
    WrappedBy,

    /** The copy's signature does not verify against the creator's public key. */
    Signature,

    /** The member's `InviteSecretEnvelope` does not open, or opens to a secret of another invite. */
    SecretOpen,

    /** The copy does not open under the wrap key derived from the secret. */
    KeyOpen,
}

/** What [InviteCipher.openInviteKeyBundle] makes of a bundle. Either way, the chat key is never in [toString]. */
sealed interface InviteBundleResult {
    class Opened(val chatKey: ByteArray) : InviteBundleResult {
        override fun toString(): String = "Opened"
    }

    /** The member treats this as the wrong key: it warns and does not send in the chat. */
    class Rejected(val failsAt: InviteBundleFailure) : InviteBundleResult {
        override fun toString(): String = "Rejected($failsAt)"
    }
}

/**
 * Invite links for private groups: the derivations in `chat.v1.Invite`, the invite's copy of the
 * chat key (`InviteKeyEnvelope`) and the link's secret wrapped for oneself (`InviteSecretEnvelope`),
 * from flipcash2-protobuf-api. `test-vectors/invite.json` in the orchestrator repo pins it.
 * [DefaultInviteCipher] is the implementation; the interface is the seam for fakes in either app.
 *
 * The link's 32-byte secret, the invite token and the wrap key let anyone join or read the chat:
 * callers never log them, and no type here prints them. `chatId` is the raw `ChatId.value`,
 * `inviteId` the raw 16-byte `InviteId.value`. Envelope schemes are the caller's to check: these
 * functions implement the one scheme each message defines.
 *
 * Link encoding and routing are in [InviteLink].
 */
interface InviteCipher {
    /** 32 fresh random bytes: the secret of a new invite's link. */
    fun newSecret(): ByteArray

    /** The 32-byte `InviteToken.value` for [secret]. It does not depend on the chat. */
    @Throws(ChatCipherException::class)
    fun inviteToken(secret: ByteArray): ByteArray

    /** The 16-byte `InviteId.value` for [secret], as the server computes it from the token. */
    @Throws(ChatCipherException::class)
    fun inviteId(secret: ByteArray): ByteArray

    /**
     * Seals [chatKey] as [secret]'s invite copy for [chatId], under a fresh random nonce, and signs
     * it with [inviter], the account creating the invite.
     *
     * @throws ChatCipherException if [secret] or [chatKey] is not 32 bytes, or [inviter] is malformed.
     */
    @Throws(ChatCipherException::class)
    fun sealInviteKeyEnvelope(inviter: KeyPair, secret: ByteArray, chatId: ByteArray, chatKey: ByteArray): InviteKeyEnvelope

    /**
     * Verifies [envelope]'s signature against [inviterPk], then opens it with [secret]'s wrap key
     * for [chatId], and returns the chat key. A member opening the copy they joined with uses
     * [openInviteKeyBundle] instead, which also runs the checks before this one.
     *
     * @throws ChatCipherException if the signature does not verify or the copy does not open.
     */
    @Throws(ChatCipherException::class)
    fun openInviteKeyEnvelope(secret: ByteArray, chatId: ByteArray, inviterPk: ByteArray, envelope: InviteKeyEnvelope): ByteArray

    /**
     * Wraps [secret] for [owner] alone as `InviteSecretEnvelope` scheme `X25519_XCHACHA20POLY1305`,
     * under a fresh random nonce. The inviter makes one with `CreateInvite`, a joiner with `JoinChat`.
     *
     * @throws ChatCipherException if [secret] is not 32 bytes or [owner] is malformed.
     */
    @Throws(ChatCipherException::class)
    fun sealInviteSecretEnvelope(owner: KeyPair, secret: ByteArray, chatId: ByteArray, inviteId: ByteArray): EncryptedPayload

    /** @throws ChatCipherException if [envelope] does not open for [owner], [chatId] and [inviteId]. */
    @Throws(ChatCipherException::class)
    fun openInviteSecretEnvelope(owner: KeyPair, chatId: ByteArray, inviteId: ByteArray, envelope: EncryptedPayload): ByteArray

    /**
     * Opens `GetKeyEnvelopeResponse.invite_key_bundle` for [member], in the order the
     * `InviteKeyEnvelope` comment sets: [wrappedByCreator] (whether `wrapped_by` is
     * `Metadata.creator`), then the copy's signature against [creatorPk], then the member's
     * [secretEnvelope], then the copy itself. [creatorPk] is one learned outside the service when
     * the caller has it, otherwise `wrapped_by_public_key`. [inviteId] is the bundle's `invite_id`.
     *
     * Never throws: every failure is a [InviteBundleResult.Rejected] naming its step.
     */
    fun openInviteKeyBundle(
        member: KeyPair,
        creatorPk: ByteArray,
        wrappedByCreator: Boolean,
        chatId: ByteArray,
        inviteId: ByteArray,
        keyEnvelope: InviteKeyEnvelope,
        secretEnvelope: EncryptedPayload,
    ): InviteBundleResult
}
