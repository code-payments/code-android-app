package com.getcode.oct24.network.controllers

import com.getcode.model.ID
import com.getcode.oct24.internal.network.repository.accounts.AccountRepository
import javax.inject.Inject

class AuthController @Inject constructor(
    private val repository: AccountRepository,
) {
    suspend fun register(displayName: String): Result<ID> {
        return repository.register(displayName)
    }

    suspend fun login(): Result<ID> {
        return repository.login()
    }
}