package com.flipcash.app.internal.startup

import android.content.Context
import androidx.startup.Initializer
import com.getcode.crypt.MnemonicCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MnemonicCacheInitializer: Initializer<Unit> {
    override fun create(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            MnemonicCache.init(context)
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}
