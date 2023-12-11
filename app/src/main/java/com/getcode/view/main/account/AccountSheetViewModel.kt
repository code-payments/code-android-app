package com.getcode.view.main.account

import android.app.Activity
import androidx.lifecycle.viewModelScope
import com.getcode.manager.AnalyticsManager
import com.getcode.manager.AuthManager
import com.getcode.model.PrefsBool
import com.getcode.network.repository.PhoneRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

data class AccountMainItem(
    val name: Int,
    val icon: Int,
    val isPhoneLinked: Boolean? = null,
    val onClick: () -> Unit
)

enum class AccountPage {
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountSheetViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val prefRepository: PrefRepository,
    private val phoneRepository: PhoneRepository,
    private val analyticsManager: AnalyticsManager
) : BaseViewModel2<AccountSheetViewModel.State, AccountSheetViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val logoClickCount: Int = 0,
        val isHome: Boolean = true,
        val page: AccountPage? = null,
        val isPhoneLinked: Boolean = false,
        val isDebug: Boolean = false
    )

    sealed interface Event {
        data class OnPhoneLinked(val linked: Boolean) : Event
        data class OnDebugChanged(val isDebug: Boolean): Event
        data object LogoClicked : Event
        data class Navigate(val page: AccountPage): Event
    }

    // TODO: handle this differently
    fun logout(activity: Activity) {
        authManager.logout(activity)
    }

    init {
        prefRepository
            .observeOrDefault(PrefsBool.IS_DEBUG_ACTIVE, false)
            .onEach {
                dispatchEvent(Event.OnDebugChanged(it))
            }.launchIn(viewModelScope)

        phoneRepository
            .phoneLinked
            .onEach { dispatchEvent(Event.OnPhoneLinked(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.LogoClicked>()
            .map { stateFlow.value.logoClickCount }
            .filter { it >= 10 }
            .map { stateFlow.value.isDebug }
            .onEach {
                prefRepository.set(PrefsBool.IS_DEBUG_ACTIVE, !it)
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Navigate>()
            .map { it.page }
            .mapNotNull { page ->
                when (page) {
                    AccountPage.DEPOSIT -> AnalyticsManager.Screen.Deposit
                    AccountPage.WITHDRAW -> AnalyticsManager.Screen.Withdraw
                    AccountPage.ACCESS_KEY -> AnalyticsManager.Screen.Backup
                    AccountPage.FAQ -> AnalyticsManager.Screen.Faq
                    else -> null
                }
            }.onEach {
                analyticsManager.open(it)
            }.launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnPhoneLinked -> { state -> state.copy(isPhoneLinked = event.linked) }
                Event.LogoClicked -> { state ->
                    val count = state.logoClickCount + 1
                    state.copy(logoClickCount = count)
                }
                is Event.OnDebugChanged -> { state ->
                    state.copy(isDebug = event.isDebug, logoClickCount = 0)
                }

                is Event.Navigate -> { state -> state }
            }
        }
    }
}