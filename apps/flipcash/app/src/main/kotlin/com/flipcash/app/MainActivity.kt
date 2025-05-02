package com.flipcash.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process.killProcess
import android.os.Process.myPid
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.router.LocalRouter
import com.flipcash.app.session.SessionController
import com.flipcash.app.router.Router
import com.flipcash.app.session.LocalSessionController
import com.flipcash.services.LocalBillingClient
import com.flipcash.services.billing.BillingClient
import com.flipcash.services.user.UserManager
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.exchange.Exchange
import com.getcode.util.permissions.LocalPermissionChecker
import com.getcode.util.permissions.PermissionChecker
import com.getcode.util.resources.LocalResources
import com.getcode.util.resources.LocalSystemSettings
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.SettingsHelper
import com.getcode.util.vibration.LocalVibrator
import com.getcode.util.vibration.Vibrator
import com.getcode.utils.CurrencyUtils
import com.getcode.utils.LocalCurrencyUtils
import com.getcode.utils.network.LocalNetworkObserver
import com.getcode.utils.network.NetworkConnectivityListener
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import dev.bmcreations.tipkit.engines.TipsEngine
import dev.theolm.rinku.compose.ext.Rinku
import javax.inject.Inject
import kotlin.system.exitProcess

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var resources: ResourceHelper

    @Inject
    lateinit var settingsHelper: SettingsHelper

    @Inject
    lateinit var tipsEngine: TipsEngine

    @Inject
    lateinit var networkObserver: NetworkConnectivityListener

    @Inject
    lateinit var currencyUtils: CurrencyUtils

    @Inject
    lateinit var vibrator: Vibrator

    @Inject
    lateinit var router: Router

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var sessionController: SessionController

    @Inject
    lateinit var exchange: Exchange
//
//    @Inject
//    lateinit var paymentController: PaymentController
//
    @Inject
    lateinit var billing: BillingClient

    @Inject
    lateinit var permissionChecker: PermissionChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleUncaughtException()
        enableEdgeToEdge()

        setContent {
            CompositionLocalProvider(
                LocalResources provides resources,
                LocalSystemSettings provides settingsHelper,
                LocalNetworkObserver provides networkObserver,
                LocalExchange provides exchange,
                LocalCurrencyUtils provides currencyUtils,
                LocalVibrator provides vibrator,
                LocalRouter provides router,
                LocalUserManager provides userManager,
                LocalSessionController provides sessionController,
//                LocalPaymentController provides paymentController,
                LocalBillingClient provides billing,
                LocalPermissionChecker provides permissionChecker,
            ) {
                Rinku {
                    App(tipsEngine = tipsEngine)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
//        client.startTimer()
        billing.connect()
    }

    override fun onStop() {
        super.onStop()
//        client.stopTimer()
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