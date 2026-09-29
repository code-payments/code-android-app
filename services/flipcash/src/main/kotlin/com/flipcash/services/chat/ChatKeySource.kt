package com.flipcash.services.chat

import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.user.UserManager
import com.getcode.ed25519kmp.KeyPair
import com.getcode.opencode.model.core.ID
import javax.inject.Inject

/** The Ed25519 keys a DM is encrypted between. */
interface ChatKeySource {
    /** The viewer's account key pair, or null when no account is loaded. */
    fun ownKeyPair(): KeyPair?

    /** The Ed25519 public key [userId] registered their account with. */
    suspend fun peerPublicKey(userId: ID): Result<ByteArray>
}

/**
 * Reads the peer's key from the Resolver: resolving a user id returns the owner key the account
 * registered with, the same key a tip to that user is sent to.
 */
internal class DefaultChatKeySource @Inject constructor(
    private val userManager: UserManager,
    private val resolver: ResolverController,
) : ChatKeySource {

    override fun ownKeyPair(): KeyPair? =
        userManager.accountCluster?.authority?.keyPair?.let { keyPair ->
            // The orlp private key is the SHA-512 expansion of the seed, which is what the
            // cipher's X25519 conversion reads.
            KeyPair(publicKey = keyPair.publicKeyBytes, privateKey = keyPair.privateKeyBytes)
        }

    override suspend fun peerPublicKey(userId: ID): Result<ByteArray> =
        resolver.resolve(userId).map { it.byteArray }
}
