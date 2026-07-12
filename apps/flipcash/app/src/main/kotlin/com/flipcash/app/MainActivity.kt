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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.android.BuildConfig
import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.appsettings.LocalAppSettings
import com.flipcash.app.bill.customization.BillPlaygroundController
import com.flipcash.app.bill.customization.LocalBillPlaygroundController
import com.flipcash.app.billing.BillingClient
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.contacts.LocalContactCoordinator
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.core.toast.LocalToastController
import com.flipcash.app.core.toast.ToastController
import com.flipcash.app.core.verification.email.EmailCodeChannel
import com.flipcash.app.core.verification.email.LocalEmailCodeChannel
import com.flipcash.app.onramp.LocalCoinbaseOnRampController
import com.flipcash.app.onramp.CoinbaseOnRampController
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.internal.ui.App
import com.flipcash.app.phone.LocalPhoneUtils
import com.flipcash.app.phone.PhoneUtils
import com.flipcash.app.router.LocalRouter
import com.flipcash.app.router.Router
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.session.SessionController
import com.flipcash.app.invite.InviteController
import com.flipcash.app.invite.LocalInviteController
import com.flipcash.app.shareable.LocalShareController
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.updates.AppUpdateController
import com.flipcash.app.updates.LocalAppUpdater
import com.flipcash.services.user.UserManager
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.exchange.Exchange
import com.getcode.ui.testing.LocalUiTesting
import com.getcode.util.permissions.PermissionChecker
import com.getcode.util.permissions.ProvidePermissionChecker
import com.getcode.util.resources.LocalResources
import com.getcode.util.resources.LocalSystemSettings
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.SettingsHelper
import com.getcode.util.vibration.LocalVibrator
import com.getcode.util.vibration.Vibrator
import com.getcode.utils.network.LocalNetworkObserver
import com.getcode.utils.network.NetworkConnectivityListener
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
    lateinit var inviteController: InviteController

    @Inject
    lateinit var appSettingsCoordinator: AppSettingsCoordinator

    @Inject
    lateinit var featureFlagController: FeatureFlagController

    @Inject
    lateinit var analytics: FlipcashAnalyticsService

    @Inject
    lateinit var phoneUtils: PhoneUtils

    @Inject
    lateinit var billPlaygroundController: BillPlaygroundController

    @Inject
    lateinit var appUpdater: AppUpdateController

    @Inject
    lateinit var emailCodeChannel: EmailCodeChannel

    @Inject
    lateinit var contactCoordinator: ContactCoordinator

    @Inject
    lateinit var toastController: ToastController

    @Inject
    lateinit var coinbaseOnRampController: CoinbaseOnRampController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleUncaughtException()
        enableEdgeToEdge()

        // Warm libphonenumber metadata off the main thread so the phone
        // verification / country picker screens are instant and never block
        // the UI thread building the country list.
        lifecycleScope.launch(Dispatchers.Default) { phoneUtils.ensureLoaded() }

        setContent {
            CompositionLocalProvider(
                LocalResources provides resources,
                LocalSystemSettings provides settingsHelper,
                LocalNetworkObserver provides networkObserver,
                LocalExchange provides exchange,
                LocalAnalytics provides analytics,
                LocalVibrator provides vibrator,
                LocalRouter provides router,
                LocalUserManager provides userManager,
                LocalSessionController provides sessionController,
                LocalShareController provides shareController,
                LocalInviteController provides inviteController,
                LocalAppSettings provides appSettingsCoordinator,
                LocalFeatureFlags provides featureFlagController,
                LocalPhoneUtils provides phoneUtils,
                LocalBillPlaygroundController provides billPlaygroundController,
                LocalAppUpdater provides appUpdater,
                LocalEmailCodeChannel provides emailCodeChannel,
                LocalContactCoordinator provides contactCoordinator,
                LocalToastController provides toastController,
                LocalCoinbaseOnRampController provides coinbaseOnRampController,
                LocalUiTesting provides intent.getBooleanExtra(UI_TEST, false),
            ) {
                ProvidePermissionChecker(permissionChecker) {
                    Rinku {
                        App(
                            tipsEngine = tipsEngine,
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