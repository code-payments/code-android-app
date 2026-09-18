package com.flipcash.services.repository

import com.flipcash.services.models.ReportTarget
import com.getcode.ed25519.Ed25519.KeyPair

interface ReportingRepository {
    /** Files a report against [target]. Reporting the same target more than once is a no-op. */
    suspend fun report(
        owner: KeyPair,
        target: ReportTarget,
        description: String = "",
    ): Result<Unit>
}
