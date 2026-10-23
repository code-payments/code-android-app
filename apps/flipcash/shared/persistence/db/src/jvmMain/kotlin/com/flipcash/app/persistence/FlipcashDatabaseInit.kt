package com.flipcash.app.persistence

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** Opens the per-account database under [directory], using the bundled SQLite driver. */
fun FlipcashDatabase.Companion.init(directory: File, entropyB64: String) =
    init(entropyB64) { name ->
        Room.databaseBuilder<FlipcashDatabase>(name = File(directory, name).absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
    }
