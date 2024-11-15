package xyz.flipchat.features.balance

import androidx.lifecycle.viewModelScope
import com.getcode.model.Currency
import com.getcode.model.Rate
import com.getcode.network.BalanceController
import com.getcode.utils.Kin
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class BalanceSheetViewModel @Inject constructor(
    balanceController: BalanceController,
    networkObserver: com.getcode.utils.network.NetworkConnectivityListener,
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
        val isBucketDebuggerEnabled: Boolean = false,
        val isBucketDebuggerVisible: Boolean = false,
    )

    sealed interface Event {
        data class OnDebugBucketsEnabled(val enabled: Boolean) : Event
        data class OnDebugBucketsVisible(val show: Boolean) : Event
        data class OnLatestRateChanged(val rate: Rate) : Event

        data class OnBalanceChanged(
            val flagResId: Int?,
            val marketValue: Double,
            val display: String,
            val isKin: Boolean,
        ) : Event

        data class OnChatsLoading(val loading: Boolean) : Event
//        data class OnChatsUpdated(val chats: List<Chat>) : Event
        data object OnOpened: Event
    }

    init {
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
//                is Event.OnChatsUpdated -> { state ->
//                    state.copy(chats = event.chats)
//                }

                Event.OnOpened -> { state -> state }
            }
        }
    }
}