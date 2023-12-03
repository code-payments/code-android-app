package com.getcode.view.main.account

import android.app.Activity
import com.getcode.BuildConfig
import com.getcode.manager.AnalyticsManager
import com.getcode.manager.AuthManager
import com.getcode.model.PrefsBool
import com.getcode.network.repository.PhoneRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

data class AccountMainItem(
    val name: Int,
    val icon: Int,
    val isPhoneLinked: Boolean? = null,
    val onClick: () -> Unit
)

enum class AccountPage {
    BUY_AND_SELL_KIN,
    DEPOSIT,
    WITHDRAW,
    PHONE,
    ACCESS_KEY,
    FAQ,
    ACCOUNT_DETAILS,
    ACCOUNT_DEBUG_OPTIONS
}

data class AccountSheetUiModel(
    val isHome: Boolean = true,
    val page: AccountPage? = null,
    val isPhoneLinked: Boolean = false,
    val isDebug: Boolean = false
)

@HiltViewModel
class AccountSheetViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val prefRepository: PrefRepository,
    private val phoneRepository: PhoneRepository,
    private val analyticsManager: AnalyticsManager
) : BaseViewModel() {

    val uiFlow = MutableStateFlow(AccountSheetUiModel())
    var logoClickCount = 0

    fun reset() {
        uiFlow.tryEmit(uiFlow.value.copy(isPhoneLinked = phoneRepository.phoneLinked))
        prefRepository.getFirstOrDefault(PrefsBool.IS_DEBUG_ACTIVE, false)
            .subscribe { value: Boolean ->
                uiFlow.tryEmit(uiFlow.value.copy(isDebug = value))
            }
    }

    fun logout(activity: Activity) {
        authManager.logout(activity)
    }

    fun onNavigation(page: AccountPage) {
        when (page) {
            AccountPage.BUY_AND_SELL_KIN -> AnalyticsManager.Screen.BuyAndSellKin
            AccountPage.DEPOSIT -> AnalyticsManager.Screen.Deposit
            AccountPage.WITHDRAW -> AnalyticsManager.Screen.Withdraw
            AccountPage.ACCESS_KEY -> AnalyticsManager.Screen.Backup
            AccountPage.FAQ -> AnalyticsManager.Screen.Faq
            else -> null
        }?.let { analyticsManager.open(it) }
    }

    fun onLogoClick() {
        logoClickCount++
        if (logoClickCount >= 10) {
            logoClickCount = 0
            val isDebug = uiFlow.value.isDebug

            prefRepository.getFirstOrDefault(PrefsBool.IS_DEBUG_ALLOWED, false)
                .subscribe { isDebugAllowed: Boolean ->
                if (isDebug || isDebugAllowed || BuildConfig.DEBUG) {
                    uiFlow.tryEmit(uiFlow.value.copy(isDebug = !isDebug))
                    prefRepository.set(PrefsBool.IS_DEBUG_ACTIVE, !isDebug)
                }
            }
        }
    }
}