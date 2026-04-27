package com.flipcash.app.internal.startup

import android.content.Context
import androidx.startup.Initializer
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Configuration
import com.flipcash.app.android.BuildConfig
import com.flipcash.app.internal.debug.FlipcashDebugTree
import com.flipcash.app.internal.debug.FlipcashErrorCallback
import com.flipcash.app.updates.ReleaseStageProvider
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class TraceInitializer: Initializer<Unit> {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TraceEntryPoint {
        fun releaseStageProvider(): ReleaseStageProvider
    }

    override fun create(context: Context) {
        TraceManager.initialize(context)

        if (BuildConfig.DEBUG) {
            Timber.plant(FlipcashDebugTree)
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                val entryPoint = EntryPointAccessors.fromApplication(context, TraceEntryPoint::class.java)
                val stageProvider = entryPoint.releaseStageProvider()
                val versionCode = BuildConfig.VERSION_CODE

                val initialStage = stageProvider.loadCachedStage(versionCode)

                val config = Configuration.load(context).apply {
                    releaseStage = initialStage.name
                    addOnError(FlipcashErrorCallback)
                    addOnSend { event ->
                        event.app.releaseStage = stageProvider.resolvedStage.name
                        true
                    }
                }
                Bugsnag.start(context, config)

                ErrorUtils.addReporter(BugsnagErrorReporter())
                TraceManager.addSink(BugsnagBreadcrumbSink())
                TraceManager.setOnUserIdChanged { id ->
                    Bugsnag.setUser(id, null, null)
                }

                stageProvider.fetchAndCache(versionCode)
            }
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return emptyList()
    }
}
