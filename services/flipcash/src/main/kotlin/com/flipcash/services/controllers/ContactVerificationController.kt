package com.flipcash.services.controllers

import com.flipcash.services.models.ContactMethod
import com.flipcash.services.repository.ContactVerificationRepository
import com.flipcash.services.user.UserManager
import javax.inject.Inject

class ContactVerificationController @Inject constructor(
    private val repository: ContactVerificationRepository,
    private val userManager: UserManager,
) {
    suspend fun sendVerificationCode(method: ContactMethod): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.sendVerificationCode(method, owner)
    }

    suspend fun checkVerificationCode(method: ContactMethod, code: String): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.checkVerificationCode(method, code, owner)
    }

    suspend fun unlink(method: ContactMethod): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.unlink(method, owner).onSuccess {
            val profile = userManager.profile ?: return@onSuccess
            val updated = when (method) {
                is ContactMethod.Phone -> profile.copy(verifiedPhoneNumber = null)
                is ContactMethod.Email -> profile.copy(verifiedEmailAddress = null)
            }
            userManager.set(updated)
        }
    }

    suspend fun linkForPayment(method: ContactMethod.Phone): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.linkForPayment(method, owner)
    }
}