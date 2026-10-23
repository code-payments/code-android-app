package com.flipcash.app.persistence

import com.flipcash.app.persistence.entities.decomposePending

/**
 * Decomposes any profiles the v25→v26 migration staged as raw JSON (see
 * [FlipcashDatabase.MIGRATION_25_26]) into the normalized `user_profiles` columns,
 * clearing the staging blob as it goes.
 *
 * Idempotent and safe to call repeatedly: it only touches rows that still carry a blob,
 * processing them in batches until none remain. Reads already fall back to the staged
 * blob, so this is best-effort cleanup rather than a correctness dependency.
 */
suspend fun FlipcashDatabase.backfillMigratedProfiles(batchSize: Int = 200) {
    val dao = userProfileDao()
    while (true) {
        val batch = dao.pendingMigrationBatch(batchSize)
        if (batch.isEmpty()) return
        dao.update(batch.map { it.decomposePending() })
    }
}
