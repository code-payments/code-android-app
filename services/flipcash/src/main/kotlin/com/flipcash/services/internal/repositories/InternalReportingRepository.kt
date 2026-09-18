package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.network.services.ReportingService
import com.flipcash.services.models.ReportTarget
import com.flipcash.services.repository.ReportingRepository
import com.getcode.ed25519.Ed25519
import com.getcode.utils.ErrorUtils

internal class InternalReportingRepository(
    private val service: ReportingService,
) : ReportingRepository {
    override suspend fun report(
        owner: Ed25519.KeyPair,
        target: ReportTarget,
        description: String,
    ): Result<Unit> = service.report(owner, target, description)
        .onFailure { ErrorUtils.handleError(it) }
}
