package com.flipcash.app.internal.startup

import android.content.Context
import androidx.startup.Initializer
import com.getcode.crypt.MnemonicCache

class MnemonicCacheInitializer: Initializer<Unit> {
    override fun create(context: Context) {
        MnemonicCache.init(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}