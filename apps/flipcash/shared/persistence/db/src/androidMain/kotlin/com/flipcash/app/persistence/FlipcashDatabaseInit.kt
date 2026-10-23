package com.flipcash.app.persistence

import android.content.Context
import androidx.room.Room

/** Opens the per-account database in the app's database directory. */
fun FlipcashDatabase.Companion.init(context: Context, entropyB64: String) =
    init(entropyB64) { name -> Room.databaseBuilder(context, FlipcashDatabase::class.java, name) }

/** Whether the current database's connection is open. Room exposes this on Android only. */
fun FlipcashDatabase.Companion.isOpen() = getInstance()?.isOpen == true
