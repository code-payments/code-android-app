package com.getcode.services.db

import android.content.Context
import timber.log.Timber

interface ClosableDatabase {
    fun closeDb()
    suspend fun deleteDb(context: Context)
}
object Database {

    private val instances = mutableListOf<ClosableDatabase>()

    fun register(database: ClosableDatabase) {
        instances += database
    }

    fun close() {
        Timber.d("close")
        instances.onEach {
            it.closeDb()
        }

        instances.clear()
    }

    suspend fun delete(context: Context) {
        instances.onEach {
            it.deleteDb(context)
        }
    }
}