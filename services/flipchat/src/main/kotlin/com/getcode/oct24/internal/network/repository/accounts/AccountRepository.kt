package com.getcode.oct24.internal.network.repository.accounts

import com.getcode.model.ID
import com.getcode.oct24.internal.network.service.AccountService
import com.getcode.oct24.user.UserManager
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import javax.inject.Inject
import javax.inject.Singleton

interface AccountRepository {
    suspend fun register(displayName: String): Result<ID>
    suspend fun login(): Result<ID>
}
