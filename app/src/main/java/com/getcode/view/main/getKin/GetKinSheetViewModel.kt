package com.getcode.view.main.getKin

import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.manager.SessionManager
import com.getcode.manager.TopBarManager
import com.getcode.model.KinAmount
import com.getcode.model.PrefsBool
import com.getcode.network.BalanceController
import com.getcode.network.client.Client
import com.getcode.network.client.fetchPaymentHistoryDelta
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
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.reactive.asFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class GetKinSheetViewModel @Inject constructor(
    private val prefsRepository: PrefRepository,
    private val balanceController: BalanceController,
    private val client: Client,
    private val networkObserver: NetworkConnectivityListener,
    private val resources: ResourceHelper,
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
        data class OnGetEligibilityChanged(val eligible: Boolean) : Event
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
            .flatMapLatest { owner ->
                client.requestFirstKinAirdrop(owner)
                    .subscribeOn(Schedulers.computation())
                    .delay(1, TimeUnit.SECONDS)
                    .toFlowable().asFlow()
            }
            .catchSafely(
                action = { amount ->
                    dispatchEvent(Event.OnKinRequestSuccessful(amount))
                    dispatchEvent(Event.OnGetEligibilityChanged(false))
                    dispatchEvent(Event.OnLoadingChanged(false))
                },
                onFailure = {
                    if (it is TransactionRepository.AirdropException.AlreadyClaimedException) {
                        prefsRepository.set(PrefsBool.IS_ELIGIBLE_GET_FIRST_KIN_AIRDROP, false)
                        dispatchEvent(Event.OnGetEligibilityChanged(false))
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
                Completable.concatArray(
                    balanceController.fetchBalance(),
                    client.fetchPaymentHistoryDelta(owner = SessionManager.getKeyPair()!!)
                        .ignoreElement()
                ).toFlowable<Any>().asFlow()
            }
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
