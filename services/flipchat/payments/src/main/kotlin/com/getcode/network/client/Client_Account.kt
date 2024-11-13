package com.getcode.network.client

import com.getcode.ed25519.Ed25519.KeyPair

suspend fun Client.isCodeAccount(owner: KeyPair): Result<Boolean> {
    return accountService.isCodeAccount(owner)
}

suspend fun Client.linkAdditionalAccount(owner: KeyPair, linkedAccount: KeyPair): Result<Unit> {
    return accountService.linkAdditionalAccounts(owner, linkedAccount)
}
