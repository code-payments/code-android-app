package com.getcode.view

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import com.getcode.CodeApp
import com.getcode.LocalAnalytics
import com.getcode.LocalDeeplinks
import com.getcode.LocalNetwork
import com.getcode.LocalPhoneFormatter
import com.getcode.manager.AnalyticsManager
import com.getcode.manager.AuthManager
import com.getcode.manager.SessionManager
import com.getcode.network.client.Client
import com.getcode.network.repository.PrefRepository
import com.getcode.util.DeeplinkHandler
import com.getcode.util.PhoneUtils
import com.getcode.util.handleUncaughtException
import com.getcode.util.vibration.LocalVibrator
import com.getcode.util.vibration.Vibrator
import com.getcode.utils.NetworkUtils
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var prefRepository: PrefRepository

    @Inject
    lateinit var client: Client

    @Inject
    lateinit var analyticsManager: AnalyticsManager

    @Inject
    lateinit var deeplinkHandler: DeeplinkHandler

    @Inject
    lateinit var networkUtils: NetworkUtils

    @Inject
    lateinit var phoneUtils: PhoneUtils

    @Inject
    lateinit var vibrator: Vibrator

    /**
     * The compose navigation controller does not play nice with single task activities.
     * Invoking the navigation controller here will cause the intent to be fired
     * again we want to debounce this once when the activity is started with an intent.
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            val cachedIntent = deeplinkHandler.debounceIntent
            if (cachedIntent != null && cachedIntent.data == intent.data) {
                Timber.d("Debouncing Intent " + intent.data)
                deeplinkHandler.debounceIntent = null
                return
            }
            deeplinkHandler.debounceIntent = intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analyticsManager.onAppStart()
        handleUncaughtException()
        authManager.init(this)
        setFullscreen()
        deeplinkHandler.debounceIntent = deeplinkHandler.checkIntent(intent)
        setContent {
            CompositionLocalProvider(
                LocalAnalytics provides analyticsManager,
                LocalDeeplinks provides deeplinkHandler,
                LocalNetwork provides networkUtils,
                LocalPhoneFormatter provides phoneUtils,
                LocalVibrator provides vibrator,
            ) {
                CodeApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        client.startTimer()
    }

    override fun onPause() {
        super.onPause()
        client.stopTimer()
    }

    private fun setFullscreen() {
        enableEdgeToEdge()
    }
}

