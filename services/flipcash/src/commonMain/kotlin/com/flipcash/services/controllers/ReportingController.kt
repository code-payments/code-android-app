package com.flipcash.services.controllers

import com.flipcash.services.models.ReportTarget
import com.flipcash.services.repository.ReportingRepository
import com.flipcash.services.user.UserManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportingController @Inject constructor(
    private val repository: ReportingRepository,
    private val userManager: UserManager,
) {
    /** Files a report against [target], with an optional free-form [description]. */
    suspend fun report(target: ReportTarget, description: String = ""): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.report(owner, target, description)
    }
}
