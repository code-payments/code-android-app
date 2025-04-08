package com.flipcash.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.bugsnag.android.Bugsnag
import com.getcode.crypt.MnemonicCache
import com.getcode.utils.ErrorUtils
import com.getcode.utils.trace
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import timber.log.Timber

@HiltAndroidApp
class FlipcashApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(object : Timber.DebugTree() {
                override fun createStackElementTag(element: StackTraceElement): String {
                    val elementTag = super.createStackElementTag(element)
                        .orEmpty()
                        .split("$")
                        .filter { it.isNotEmpty() }
                        .take(2)
                        .joinToString(" ")
                        .replace("_", " ")

                    val methodName = element.methodName
                        .split("$")
                        .firstOrNull()
                        .orEmpty()

                    return String.format(
                        "%s | %s ",
                        elementTag,
                        methodName
                    )
                }
            })
        } else {
            Bugsnag.start(this)
        }

        RxJavaPlugins.setErrorHandler {
            ErrorUtils.handleError(it)
        }

//        Firebase.initialize(this)
//        Firebase.crashlytics.setCrashlyticsCollectionEnabled(BuildConfig.NOTIFY_ERRORS || !BuildConfig.DEBUG)
        MnemonicCache.init(this)
//        authManager.init { trace("NaCl init") }

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