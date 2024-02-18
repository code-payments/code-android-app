package com.getcode.network.client

import com.getcode.ed25519.Ed25519
import com.getcode.solana.keys.PublicKey

suspend fun Client.loginToThirdParty(rendezvous: PublicKey, relationship: Ed25519.KeyPair): Result<Unit> {
    return identityRepository.loginToThirdParty(rendezvous, relationship)
}
