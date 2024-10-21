package com.getcode.oct24.network.controllers

import com.getcode.model.ID
import com.getcode.oct24.internal.network.service.AccountService
import com.getcode.oct24.user.UserManager
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountController @Inject constructor(
    private val userManager: UserManager,
    private val service: AccountService
) {
    @Throws(AccountService.RegisterError::class, IllegalStateException::class)
    suspend fun register(displayName: String): Result<ID> {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")
        return service.register(
            owner = owner,
            displayName = displayName
        ).onFailure {
            ErrorUtils.handleError(it)
        }.onSuccess {
            trace(
                tag = "Accounts",
                type = TraceType.Silent,
                message = "Registered successfully"
            )
        }
    }

    suspend fun login(): Result<ID> {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")
        return service.login(owner)
            .onFailure {
                ErrorUtils.handleError(it)
            }
            .onSuccess {
                trace(
                    tag = "Accounts",
                    type = TraceType.Silent,
                    message = "Logged in successfully"
                )
            }
    }
}
