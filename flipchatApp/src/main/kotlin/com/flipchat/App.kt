package com.flipchat

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.bugsnag.android.Bugsnag
import com.flipchat.manager.AuthManager
import com.getcode.crypt.MnemonicCache
import com.getcode.network.integrity.DeviceCheck
import com.getcode.utils.ErrorUtils
import com.getcode.utils.trace
import com.google.firebase.Firebase
import com.google.firebase.initialize
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {

    @Inject
    lateinit var authManager: AuthManager

    override fun onCreate() {
        super.onCreate()

        Firebase.initialize(this)
        DeviceCheck.register(this)
        MnemonicCache.init(this)
        authManager.init()

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        RxJavaPlugins.setErrorHandler {
            ErrorUtils.handleError(it)
        }

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
        trace("app onCreate end")
    }
}