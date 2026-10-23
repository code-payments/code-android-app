package com.getcode.utils

/**
 * Build-type flags the app sets at startup.
 *
 * KMP library modules have a single variant and no `BuildConfig`, so a value that used to differ
 * between a library's debug and release builds is read here instead. Both default to the release
 * value until the app sets them.
 */
object BuildFlags {
    /** What a library's `BuildConfig.DEBUG` used to report. */
    @Volatile
    var isDebug: Boolean = false

    /** Whether [ErrorUtils] forwards errors to its reporters. */
    @Volatile
    var notifyErrors: Boolean = true
}
