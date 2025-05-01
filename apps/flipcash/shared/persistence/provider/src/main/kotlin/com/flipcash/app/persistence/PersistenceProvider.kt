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
        if (FlipcashDatabase.isOpen()) return
        FlipcashDatabase.init(context, entropy)
    }

    fun close() {
        FlipcashDatabase.closeDb()
    }
}