package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.network.services.EmailVerificationService
import com.flipcash.services.internal.network.services.PhoneVerificationService
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.repository.ContactVerificationRepository
import com.getcode.ed25519.Ed25519
import com.getcode.utils.ErrorUtils

internal class InternalContactVerificationRepository(
    private val emailService: EmailVerificationService,
    private val phoneService: PhoneVerificationService,
): ContactVerificationRepository {
    override suspend fun sendVerificationCode(
        method: ContactMethod,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return when (method) {
            is ContactMethod.Email -> emailService.sendVerificationCode(method, owner)
            is ContactMethod.Phone -> phoneService.sendVerificationCode(method, owner)
        }.onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun checkVerificationCode(
        method: ContactMethod,
        code: String,
        owner: Ed25519.KeyPair
    ): Result<Unit> {
        return when (method) {
            is ContactMethod.Email -> emailService.checkVerificationCode(method, code, owner)
            is ContactMethod.Phone -> phoneService.checkVerificationCode(method, code, owner)
        }.onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun unlink(method: ContactMethod, owner: Ed25519.KeyPair): Result<Unit> {
        return when (method) {
            is ContactMethod.Email -> emailService.unlink(method, owner)
            is ContactMethod.Phone -> phoneService.unlink(method, owner)
        }.onFailure { ErrorUtils.handleError(it) }
    }
}