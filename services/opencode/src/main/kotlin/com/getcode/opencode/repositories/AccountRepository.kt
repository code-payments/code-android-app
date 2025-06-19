package com.getcode.opencode.repositories

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountResponse
import com.getcode.solana.keys.PublicKey

interface AccountRepository {
    suspend fun isCodeAccount(owner: KeyPair): Result<Boolean>
    suspend fun getAccounts(accountOwner: KeyPair, requestingOwner: KeyPair): Result<AccountResponse>
}