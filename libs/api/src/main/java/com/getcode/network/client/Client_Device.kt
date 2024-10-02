package com.getcode.network.client

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.solana.keys.PublicKey
import timber.log.Timber

suspend fun Client.registerInstallation(
    owner: KeyPair,
    installationId: String,
): Result<Unit> {
    return deviceService.registerInstallation(owner, installationId)
        .onSuccess {
            Timber.d("Registered installation: $installationId")
        }
}

suspend fun Client.fetchInstallationAccounts(
    installationId: String
): Result<List<PublicKey>> {
    return deviceService.fetchInstallations(installationId)
        .onSuccess {
            Timber.d("fetched ${it.count()} accounts")
        }
}