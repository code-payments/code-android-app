package com.getcode.opencode.internal.domain.repositories

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.services.AccountService
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.repositories.AccountRepository
import com.getcode.solana.keys.PublicKey
import javax.inject.Inject

internal class InternalAccountRepository @Inject constructor(
    private val service: AccountService,
) : AccountRepository {
    override suspend fun isCodeAccount(
        owner: Ed25519.KeyPair
    ): Result<Boolean> = service.isCodeAccount(owner)

    override suspend fun getAccounts(
        owner: Ed25519.KeyPair
    ): Result<Map<PublicKey, AccountInfo>> = service.getAccounts(owner)

    override suspend fun linkAdditionalAccounts(
        owner: Ed25519.KeyPair,
        accountToLink: Ed25519.KeyPair
    ): Result<Unit> = service.linkAdditionalAccounts(owner, accountToLink)
}