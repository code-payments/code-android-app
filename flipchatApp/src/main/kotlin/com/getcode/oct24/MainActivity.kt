package com.getcode.oct24

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process.killProcess
import android.os.Process.myPid
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import com.getcode.network.exchange.Exchange
import com.getcode.network.exchange.LocalExchange
import com.getcode.payments.LocalPaymentController
import com.getcode.payments.PaymentController
import com.getcode.util.resources.LocalResources
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.vibration.LocalVibrator
import com.getcode.util.vibration.Vibrator
import com.getcode.utils.CurrencyUtils
import com.getcode.utils.LocalCurrencyUtils
import com.getcode.utils.network.LocalNetworkObserver
import com.getcode.utils.network.NetworkConnectivityListener
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import dev.bmcreations.tipkit.engines.TipsEngine
import javax.inject.Inject
import kotlin.system.exitProcess

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var resources: ResourceHelper

    @Inject
    lateinit var tipsEngine: TipsEngine

    @Inject
    lateinit var networkObserver: NetworkConnectivityListener

    @Inject
    lateinit var exchange: Exchange

    @Inject
    lateinit var currencyUtils: CurrencyUtils

    @Inject
    lateinit var vibrator: Vibrator

    @Inject
    lateinit var paymentController: PaymentController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleUncaughtException()
        enableEdgeToEdge()

        setContent {
            CompositionLocalProvider(
                LocalResources provides resources,
                LocalNetworkObserver provides networkObserver,
                LocalExchange provides exchange,
                LocalCurrencyUtils provides currencyUtils,
                LocalVibrator provides vibrator,
                LocalPaymentController provides paymentController
            ) {
                App(tipsEngine = tipsEngine)
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