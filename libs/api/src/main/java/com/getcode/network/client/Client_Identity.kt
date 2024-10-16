package com.getcode.network.client

import com.getcode.ed25519.Ed25519
import com.getcode.model.TwitterUser
import com.getcode.solana.organizer.Organizer
import java.util.Locale

suspend fun Client.loginToThirdParty(rendezvous: com.getcode.solana.keys.PublicKey, relationship: Ed25519.KeyPair): Result<Unit> {
    return identityRepository.loginToThirdParty(rendezvous, relationship)
}

suspend fun Client.updatePreferences(organizer: Organizer): Result<Boolean> {
    return identityRepository.updatePreferences(
        locale = Locale.getDefault(),
        owner = organizer.ownerKeyPair
    )
}

suspend fun Client.fetchTwitterUser(
    organizer: Organizer,
    username: String
): Result<TwitterUser> {
    return identityRepository.fetchTwitterUserByUsername(organizer.ownerKeyPair, username)
}

suspend fun Client.fetchTwitterUser(
    organizer: Organizer,
    address: com.getcode.solana.keys.PublicKey
): Result<TwitterUser> {
    return identityRepository.fetchTwitterUserByAddress(organizer.ownerKeyPair, address)
}