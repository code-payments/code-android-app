package com.flipcash.app.persistence

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistenceProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Long-lived scope for one-off maintenance that must outlive the caller's frame.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun openDatabase(entropy: String) {
        // Always delegate to init(): it is idempotent (no-op when this user's DB
        // is already open) and rebuilds when the entropy/DB name differs. We must
        // NOT short-circuit on isOpen() — after logout we intentionally leave the
        // DB open (see AuthManager.resetStateForUser), so a *different* user
        // signing in has to reach init() to swap to their own database. Guarding
        // on isOpen() here would hand the new user the previous user's data.
        FlipcashDatabase.init(context, entropy)

        // Drain any profiles the v25→v26 migration staged as raw JSON into the
        // normalized user_profiles columns. Fire-and-forget so login isn't blocked;
        // idempotent and cheap (only touches rows still carrying a blob), and reads
        // already fall back to the staged blob, so this is best-effort cleanup.
        val database = FlipcashDatabase.getInstance() ?: return
        scope.launch { runCatching { database.backfillMigratedProfiles() } }
    }

    fun close() {
        FlipcashDatabase.closeDb()
    }
}
