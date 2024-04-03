package com.getcode.view.main.getKin

import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.manager.SessionManager
import com.getcode.manager.TopBarManager
import com.getcode.model.KinAmount
import com.getcode.model.PrefsBool
import com.getcode.network.BalanceController
import com.getcode.network.HistoryController
import com.getcode.network.client.Client
import com.getcode.network.client.receiveFromPrimaryIfWithinLimits
import com.getcode.network.client.requestFirstKinAirdrop
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.network.repository.BetaOptions
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.showNetworkError
import com.getcode.utils.ErrorUtils
import com.getcode.utils.catchSafely
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.core.Completable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.reactive.asFlow
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class GetKinSheetViewModel @Inject constructor(
    betaFlags: BetaFlagsRepository,
) : BaseViewModel2<GetKinSheetViewModel.State, GetKinSheetViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val isTipsEnabled: Boolean = false,
        val isRequestKinEnabled: Boolean = false,
    )

    sealed interface Event {
        data class OnBetaFlagsChanged(val options: BetaOptions) : Event
    }

    init {

        betaFlags.observe()
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnBetaFlagsChanged(it)) }
            .launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnBetaFlagsChanged -> { state ->
                    state.copy(
                        isTipsEnabled = event.options.tipsEnabled,
                        isRequestKinEnabled = event.options.giveRequestsEnabled,
                    )
                }
            }
        }
    }
}
