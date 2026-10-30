package com.getcode.utils

import android.content.Context
import android.database.SQLException
import android.os.SystemClock
import timber.log.Timber

internal actual object PlatformLog {
    actual fun log(priority: Int, tag: String?, message: String) {
        val tree = if (tag == null) Timber else Timber.tag(tag)
        tree.log(priority, message)
    }

    actual fun error(throwable: Throwable) {
        Timber.e(throwable)
    }

    actual fun error(throwable: Throwable, message: String?) {
        Timber.e(throwable, message)
    }
}

internal actual fun isPlatformSqlException(throwable: Throwable): Boolean = throwable is SQLException

internal actual fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

/**
 * Plants a [FileTree] and initializes [TraceManager] with it. Safe to call multiple times;
 * subsequent calls are no-ops.
 */
fun TraceManager.initialize(context: Context) {
    if (initialized) return
    val tree = FileTree(context, plugins = { plugins })
    Timber.plant(tree)
    initialize(tree)
}
