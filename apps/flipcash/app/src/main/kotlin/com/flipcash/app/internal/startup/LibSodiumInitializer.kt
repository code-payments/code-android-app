package com.flipcash.app.internal.startup

import android.content.Context
import androidx.startup.Initializer
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.ionspin.kotlin.crypto.LibsodiumInitializer

class LibSodiumInitializer: Initializer<Unit> {
    override fun create(context: Context) {
        LibsodiumInitializer.initializeWithCallback {
            trace("libsodium initialized", type = TraceType.Process)
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}