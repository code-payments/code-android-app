package com.flipcash.app.internal.startup

import android.content.Context
import androidx.startup.Initializer
import com.getcode.crypt.MnemonicCache

class MnemonicCacheInitializer: Initializer<Unit> {
    override fun create(context: Context) {
        // Touching the cache loads the BIP39 word list at startup rather than on first use.
        MnemonicCache.cachedCode
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}
