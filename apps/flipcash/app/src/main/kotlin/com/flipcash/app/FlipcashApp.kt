package com.flipcash.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.flipcash.app.auth.AuthManager
import okio.Path.Companion.toOkioPath
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
        return ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            // Use Coil's own disk cache as the single persistent image layer.
            //
            // NOTE: do NOT disable the disk cache here. Coil's NetworkFetcher derives the
            // request's Cache-Control from these policies: with diskCachePolicy(DISABLED)
            // it stamps `Cache-Control: no-cache, no-store` on every request, which makes
            // any HTTP cache (OkHttp's included) refuse to read *or* write the response —
            // so every image is fully re-downloaded on every launch. Keeping the disk
            // cache enabled lets Coil persist responses and serve them without a network
            // trip on subsequent cold starts.
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            // Treat cached blobs as immutable so we never re-download bytes we already hold (which
            // would flash the BlurHash again). The default strategy respects HTTP cache headers and
            // would revalidate/re-fetch the ephemeral, expiring download URLs; blobs are static.
            .components {
                add(OkHttpNetworkFetcherFactory(cacheStrategy = { ImmutableBlobCacheStrategy }))
            }
            .build()
    }
}