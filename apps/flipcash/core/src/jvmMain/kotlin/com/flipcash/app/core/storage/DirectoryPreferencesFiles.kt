package com.flipcash.app.core.storage

import java.io.File

/** [PreferencesFiles] rooted at [directory], using DataStore's `<name>.preferences_pb` naming. */
class DirectoryPreferencesFiles(private val directory: File) : PreferencesFiles {
    override fun file(name: String): File = File(directory, "datastore/$name.preferences_pb")
}
