package com.flipcash.app.persistence

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistenceProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun openDatabase(entropy: String) {
        // Always delegate to init(): it is idempotent (no-op when this user's DB
        // is already open) and rebuilds when the entropy/DB name differs. We must
        // NOT short-circuit on isOpen() — after logout we intentionally leave the
        // DB open (see AuthManager.resetStateForUser), so a *different* user
        // signing in has to reach init() to swap to their own database. Guarding
        // on isOpen() here would hand the new user the previous user's data.
        FlipcashDatabase.init(context, entropy)
    }

    fun close() {
        FlipcashDatabase.closeDb()
    }
}