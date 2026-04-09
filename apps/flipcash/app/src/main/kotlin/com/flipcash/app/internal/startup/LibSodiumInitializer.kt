package com.flipcash.app.internal.startup

import android.content.Context
import androidx.startup.Initializer
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.ionspin.kotlin.crypto.LibsodiumInitializer

class LibSodiumInitializer: Initializer<Unit> {
    override fun create(context: Context) {
        // Must run synchronously on the main thread: Initializer.create runs at app
        // startup before any UI is attached, and downstream callers (e.g. Box.keypair)
        // hit sodiumJna as soon as Compose attaches. Dispatching this to a background
        // coroutine creates a race where the native lib may not be loaded in time.
        // On Android, initializeWithCallback is synchronous — it loads the .so via
        // JNA, calls sodium_init, then invokes the callback on the same thread.
        LibsodiumInitializer.initializeWithCallback {
            trace("libsodium initialized", type = TraceType.Process)
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}
