package com.flipcash.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.flipcash.app.auth.AuthManager
import okhttp3.Cache
import okhttp3.OkHttpClient
import com.flipcash.app.currency.PreferredCurrencyController
import com.getcode.opencode.repositories.EventRepository
import com.getcode.utils.trace
import dev.bmcreations.phantom.connect.PhantomSdk
import dagger.hilt.android.HiltAndroidApp
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
        PhantomSdk.init(this)
        authManager.init()

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        trace("app onCreate end")
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .cache(Cache(context.cacheDir.resolve("http_image_cache"), 50L * 1024 * 1024))
            .build()
        return ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .build()
    }
}