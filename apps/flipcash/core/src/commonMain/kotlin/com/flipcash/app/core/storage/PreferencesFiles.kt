package com.flipcash.app.core.storage

import java.io.File

/** Where DataStore preference files live: the app's files dir on Android, an app data dir on desktop. */
fun interface PreferencesFiles {
    /** The file backing the preferences store called [name]. */
    fun file(name: String): File
}
