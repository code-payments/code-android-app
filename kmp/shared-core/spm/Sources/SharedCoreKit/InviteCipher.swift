import Foundation
import SharedCore

/// Invite links for private groups, computed by the shared Kotlin (`DefaultInviteCipher` and
/// `InviteLink` in chat-cipher). `test-vectors/invite.json` pins it on both platforms.
///
/// The link's secret, its token and the link itself are as good as the chat key: never log them.
/// `chatId` is the raw `ChatId.value` and `inviteId` the raw 16-byte `InviteId.value`. Key pairs are
/// the ones `SharedEd25519.keyPair(seed:)` returns.
public enum SharedInviteCipher {

    /// `chat.v1.InviteKeyEnvelope` scheme `XCHACHA20POLY1305`, without its scheme.
    public struct KeyEnvelope: Equatable {
        public let nonce: Data
        public let ciphertext: Data
        public let signature: Data

        public init(nonce: Data, ciphertext: Data, signature: Data) {
            self.nonce = nonce
            self.ciphertext = ciphertext
            self.signature = signature
        }
    }

    /// `chat.v1.InviteSecretEnvelope` scheme `X25519_XCHACHA20POLY1305`, without its scheme.
    public struct SecretEnvelope: Equatable {
        public let nonce: Data
        public let ciphertext: Data

        public init(nonce: Data, ciphertext: Data) {
            self.nonce = nonce
            self.ciphertext = ciphertext
        }
    }

    /// The step at which `openKeyBundle` rejected a bundle. Every case is the wrong-key state.
    public enum BundleError: Error, Equatable {
        case wrappedBy
        case signature
        case secretOpen
        case keyOpen
    }

    /// What a URL is to the router. `invite` and `invalidInvite` are matched ahead of `/chat/{uuid}`.
    public enum Route: Equatable {
        case invite(secret: Data)
        case invalidInvite
        case chat
        case other
    }

    /// 32 fresh random bytes: the secret of a new invite's link.
    public static func newSecret() -> Data {
        Data(DefaultInviteCipher.shared.doNewSecret())
    }

    public static func inviteToken(secret: Data) throws -> Data {
        Data(try DefaultInviteCipher.shared.inviteToken(secret: secret.kotlinByteArray))
    }

    public static func inviteId(secret: Data) throws -> Data {
        Data(try DefaultInviteCipher.shared.inviteId(secret: secret.kotlinByteArray))
    }

    /// Seals `chatKey` as the invite's copy under a fresh random nonce, signed by `inviter`.
    public static func sealKeyEnvelope(
        inviter: SharedEd25519.KeyPair,
        secret: Data,
        chatId: Data,
        chatKey: Data
    ) throws -> KeyEnvelope {
        let envelope = try DefaultInviteCipher.shared.sealInviteKeyEnvelope(
            inviter: inviter.kotlin,
            secret: secret.kotlinByteArray,
            chatId: chatId.kotlinByteArray,
            chatKey: chatKey.kotlinByteArray
        )
        return KeyEnvelope(
            nonce: Data(envelope.nonce),
            ciphertext: Data(envelope.ciphertext),
            signature: Data(envelope.signature)
        )
    }

    /// Verifies the copy against `inviterPublicKey`, then opens it. A member opening the copy they
    /// joined with uses `openKeyBundle`.
    public static func openKeyEnvelope(
        secret: Data,
        chatId: Data,
        inviterPublicKey: Data,
        envelope: KeyEnvelope
    ) throws -> Data {
        Data(try DefaultInviteCipher.shared.openInviteKeyEnvelope(
            secret: secret.kotlinByteArray,
            chatId: chatId.kotlinByteArray,
            inviterPk: inviterPublicKey.kotlinByteArray,
            envelope: envelope.kotlin
        ))
    }

