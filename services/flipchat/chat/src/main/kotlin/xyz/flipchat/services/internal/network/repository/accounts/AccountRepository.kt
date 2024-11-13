package xyz.flipchat.services.internal.network.repository.accounts

import com.getcode.model.ID

interface AccountRepository {
    suspend fun register(displayName: String): Result<ID>
    suspend fun login(): Result<ID>
}
