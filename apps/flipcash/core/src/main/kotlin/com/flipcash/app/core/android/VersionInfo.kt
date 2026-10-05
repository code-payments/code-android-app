package com.flipcash.app.core.android

import javax.inject.Inject

class VersionInfo @Inject constructor(
    val versionName: String = "",
    val versionCode: Int = 0,
    /** The full commit SHA the build was made from; empty when the build carries none. */
    val commitSha: String = "",
    /** Whether the build was made with uncommitted changes to tracked files. */
    val isDirty: Boolean = false,
) {
    /**
     * The commit as the version footer shows it: the first [COMMIT_LABEL_LENGTH] characters of
     * [commitSha], with `*` appended for a build with uncommitted changes. Null when the
     * build carries no commit. The iOS footer uses the same format.
     */
    val commitLabel: String?
        get() {
            if (commitSha.isBlank()) return null
            val short = commitSha.trim().take(COMMIT_LABEL_LENGTH)
            return if (isDirty) "$short*" else short
        }

    companion object {
        const val COMMIT_LABEL_LENGTH = 10
    }
}
