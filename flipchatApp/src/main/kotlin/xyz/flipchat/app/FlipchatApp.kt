package xyz.flipchat.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.bugsnag.android.Bugsnag
import com.getcode.crypt.MnemonicCache
import com.getcode.utils.ErrorUtils
import com.getcode.utils.trace
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.initialize
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import timber.log.Timber
import xyz.flipchat.app.auth.AuthManager
import javax.inject.Inject

@HiltAndroidApp
class FlipchatApp : Application() {

    @Inject
    lateinit var authManager: AuthManager

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

        Firebase.initialize(this)
        Firebase.crashlytics.setCrashlyticsCollectionEnabled(BuildConfig.NOTIFY_ERRORS || !BuildConfig.DEBUG)
        MnemonicCache.init(this)
        authManager.init { trace("NaCl init") }

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        trace("app onCreate end")
    }
}