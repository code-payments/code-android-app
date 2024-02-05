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
    private val prefsRepository: PrefRepository,
    private val balanceController: BalanceController,
    private val client: Client,
    private val networkObserver: NetworkConnectivityListener,
    private val resources: ResourceHelper,
    private val historyController: HistoryController,
) : BaseViewModel2<GetKinSheetViewModel.State, GetKinSheetViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val isEligibleGetFirstKinAirdrop: Boolean = false,
        val isEligibleGiveFirstKinAirdrop: Boolean = false,
        val isGetFirstKinAirdropLoading: Boolean = false
    )

    sealed interface Event {
        data class OnGetEligibilityChanged(val eligible: Boolean, val fromEvent: Boolean = false) : Event
        data class OnGiveEligibilityChanged(val eligible: Boolean) : Event
        data class OnLoadingChanged(val loading: Boolean) : Event

        /**
         * User initiates a request for the FirstKin
         */
        data object RequestedFirstKin : Event

        /**
         * Kin Airdrop ready to present to the user to be grabbed
         */
        data class OnKinReadyToGrab(val amount: KinAmount) : Event

        /**
         * Kin Airdrop received from server
         */
        data class OnKinRequestSuccessful(val amount: KinAmount) : Event
    }

    init {
        prefsRepository.observeOrDefault(PrefsBool.IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP, false)
            .map { it }
            .onEach {
                dispatchEvent(Event.OnGetEligibilityChanged(it))
            }.launchIn(viewModelScope)

        prefsRepository.observeOrDefault(PrefsBool.IS_ELIGIBLE_GIVE_FIRST_KIN_AIRDROP, false)
            .map { it }
            .onEach {
                dispatchEvent(Event.OnGiveEligibilityChanged(it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.RequestedFirstKin>()
            .mapNotNull {
                if (!networkObserver.isConnected) {
                    ErrorUtils.showNetworkError(resources)
                    return@mapNotNull null
                }
                SessionManager.getKeyPair()
            }.onEach { dispatchEvent(Event.OnLoadingChanged(true)) }
            .catchSafely(
                action = { owner ->
                    delay(1.seconds)
                    val amount = client.requestFirstKinAirdrop(owner).getOrThrow()

                    dispatchEvent(Event.OnGetEligibilityChanged(eligible = false, fromEvent = true))
                    dispatchEvent(Event.OnLoadingChanged(false))
                    dispatchEvent(Event.OnKinRequestSuccessful(amount))

                    balanceController.fetchBalanceSuspend()

                    historyController.fetchChats()
                },
                onFailure = {
                    if (it is TransactionRepository.AirdropException.AlreadyClaimedException) {
                        dispatchEvent(Event.OnGetEligibilityChanged(eligible = false, fromEvent = true))
                    } else {
                        TopBarManager.showMessage(
                            resources.getString(R.string.title_failed),
                            resources.getString(R.string.error_description_failedToVerifyPhone),
                        )
                    }
                    ErrorUtils.handleError(it)
                }
            )
            .flatMapLatest {
                val organizer = SessionManager.getOrganizer()
                val receiveWithinLimits = organizer?.let {
                    client.receiveFromPrimaryIfWithinLimits(it)
                } ?: Completable.complete()

                receiveWithinLimits.toFlowable<Any>().asFlow()
            }.onEach { historyController.fetchChats() }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnGetEligibilityChanged>()
            .filter { it.fromEvent }
            .map { it.eligible }
            .onEach { prefsRepository.set(PrefsBool.IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP, it) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnKinRequestSuccessful>()
            .onEach { delay(300.milliseconds) }
            .map { it.amount }
            .onEach {
                dispatchEvent(Event.OnKinReadyToGrab(it))
            }.launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnGetEligibilityChanged -> { state ->
                    state.copy(isEligibleGetFirstKinAirdrop = event.eligible)
                }

                is Event.OnGiveEligibilityChanged -> { state ->
                    state.copy(isEligibleGiveFirstKinAirdrop = event.eligible)
                }

                is Event.OnLoadingChanged -> { state ->
                    state.copy(isGetFirstKinAirdropLoading = event.loading)
                }

                Event.RequestedFirstKin,
                is Event.OnKinReadyToGrab,
                is Event.OnKinRequestSuccessful -> { state -> state }
            }
        }
    }
}
