package com.getcode.opencode.repositories

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.solana.keys.PublicKey

interface AccountRepository {
    suspend fun isCodeAccount(owner: KeyPair): Result<Boolean>
    suspend fun getAccounts(owner: KeyPair): Result<Map<PublicKey, AccountInfo>>
    suspend fun linkAdditionalAccounts(
        owner: KeyPair,
        accountToLink: KeyPair,
    ): Result<Unit>
}