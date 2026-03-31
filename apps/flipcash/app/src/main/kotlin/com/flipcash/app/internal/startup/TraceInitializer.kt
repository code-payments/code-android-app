package com.flipcash.app.internal.startup

import android.content.Context
import androidx.startup.Initializer
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Configuration
import com.flipcash.app.android.BuildConfig
import com.flipcash.app.internal.debug.FlipcashDebugTree
import com.flipcash.app.internal.debug.FlipcashErrorCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class TraceInitializer: Initializer<Unit> {
    override fun create(context: Context) {
        if (BuildConfig.DEBUG) {
            Timber.plant(FlipcashDebugTree)
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                val config = Configuration.load(context)
                config.addOnError(FlipcashErrorCallback)
                Bugsnag.start(context, config)
            }
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}
