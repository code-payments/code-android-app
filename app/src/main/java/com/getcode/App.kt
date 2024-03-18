package com.getcode

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.bugsnag.android.Bugsnag
import com.getcode.manager.AuthManager
import com.getcode.network.integrity.DeviceCheck
import com.getcode.utils.ErrorUtils
import com.getcode.view.main.bill.CashBillAssets
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
        instance = this

        CashBillAssets.load(this)

        Firebase.initialize(this)
        DeviceCheck.register(this)
        authManager.init(this)

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
    }

    companion object {
        private lateinit var instance: Application

        @Deprecated(message = "Anti-pattern; inject @ApplicationContext where needed. Convert objects to classes.")
        fun getInstance(): Application {
            return instance
        }
    }
}