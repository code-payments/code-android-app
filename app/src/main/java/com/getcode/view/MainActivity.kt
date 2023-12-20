package com.getcode.view

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Debug
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.util.Consumer
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.findNavController
import com.getcode.CodeApp
import com.getcode.LocalAnalytics
import com.getcode.manager.AnalyticsManager
import com.getcode.manager.AuthManager
import com.getcode.manager.SessionManager
import com.getcode.network.client.Client
import com.getcode.network.repository.PrefRepository
import com.getcode.rememberCodeAppState
import com.getcode.util.DeeplinkState
import com.getcode.util.handleUncaughtException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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

    private var currentNavHostController: NavHostController? = null

    override fun onPause() {
        super.onPause()
        client.stopTimer()
    }

    /**
     * The compose navigation controller does not play nice with single task activities.
     * Invoking the navigation controller here will cause the intent to be fired
     * again we want to debounce this once when the activity is started with an intent.
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            val cachedIntent = DeeplinkState.debounceIntent
            if (cachedIntent != null && cachedIntent?.data == intent.data) {
                Timber.d("Debouncing Intent " + intent.data)
                DeeplinkState.debounceIntent = null
                return
            }
            Timber.d("Intent " + intent.data)
            currentNavHostController?.handleDeepLink(intent)
            DeeplinkState.debounceIntent = intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analyticsManager.onAppStart()
        handleUncaughtException()
        authManager.init(this)
        setFullscreen()
        setContent {
            val appState = rememberCodeAppState()
            currentNavHostController = appState.navController
            CompositionLocalProvider(LocalAnalytics provides analyticsManager) {
                CodeApp(appState)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        client.startTimer()
    }

    private fun setFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            /*val controller = (window.decorView).windowInsetsController
            controller?.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsets.Type.navigationBars())*/
            //controller?.hide(WindowInsets.Type.ime())
        }
    }
}

