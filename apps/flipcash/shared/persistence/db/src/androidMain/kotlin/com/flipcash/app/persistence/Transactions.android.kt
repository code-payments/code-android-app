package com.flipcash.app.persistence

import androidx.room.RoomDatabase
import androidx.room.withTransaction

actual suspend fun <R> RoomDatabase.inTransaction(block: suspend () -> R): R = withTransaction(block)
