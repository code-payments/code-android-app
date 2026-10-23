package com.flipcash.app.persistence

import androidx.room.RoomDatabase

/**
 * Runs [block] in a write transaction. Android keeps room-ktx's `withTransaction`; the desktop target
 * has no SupportSQLite, so it takes Room's writer connection instead.
 */
expect suspend fun <R> RoomDatabase.inTransaction(block: suspend () -> R): R
