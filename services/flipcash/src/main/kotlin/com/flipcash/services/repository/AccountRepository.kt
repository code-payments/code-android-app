package com.flipcash.services.repository

import com.flipcash.services.models.UserFlags
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID

interface AccountRepository {
    suspend fun register(owner: KeyPair): Result<ID>
    suspend fun login(owner: KeyPair): Result<ID>
    suspend fun getUserFlags(owner: KeyPair, userId: ID, countryCode: String): Result<UserFlags>
}