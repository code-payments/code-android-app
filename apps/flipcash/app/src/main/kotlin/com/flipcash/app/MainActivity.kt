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
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.android.BuildConfig
import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.appsettings.LocalAppSettings
import com.flipcash.app.bill.customization.BillPlaygroundController
import com.flipcash.app.bill.customization.LocalBillPlaygroundController
import com.flipcash.app.billing.BillingClient
import com.flipcash.app.billing.LocalBillingClient
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.internal.ui.App
import com.flipcash.app.onramp.LocalOnRampAmountController
import com.flipcash.app.onramp.OnRampAmountController
import com.flipcash.app.payments.LocalPaymentController
import com.flipcash.app.payments.PaymentController
import com.flipcash.app.phone.LocalPhoneUtils
import com.flipcash.app.phone.PhoneUtils
import com.flipcash.app.router.LocalRouter
import com.flipcash.app.router.Router
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.session.SessionController
import com.flipcash.app.shareable.LocalShareController
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.updates.AppUpdateController
import com.flipcash.app.updates.LocalAppUpdater
import com.flipcash.services.user.UserManager
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.compose.LocalTransactionController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.solana.rpc.RpcConfig
import com.getcode.ui.testing.LocalUiTesting
import com.getcode.util.permissions.PermissionChecker
import com.getcode.util.permissions.ProvidePermissionChecker
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

    @Inject
    lateinit var billing: BillingClient

    @Inject
    lateinit var permissionChecker: PermissionChecker

    @Inject
    lateinit var shareController: ShareSheetController

    @Inject
    lateinit var appSettingsCoordinator: AppSettingsCoordinator

    @Inject
    lateinit var featureFlagController: FeatureFlagController

    @Inject
    lateinit var paymentController: PaymentController

    @Inject
    lateinit var analytics: FlipcashAnalyticsService

    @Inject
    lateinit var solanaRpcConfig: RpcConfig

    @Inject
    lateinit var phoneUtils: PhoneUtils

    @Inject
    lateinit var onRampAmountController: OnRampAmountController

    @Inject
    lateinit var billPlaygroundController: BillPlaygroundController

    @Inject
    lateinit var appUpdater: AppUpdateController

    @Inject
    lateinit var transactionController: TransactionController


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
                LocalAnalytics provides analytics,
                LocalCurrencyUtils provides currencyUtils,
                LocalVibrator provides vibrator,
                LocalRouter provides router,
                LocalUserManager provides userManager,
                LocalSessionController provides sessionController,
                LocalBillingClient provides billing,
                LocalShareController provides shareController,
                LocalAppSettings provides appSettingsCoordinator,
                LocalFeatureFlags provides featureFlagController,
                LocalPaymentController provides paymentController,
                LocalOnRampAmountController provides onRampAmountController,
                LocalTransactionController provides transactionController,
                LocalPhoneUtils provides phoneUtils,
                LocalBillPlaygroundController provides billPlaygroundController,
                LocalAppUpdater provides appUpdater,
                LocalUiTesting provides intent.getBooleanExtra(UI_TEST, false),
            ) {
                ProvidePermissionChecker(permissionChecker) {
                    Rinku {
                        App(
                            tipsEngine = tipsEngine,
                            solanaRpcConfig = solanaRpcConfig,
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val UI_TEST = "isUiTest"
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