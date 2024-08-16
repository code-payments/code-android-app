package com.getcode.view.main.account

import android.app.Activity
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.manager.AuthManager
import com.getcode.model.BuyModuleFeature
import com.getcode.model.Feature
import com.getcode.model.PrefsBool
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.network.repository.BetaOptions
import com.getcode.network.repository.FeatureRepository
import com.getcode.network.repository.PhoneRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

data class AccountMainItem(
    val type: AccountPage,
    val name: Int,
    val description: Int? = null,
    val icon: Int,
    val isPhoneLinked: Boolean? = null,
)

enum class AccountPage {
    BUY_KIN,
    DEPOSIT,
    WITHDRAW,
    PHONE,
    DELETE_ACCOUNT,
    ACCESS_KEY,
    FAQ,
    ACCOUNT_DETAILS,
    APP_SETTINGS,
    ACCOUNT_DEBUG_OPTIONS,
    LOGOUT
}

@HiltViewModel
class AccountSheetViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val prefRepository: PrefRepository,
    betaFlags: BetaFlagsRepository,
    features: FeatureRepository,
    phoneRepository: PhoneRepository,
) : BaseViewModel2<AccountSheetViewModel.State, AccountSheetViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    @Stable
    data class State(
        val logoClickCount: Int = 0,
        val items: List<AccountMainItem> = emptyList(),
        val isHome: Boolean = true,
        val page: AccountPage? = null,
        val isPhoneLinked: Boolean = false,
        val betaFlagsVisible: Boolean = false,
        val buyModule: Feature = BuyModuleFeature(),
    )

    sealed interface Event {
        data class OnPhoneLinked(val linked: Boolean) : Event
        data class BetaFlagsChanged(val options: BetaOptions) : Event
        data class OnBetaVisibilityChanged(val visible: Boolean) : Event
        data class OnBuyModuleStateChanged(val module: Feature) : Event
        data object LogoClicked : Event
        data class Navigate(val page: AccountPage) : Event
        data class OnItemsChanged(val items: List<AccountMainItem>) : Event
    }

    // TODO: handle this differently
    fun logout(activity: Activity) {
        authManager.logout(activity, onComplete = {})
    }

    init {
        betaFlags.observe()
            .distinctUntilChanged()
            .onEach { dispatchEvent(Dispatchers.Main, Event.BetaFlagsChanged(it)) }
            .launchIn(viewModelScope)

        prefRepository
            .observeOrDefault(PrefsBool.IS_DEBUG_ACTIVE, false)
            .distinctUntilChanged()
            .onEach { dispatchEvent(Dispatchers.Main, Event.OnBetaVisibilityChanged(it)) }
            .launchIn(viewModelScope)

        features.buyModule
            .onEach { dispatchEvent(Event.OnBuyModuleStateChanged(it)) }
            .launchIn(viewModelScope)

        phoneRepository
            .phoneLinked
            .onEach { dispatchEvent(Event.OnPhoneLinked(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.LogoClicked>()
            .filter { prefRepository.get(PrefsBool.IS_DEBUG_ALLOWED, false) }
            .map { stateFlow.value.logoClickCount }
            .filter { it >= 10 }
            .map { stateFlow.value.betaFlagsVisible }
            .onEach {
                prefRepository.set(PrefsBool.IS_DEBUG_ACTIVE, !it)
            }.launchIn(viewModelScope)
    }

    companion object {
        private val fullItemSet = listOf(
            AccountMainItem(
                type = AccountPage.BUY_KIN,
                name = R.string.title_buySellKin,
                icon = R.drawable.ic_currency_dollar_active
            ),
            AccountMainItem(
                type = AccountPage.DEPOSIT,
                name = R.string.title_depositKin,
                icon = R.drawable.ic_menu_deposit
            ),
            AccountMainItem(
                type = AccountPage.WITHDRAW,
                name = R.string.title_withdrawKin,
                icon = R.drawable.ic_menu_withdraw
            ),
            AccountMainItem(
                type = AccountPage.ACCOUNT_DETAILS,
                name = R.string.title_myAccount,
                icon = R.drawable.ic_menu_account
            ),
            AccountMainItem(
                type = AccountPage.APP_SETTINGS,
                name = R.string.title_appSettings,
                icon = R.drawable.ic_settings_outline,
            ),
            AccountMainItem(
                type = AccountPage.ACCOUNT_DEBUG_OPTIONS,
                name = R.string.title_betaFlags,
                icon = R.drawable.ic_bug,
            ),
            AccountMainItem(
                type = AccountPage.FAQ,
                name = R.string.title_faq,
                icon = R.drawable.ic_faq,
            ),
            AccountMainItem(
                type = AccountPage.LOGOUT,
                name = R.string.action_logout,
                icon = R.drawable.ic_menu_logout
            )
        )

        private fun buildItemSet(
            betaFlagsVisible: Boolean,
            buyModuleEnabled: Boolean,
        ): List<AccountMainItem> {
            val fullItems = fullItemSet

            val items = fullItems
                .map {
                    when (it.type) {
                        AccountPage.BUY_KIN -> {
                            if (buyModuleEnabled) {
                                it.copy(name = R.string.action_addCash)
                            } else {
                                it.copy(name = R.string.title_buySellKin)
                            }
                        }
                        else -> it
                    }
                }
                .filter {
                    if (it.type == AccountPage.ACCOUNT_DEBUG_OPTIONS) {
                        betaFlagsVisible
                    } else {
                        true
                    }
                }

            return items
        }

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnPhoneLinked -> { state -> state.copy(isPhoneLinked = event.linked) }
                Event.LogoClicked -> { state ->
                    val count = state.logoClickCount + 1
                    state.copy(logoClickCount = count)
                }

                is Event.BetaFlagsChanged -> { state ->
                    val items = buildItemSet(
                        betaFlagsVisible = state.betaFlagsVisible,
                        buyModuleEnabled = event.options.buyModuleEnabled,
                    )

                    state.copy(items = items,)
                }

                is Event.OnBetaVisibilityChanged -> { state ->
                    val items = buildItemSet(
                        betaFlagsVisible = event.visible,
                        buyModuleEnabled = state.buyModule.enabled
                    )

                    state.copy(
                        betaFlagsVisible = event.visible,
                        items = items,
                        logoClickCount = 0
                    )
                }

                is Event.OnItemsChanged -> { state -> state.copy(items = event.items) }
                is Event.Navigate -> { state -> state }
                is Event.OnBuyModuleStateChanged -> { state -> state.copy(buyModule = event.module) }
            }
        }
    }
}