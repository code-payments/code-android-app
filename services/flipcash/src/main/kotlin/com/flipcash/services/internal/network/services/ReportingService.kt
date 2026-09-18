package com.flipcash.services.internal.network.services

import com.flipcash.services.internal.network.api.ReportingApi
import com.flipcash.services.models.ReportError
import com.flipcash.services.models.ReportTarget
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.utils.toValidationOrElse
import javax.inject.Inject
import com.codeinc.flipcash.gen.reporting.v1.ReportingService as RpcReportingService

internal class ReportingService @Inject constructor(
    private val api: ReportingApi,
) {
    suspend fun report(
        owner: KeyPair,
        target: ReportTarget,
        description: String,
    ): Result<Unit> {
        return runCatching {
            api.report(owner, target, description)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcReportingService.ReportResponse.Result.OK -> Result.success(Unit)
                    RpcReportingService.ReportResponse.Result.DENIED -> Result.failure(ReportError.Denied())
                    RpcReportingService.ReportResponse.Result.NOT_FOUND -> Result.failure(ReportError.NotFound())
                    RpcReportingService.ReportResponse.Result.UNRECOGNIZED -> Result.failure(ReportError.Unrecognized())
                    else -> Result.failure(ReportError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { ReportError.Other(cause = it) })
            }
        )
    }
}
