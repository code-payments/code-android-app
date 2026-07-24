package com.flipcash.app.session.internal.delegates

import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.AppRoute
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.session.DepositOperations
import com.flipcash.app.session.internal.SessionStateHolder
import com.flipcash.app.tokens.UsdcDepositSweep
import com.flipcash.core.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [DepositOperations] and manages USDC deposit sweeps.
 *
 * This delegate owns the "getting money into the wallet" domain:
 * - Presenting deposit/discovery options when the wallet is empty.
 * - Observing the `depositFirstUx` feature flag.
 * - Executing and cancelling USDC deposit sweeps on lifecycle transitions.
 *
 * @see com.flipcash.app.session.internal.RealSessionController
 */
@Singleton
class DepositDelegate @Inject constructor(
    private val stateHolder: SessionStateHolder,
    private val purchaseMethodController: PurchaseMethodController,
    private val usdcSweep: UsdcDepositSweep,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
    private val analytics: FlipcashAnalyticsService,
    dispatchers: DispatcherProvider,
    featureFlagController: FeatureFlagController,
) : DepositOperations {

    private val scope = CoroutineScope(dispatchers.IO + SupervisorJob())

    init {
        featureFlagController.observe(FeatureFlag.AddMoneyUX)
            .onEach { enabled -> stateHolder.update { it.copy(addMoneyUx = enabled) } }
            .launchIn(scope)
    }

    override fun presentDepositOptions(onDismiss: (() -> Unit)?, onRoute: ((AppRoute) -> Unit)?) {
        // Prompt to add money only when the wallet is empty and add-money is available.
        // Otherwise the user has funds (e.g. reserves) but nothing giveable — or can't
        // add money at all — so steer them to discover/buy a currency.
        val addMoneyEnabled = stateHolder.current.addMoneyUx
        val hasBalance = stateHolder.current.hasBalance

        if (!hasBalance) {
            presentAddMoney(addMoneyEnabled, onRoute, onDismiss)
        } else {
            presentDiscoverCurrencies(onRoute, onDismiss)
        }
    }

    private fun presentAddMoney(
        addMoneyEnabled: Boolean,
        onRoute: ((AppRoute) -> Unit)?,
        onDismiss: (() -> Unit)?
    ) {
        BottomBarManager.showInfo(
            title = resources.getString(R.string.title_noBalanceYet),
            message = if (addMoneyEnabled) {
                resources.getString(R.string.description_noBalanceYetToGive)
            } else {
                resources.getString(R.string.description_noBalanceYetDiscover)
            },
            actions = listOf(
                BottomBarAction(
                    text = resources.getString(R.string.action_addMoney)
                ) {
                    scope.launch {
                        // the scanner's Give action is the only session caller today
                        analytics.addMoneyOpened(Analytics.AddMoneySource.Scanner)
                        val destination = purchaseMethodController.presentDepositOptions(popToRoot = true)
                        if (destination != null) {
                            onRoute?.invoke(destination)
                        }
                    }
                },
            ),
            showCancel = true,
            onDismiss = { onDismiss?.invoke() }
        )
    }

    private fun presentDiscoverCurrencies(onRoute: ((AppRoute) -> Unit)?, onDismiss: (() -> Unit)?) {
        BottomBarManager.showInfo(
            title = resources.getString(R.string.title_noCommunityCurrenciesYet),
            message = resources.getString(R.string.description_noCommunityCurrenciesYet),
            actions = listOf(
                BottomBarAction(
                    text = resources.getString(R.string.action_discoverCurrencies)
                ) {
                    onRoute?.invoke(AppRoute.Token.Discovery)
                },
            ),
            showCancel = true,
            onDismiss = { onDismiss?.invoke() }
        )
    }

    fun sweepIfNeeded() {
        val owner = userManager.accountCluster ?: return
        if (userManager.authState.canAccessAuthenticatedApis) {
            usdcSweep.execute(owner)
        }
    }

    fun cancelSweep() {
        usdcSweep.cancel()
    }
}
