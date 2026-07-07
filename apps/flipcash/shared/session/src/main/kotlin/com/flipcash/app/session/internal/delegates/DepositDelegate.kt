package com.flipcash.app.session.internal.delegates

import com.flipcash.app.core.AppRoute
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.payments.PurchaseMethodController
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
    dispatchers: DispatcherProvider,
    featureFlagController: FeatureFlagController,
) : DepositOperations {

    private val scope = CoroutineScope(dispatchers.IO + SupervisorJob())

    init {
        featureFlagController.observe(FeatureFlag.AddMoneyUX)
            .onEach { enabled -> stateHolder.update { it.copy(addMoneyUx = enabled) } }
            .launchIn(scope)
    }

    override fun presentDepositOptions(onRoute: ((AppRoute) -> Unit)?) {
        val depositFirstUx = stateHolder.current.addMoneyUx

        val message = if (depositFirstUx) {
            resources.getString(R.string.description_noBalanceYetToGive)
        } else {
            resources.getString(R.string.description_noBalanceYetDiscover)
        }
        val cta = if (depositFirstUx) {
            resources.getString(R.string.action_addMoney)
        } else {
            resources.getString(R.string.action_discoverCurrencies)
        }

        BottomBarManager.showInfo(
            title = resources.getString(R.string.title_noBalanceYet),
            message = message,
            actions = listOf(
                BottomBarAction(
                    text = cta
                ) {
                    scope.launch {
                        if (depositFirstUx) {
                            val destination = purchaseMethodController.presentDepositOptions(popToRoot = true)
                            if (destination != null) {
                                onRoute?.invoke(destination)
                            }
                        } else {
                            onRoute?.invoke(AppRoute.Token.Discovery)
                        }
                    }
                },
            ),
            showCancel = true,
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
