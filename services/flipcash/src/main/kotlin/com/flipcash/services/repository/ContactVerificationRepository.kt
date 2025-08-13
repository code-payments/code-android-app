package com.flipcash.services.repository

import com.flipcash.services.models.ContactMethod
import com.getcode.ed25519.Ed25519

interface ContactVerificationRepository {
    suspend fun sendVerificationCode(method: ContactMethod, owner: Ed25519.KeyPair): Result<Unit>
    suspend fun checkVerificationCode(method: ContactMethod, code: String, owner: Ed25519.KeyPair): Result<Unit>
    suspend fun unlink(method: ContactMethod, owner: Ed25519.KeyPair): Result<Unit>

}