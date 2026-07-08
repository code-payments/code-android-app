package com.flipcash.services.controllers

import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.VerifiableContactMethod
import com.flipcash.services.repository.ContactVerificationRepository
import com.flipcash.services.user.UserManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
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
                is ContactMethod.Phone -> profile.copy(phoneNumber = null)
                is ContactMethod.Email -> profile.copy(email = null)
            }
            userManager.set(updated)
        }
    }

    /**
     * Records a contact on the local profile as *unverified*, without any server
     * round-trip. Used when server verification is skipped but we still need the
     * value available (e.g. a Coinbase on-ramp email the user entered). The change
     * is persisted locally by [com.flipcash.shared.profile.ProfileCoordinator].
     */
    fun setLocalUnverified(method: ContactMethod) {
        val profile = userManager.profile ?: return
        val updated = when (method) {
            is ContactMethod.Phone ->
                profile.copy(phoneNumber = VerifiableContactMethod(method.phoneNumber, verified = false))
            is ContactMethod.Email ->
                profile.copy(email = VerifiableContactMethod(method.emailAddress, verified = false))
        }
        userManager.set(updated)
    }

    fun removeLocalUnverified(method: ContactMethod) {
        val profile = userManager.profile ?: return
        val updated = when (method) {
            is ContactMethod.Phone ->
                profile.copy(phoneNumber = null)
            is ContactMethod.Email ->
                profile.copy(email = null)
        }
        userManager.set(updated)
    }

    suspend fun linkForPayment(method: ContactMethod.Phone): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.linkForPayment(method, owner)
    }
}