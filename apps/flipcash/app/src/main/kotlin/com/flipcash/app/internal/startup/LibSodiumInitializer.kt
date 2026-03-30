package com.flipcash.app.internal.startup

import android.content.Context
import androidx.startup.Initializer
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.ionspin.kotlin.crypto.LibsodiumInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LibSodiumInitializer: Initializer<Unit> {
    override fun create(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            LibsodiumInitializer.initializeWithCallback {
                trace("libsodium initialized", type = TraceType.Process)
            }
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}
