package xyz.flipchat.controllers

import com.getcode.model.ID
import xyz.flipchat.services.internal.network.repository.accounts.AccountRepository
import javax.inject.Inject

class AuthController @Inject constructor(
    private val repository: AccountRepository,
) {
    suspend fun createAccount(): Result<ID> {
        return repository.createAccount()
    }

    @Deprecated("Being replaced with a delayed account creation flow")
    suspend fun register(displayName: String): Result<ID> {
        return repository.register(displayName)
    }

    suspend fun login(): Result<ID> {
        return repository.login()
    }
}