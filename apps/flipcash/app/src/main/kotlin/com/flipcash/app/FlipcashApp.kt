package com.flipcash.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.currency.PreferredCurrencyController
import com.getcode.opencode.repositories.EventRepository
import com.getcode.utils.ErrorUtils
import com.getcode.utils.trace
import dev.bmcreations.phantom.connect.PhantomSdk
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import javax.inject.Inject

@HiltAndroidApp
class FlipcashApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var eventRepository: EventRepository

    @Inject
    lateinit var preferredCurrencyController: PreferredCurrencyController

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()


    override fun onCreate() {
        super.onCreate()

        RxJavaPlugins.setErrorHandler {
            ErrorUtils.handleError(it)
        }

        PhantomSdk.init(this)
        authManager.init()

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        trace("app onCreate end")
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.2)
                    .build()
            }
            .build()
    }
}