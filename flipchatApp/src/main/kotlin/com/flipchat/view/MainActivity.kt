package com.flipchat.view

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process.killProcess
import android.os.Process.myPid
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.FadeTransition
import cafe.adriel.voyager.transitions.SlideTransition
import com.flipchat.BuildConfig
import com.flipchat.MainRoot
import com.flipchat.navigation.screens.LoginScreen
import com.flipchat.util.AndroidResources
import com.getcode.theme.CodeTheme
import com.getcode.util.resources.LocalResources
import com.getcode.utils.network.LocalNetworkObserver
import com.getcode.utils.network.NetworkConnectivityListener
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.system.exitProcess

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var resources: AndroidResources

    @Inject
    lateinit var networkObserver: NetworkConnectivityListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleUncaughtException()
        enableEdgeToEdge()

        setContent {
            CompositionLocalProvider(
                LocalResources provides resources,
                LocalNetworkObserver provides networkObserver,
            ) {
                CodeTheme {
                    Navigator(MainRoot) { navigator ->
                        FadeTransition(navigator)
                    }
                }
            }
        }
    }
}

private fun Activity.handleUncaughtException() {
    val crashedKey = "isCrashed"
    if (intent.getBooleanExtra(crashedKey, false)) return
    Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
        if (BuildConfig.DEBUG) throw throwable

        FirebaseCrashlytics.getInstance().recordException(throwable)

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(crashedKey, true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
        killProcess(myPid())
        exitProcess(2)
    }
}