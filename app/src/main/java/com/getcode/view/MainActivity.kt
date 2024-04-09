package com.getcode.view

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import com.getcode.CodeApp
import com.getcode.LocalAnalytics
import com.getcode.LocalCurrencyUtils
import com.getcode.LocalDeeplinks
import com.getcode.LocalExchange
import com.getcode.LocalNetworkObserver
import com.getcode.LocalPhoneFormatter
import com.getcode.analytics.AnalyticsService
import com.getcode.manager.AuthManager
import com.getcode.manager.SessionManager
import com.getcode.network.TipController
import com.getcode.network.client.Client
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.PrefRepository
import com.getcode.ui.components.startupLog
import com.getcode.util.CurrencyUtils
import com.getcode.util.DeeplinkHandler
import com.getcode.util.PhoneUtils
import com.getcode.ui.utils.handleUncaughtException
import com.getcode.util.vibration.LocalVibrator
import com.getcode.util.vibration.Vibrator
import com.getcode.utils.network.NetworkConnectivityListener
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
    lateinit var tipController: TipController

    @Inject
    lateinit var analyticsManager: AnalyticsService

    @Inject
    lateinit var deeplinkHandler: DeeplinkHandler

    @Inject
    lateinit var networkObserver: NetworkConnectivityListener

    @Inject
    lateinit var phoneUtils: PhoneUtils

    @Inject
    lateinit var vibrator: Vibrator

    @Inject
    lateinit var currencyUtils: CurrencyUtils

    @Inject
    lateinit var exchange: Exchange

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
        setFullscreen()

        setContent {
            startupLog("set content")
            CompositionLocalProvider(
                LocalAnalytics provides analyticsManager,
                LocalDeeplinks provides deeplinkHandler,
                LocalNetworkObserver provides networkObserver,
                LocalPhoneFormatter provides phoneUtils,
                LocalVibrator provides vibrator,
                LocalCurrencyUtils provides currencyUtils,
                LocalExchange provides exchange,
            ) {
                CodeApp()
            }
        }

        deeplinkHandler.debounceIntent = intent
    }

    override fun onResume() {
        super.onResume()
        client.startTimer()
        tipController.checkForConnection()
    }

    override fun onPause() {
        super.onPause()
        client.stopTimer()
        tipController.stopTimer()
    }

    private fun setFullscreen() {
        enableEdgeToEdge()
    }
}

