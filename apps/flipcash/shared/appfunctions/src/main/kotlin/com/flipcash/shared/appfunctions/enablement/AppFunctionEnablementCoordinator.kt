package com.flipcash.shared.appfunctions.enablement

import android.content.Context
import android.os.Build
import androidx.appfunctions.AppFunctionManager
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.services.user.UserManager
import com.flipcash.shared.appfunctions.functions.BalanceFunctionsIds
import com.flipcash.shared.appfunctions.functions.CashLinkFunctionsIds
import com.flipcash.shared.appfunctions.functions.DepositFunctionsIds
import com.flipcash.shared.appfunctions.functions.TokenInfoFunctionsIds
import com.flipcash.shared.appfunctions.functions.TransactionFunctionsIds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppFunctionEnablementCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val featureFlagController: FeatureFlagController,
    private val userManager: UserManager,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val functionIds = listOf(
        BalanceFunctionsIds.GET_BALANCE_ID,
        TransactionFunctionsIds.GET_TRANSACTION_HISTORY_ID,
        DepositFunctionsIds.GET_DEPOSIT_ADDRESS_ID,
        TokenInfoFunctionsIds.GET_TOKEN_INFO_ID,
        CashLinkFunctionsIds.SEND_CASH_LINK_ID,
        CashLinkFunctionsIds.CLAIM_CASH_LINK_ID,
    )

    fun init() {
        if (Build.VERSION.SDK_INT < 36) return

        scope.launch {
            val flagEnabled: Flow<Boolean> =
                featureFlagController.observe(FeatureFlag.AppFunctions)
            val loggedIn: Flow<Boolean> =
                userManager.state.map { it.authState.canAccessAuthenticatedApis }

            combine(flagEnabled, loggedIn) { flag: Boolean, auth: Boolean -> flag && auth }
                .distinctUntilChanged()
                .collect { enabled ->
                    setFunctionsEnabled(enabled)
                }
        }
    }

    private suspend fun setFunctionsEnabled(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < 36) return
        val manager = AppFunctionManager.getInstance(context) ?: return
        val state = if (enabled) {
            AppFunctionManager.APP_FUNCTION_STATE_ENABLED
        } else {
            AppFunctionManager.APP_FUNCTION_STATE_DISABLED
        }
        for (functionId in functionIds) {
            try {
                manager.setAppFunctionEnabled(functionId, state)
            } catch (e: Exception) {
                Timber.w(e, "Failed to set AppFunction enabled state for $functionId")
            }
        }
    }
}
