package com.flipcash.app.userflags

import java.io.File

/** Where [UserFlagsCoordinator] keeps its DataStore files and its first-run marker. */
interface UserFlagsFiles {
    /** Directory excluded from backup, for the first-run marker. */
    val noBackupDir: File

    /** The file backing the preferences DataStore called [name]. */
    fun dataStoreFile(name: String): File
}
