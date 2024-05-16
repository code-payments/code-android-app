package com.getcode.view.main.balance

import androidx.lifecycle.viewModelScope
import com.getcode.model.BalanceCurrencyFeature
import com.getcode.model.BuyModuleFeature
import com.getcode.model.Chat
import com.getcode.model.Currency
import com.getcode.model.Feature
import com.getcode.model.PrefsBool
import com.getcode.model.Rate
import com.getcode.network.BalanceController
import com.getcode.network.HistoryController
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.network.repository.FeatureRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.util.Kin
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.view.BaseViewModel2
import com.getcode.view.main.getKin.GetKinSheetViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.cache
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject


@HiltViewModel
class BalanceSheetViewModel @Inject constructor(
    balanceController: BalanceController,
    historyController: HistoryController,
    prefsRepository: PrefRepository,
    features: FeatureRepository,
    networkObserver: NetworkConnectivityListener,
) : BaseViewModel2<BalanceSheetViewModel.State, BalanceSheetViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val amountText: String = "",
        val marketValue: Double = 0.0,
        val selectedRate: Rate? = null,
        val isKinSelected: Boolean = false,
        val currencyFlag: Int? = null,
        val chatsLoading: Boolean = false,
        val chats: List<Chat> = emptyList(),
        val isBucketDebuggerEnabled: Boolean = false,
        val isBucketDebuggerVisible: Boolean = false,
        val buyModule: Feature = BuyModuleFeature(),
        val currencySelection: Feature = BalanceCurrencyFeature()
    )

    sealed interface Event {
        data class OnDebugBucketsEnabled(val enabled: Boolean) : Event
        data class OnDebugBucketsVisible(val show: Boolean) : Event
        data class OnBuyModuleStateChanged(val module: Feature) : Event
        data class OnCurrencySelectionStateChanged(val module: Feature): Event
        data class OnLatestRateChanged(val rate: Rate) : Event

        data class OnBalanceChanged(
            val flagResId: Int?,
            val marketValue: Double,
            val display: String,
            val isKin: Boolean,
        ) : Event

        data class OnChatsLoading(val loading: Boolean) : Event
        data class OnChatsUpdated(val chats: List<Chat>) : Event
    }

    init {
        features.buyModule
            .onEach { dispatchEvent(Event.OnBuyModuleStateChanged(it)) }
            .launchIn(viewModelScope)

        features.balanceCurrencySelection
            .onEach { dispatchEvent(Event.OnCurrencySelectionStateChanged(it)) }
            .launchIn(viewModelScope)

        prefsRepository.observeOrDefault(PrefsBool.BUCKET_DEBUGGER_ENABLED, false)
            .distinctUntilChanged()
            .onEach { enabled ->
                dispatchEvent(Dispatchers.Main, Event.OnDebugBucketsEnabled(enabled))
            }.launchIn(viewModelScope)

        balanceController.formattedBalance
            .filterNotNull()
            .distinctUntilChanged()
            .onEach {
                dispatchEvent(
                    Dispatchers.Main,
                    Event.OnBalanceChanged(
                        flagResId = it.currency?.resId,
                        marketValue = it.marketValue,
                        display = it.formattedValue,
                        isKin = it.currency == Currency.Kin
                    )
                )
            }
            .launchIn(viewModelScope)

        historyController.chats
            .onEach {
                if (it == null || (it.isEmpty() && !networkObserver.isConnected)) {
                    dispatchEvent(Dispatchers.Main, Event.OnChatsLoading(true))
                }
            }
            .map { chats ->
                when {
                    chats == null -> null // await for confirmation it's empty
                    chats.isEmpty() && !networkObserver.isConnected -> null // remain loading while disconnected
                    chats.any { it.messages.isEmpty() } -> null // remain loading while fetching messages
                    else -> chats
                }
            }
            .filterNotNull()
            .onEach { update ->
                dispatchEvent(Dispatchers.Main, Event.OnChatsUpdated(update))
            }.onEach {
                dispatchEvent(Dispatchers.Main, Event.OnChatsLoading(false))
            }.launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnDebugBucketsEnabled -> { state ->
                    state.copy(isBucketDebuggerEnabled = event.enabled)
                }

                is Event.OnDebugBucketsVisible -> { state ->
                    state.copy(isBucketDebuggerVisible = event.show)
                }

                is Event.OnBuyModuleStateChanged -> { state ->
                    state.copy(
                        buyModule = event.module
                    )
                }

                is Event.OnCurrencySelectionStateChanged -> { state ->
                    state.copy(
                        currencySelection = event.module
                    )
                }

                is Event.OnLatestRateChanged -> { state ->
                    state.copy(selectedRate = event.rate)
                }

                is Event.OnBalanceChanged -> { state ->
                    state.copy(
                        currencyFlag = event.flagResId,
                        marketValue = event.marketValue,
                        amountText = event.display,
                        isKinSelected = event.isKin
                    )
                }
                is Event.OnChatsLoading -> { state ->
                    state.copy(chatsLoading = event.loading)
                }
                is Event.OnChatsUpdated -> { state ->
                    state.copy(chats = event.chats)
                }
            }
        }
    }
}