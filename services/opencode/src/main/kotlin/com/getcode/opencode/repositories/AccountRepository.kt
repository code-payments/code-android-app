package com.getcode.opencode.repositories

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountResponse
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.CoroutineScope

interface AccountRepository {
    suspend fun isValidAccount(owner: KeyPair): Result<Boolean>
    suspend fun createUserAccount(
        scope: CoroutineScope,
        ownerForMint: AccountCluster,
        mint: Mint
    ): Result<ID>

    suspend fun getAccounts(
        accountOwner: KeyPair,
        requestingOwner: KeyPair,
        filter: AccountFilter?
    ): Result<AccountResponse>

    suspend fun getAccount(
        accountOwner: KeyPair,
        requestingOwner: KeyPair,
        filter: AccountFilter
    ): Result<AccountInfo>
}