    /// Wraps `secret` for `owner` alone, under a fresh random nonce.
    public static func sealSecretEnvelope(
        owner: SharedEd25519.KeyPair,
        secret: Data,
        chatId: Data,
        inviteId: Data
    ) throws -> SecretEnvelope {
        let envelope = try DefaultInviteCipher.shared.sealInviteSecretEnvelope(
            owner: owner.kotlin,
            secret: secret.kotlinByteArray,
            chatId: chatId.kotlinByteArray,
            inviteId: inviteId.kotlinByteArray
        )
        return SecretEnvelope(nonce: Data(envelope.nonce), ciphertext: Data(envelope.ciphertext))
    }

    public static func openSecretEnvelope(
        owner: SharedEd25519.KeyPair,
        chatId: Data,
        inviteId: Data,
        envelope: SecretEnvelope
    ) throws -> Data {
        Data(try DefaultInviteCipher.shared.openInviteSecretEnvelope(
            owner: owner.kotlin,
            chatId: chatId.kotlinByteArray,
            inviteId: inviteId.kotlinByteArray,
            envelope: envelope.kotlin
        ))
    }

    /// Opens `GetKeyEnvelopeResponse.invite_key_bundle` and returns the chat key. `wrappedByCreator`
    /// is whether `wrapped_by` is `Metadata.creator`; `creatorPublicKey` is one learned outside the
    /// service when there is one, otherwise `wrapped_by_public_key`; `inviteId` is the bundle's.
    public static func openKeyBundle(
        member: SharedEd25519.KeyPair,
        creatorPublicKey: Data,
        wrappedByCreator: Bool,
        chatId: Data,
        inviteId: Data,
        keyEnvelope: KeyEnvelope,
        secretEnvelope: SecretEnvelope
    ) throws -> Data {
        let result = DefaultInviteCipher.shared.openInviteKeyBundle(
            member: member.kotlin,
            creatorPk: creatorPublicKey.kotlinByteArray,
            wrappedByCreator: wrappedByCreator,
            chatId: chatId.kotlinByteArray,
            inviteId: inviteId.kotlinByteArray,
            keyEnvelope: keyEnvelope.kotlin,
            secretEnvelope: secretEnvelope.kotlin
        )
        if let opened = result as? InviteBundleResultOpened {
            return Data(opened.chatKey)
        }
        let failsAt = (result as? InviteBundleResultRejected)?.failsAt
        switch failsAt {
        case InviteBundleFailure.wrappedby: throw BundleError.wrappedBy
        case InviteBundleFailure.signature: throw BundleError.signature
        case InviteBundleFailure.secretopen: throw BundleError.secretOpen
        default: throw BundleError.keyOpen
        }
    }

    /// `https://app.flipcash.com/chat/invite#s=<unpadded base64url secret>`.
    public static func link(secret: Data) throws -> String {
        try InviteLink.shared.url(secret: secret.kotlinByteArray)
    }

    /// The secret in a URL fragment (without its `#`), or `nil` when it holds none.
    public static func secret(fragment: String) -> Data? {
        InviteLink.shared.parseFragment(fragment: fragment).map { Data($0) }
    }

    public static func route(url: String) -> Route {
        let route = InviteLink.shared.route(url: url)
        if let invite = route as? InviteLinkRouteInvite {
            return .invite(secret: Data(invite.secret))
        }
        if route is InviteLinkRouteInvalidInvite { return .invalidInvite }
        if route is InviteLinkRouteChat { return .chat }
        return .other
    }
}

private extension SharedEd25519.KeyPair {
    var kotlin: SharedCore.KeyPair {
        SharedCore.KeyPair(publicKey: publicKey.kotlinByteArray, privateKey: privateKey.kotlinByteArray)
    }
}

private extension SharedInviteCipher.KeyEnvelope {
    var kotlin: InviteKeyEnvelope {
        InviteKeyEnvelope(
            nonce: nonce.kotlinByteArray,
            ciphertext: ciphertext.kotlinByteArray,
            signature: signature.kotlinByteArray
        )
    }
}

private extension SharedInviteCipher.SecretEnvelope {
    var kotlin: EncryptedPayload {
        EncryptedPayload(nonce: nonce.kotlinByteArray, ciphertext: ciphertext.kotlinByteArray)
    }
}
