package com.getcode.view.main.account

import androidx.lifecycle.viewModelScope
import com.getcode.model.PrefsBool
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.network.repository.BetaOptions
import com.getcode.network.repository.PrefRepository
import com.getcode.utils.ErrorUtils
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class BetaFlagsViewModel @Inject constructor(
    betaFlags: BetaFlagsRepository,
    prefRepository: PrefRepository,
) : BaseViewModel2<BetaFlagsViewModel.State, BetaFlagsViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val showNetworkDropOff: Boolean = false,
        val canViewBuckets: Boolean = false,
        val isVibrateOnScan: Boolean = false,
        val debugScanTimesEnabled: Boolean = false,
        val displayErrors: Boolean = false,
        val giveRequestsEnabled: Boolean = false,
        val buyKinEnabled: Boolean = false,
        val establishCodeRelationship: Boolean = false,
        val chatUnsubEnabled: Boolean = false,
        val tipsEnabled: Boolean = false,
        val chatMessageV2Enabled: Boolean = false,
        val tipsChatEnabled: Boolean = false,
    )

    sealed interface Event {
        data class UpdateSettings(val settings: BetaOptions) : Event

        data class ShowErrors(val display: Boolean) : Event
        data class ShowNetworkDropOff(val show: Boolean) : Event
        data class SetLogScanTimes(val log: Boolean) : Event
        data class SetVibrateOnScan(val vibrate: Boolean) : Event
        data class UseDebugBuckets(val enabled: Boolean) : Event
        data class EnableGiveRequests(val enabled: Boolean) : Event
        data class EnableBuyKin(val enabled: Boolean) : Event
        data class EnableTipCard(val enabled: Boolean) : Event
        data class EnableCodeRelationshipEstablish(val enabled: Boolean) : Event
        data class EnableChatUnsubscribe(val enabled: Boolean) : Event
        data class EnableChatMessageV2Ui(val enabled: Boolean) : Event
        data class EnableTipChats(val enabled: Boolean) : Event
    }

    init {
        betaFlags.observe()
            .distinctUntilChanged()
            .onEach { settings ->
                dispatchEvent(Event.UpdateSettings(settings))
            }.launchIn(viewModelScope)


        eventFlow
            .onEach { event ->
                 when (event) {
                     is Event.EnableBuyKin -> prefRepository.set(PrefsBool.BUY_MODULE_ENABLED, event.enabled)
                     is Event.EnableChatMessageV2Ui -> prefRepository.set(PrefsBool.MESSAGE_PAYMENT_NODE_V2, event.enabled)
                     is Event.EnableChatUnsubscribe -> prefRepository.set(PrefsBool.CHAT_UNSUB_ENABLED, event.enabled)
                     is Event.EnableCodeRelationshipEstablish -> prefRepository.set(PrefsBool.ESTABLISH_CODE_RELATIONSHIP, event.enabled)
                     is Event.EnableGiveRequests -> prefRepository.set(PrefsBool.GIVE_REQUESTS_ENABLED, event.enabled)
                     is Event.EnableTipCard -> prefRepository.set(PrefsBool.TIPS_ENABLED, event.enabled)
                     is Event.EnableTipChats -> prefRepository.set(PrefsBool.TIPS_CHAT_ENABLED, event.enabled)
                     is Event.SetLogScanTimes -> prefRepository.set(PrefsBool.LOG_SCAN_TIMES, event.log)
                     is Event.SetVibrateOnScan -> prefRepository.set(PrefsBool.VIBRATE_ON_SCAN, event.vibrate)
                     is Event.ShowErrors -> {
                         prefRepository.set(PrefsBool.DISPLAY_ERRORS, event.display)
                         ErrorUtils.setDisplayErrors(event.display)
                     }
                     is Event.ShowNetworkDropOff ->  prefRepository.set(PrefsBool.SHOW_CONNECTIVITY_STATUS, event.show)
                     is Event.UseDebugBuckets -> prefRepository.set(PrefsBool.BUCKET_DEBUGGER_ENABLED, event.enabled)

                     is Event.UpdateSettings -> Unit
                 }
            }.launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.UpdateSettings -> { state ->
                    with(event.settings) {
                        state.copy(
                            showNetworkDropOff = showNetworkDropOff,
                            canViewBuckets = canViewBuckets,
                            isVibrateOnScan = tickOnScan,
                            debugScanTimesEnabled = debugScanTimesEnabled,
                            displayErrors = displayErrors,
                            giveRequestsEnabled = giveRequestsEnabled,
                            buyKinEnabled = buyModuleEnabled,
                            establishCodeRelationship = establishCodeRelationship,
                            chatUnsubEnabled = chatUnsubEnabled,
                            tipsEnabled = tipsEnabled,
                            chatMessageV2Enabled = chatMessageV2Enabled,
                            tipsChatEnabled = tipsChatEnabled,
                        )
                    }
                }

                is Event.EnableBuyKin,
                is Event.EnableTipCard,
                is Event.EnableGiveRequests,
                is Event.ShowNetworkDropOff,
                is Event.UseDebugBuckets,
                is Event.SetLogScanTimes,
                is Event.SetVibrateOnScan,
                is Event.EnableCodeRelationshipEstablish,
                is Event.EnableChatUnsubscribe,
                is Event.EnableChatMessageV2Ui,
                is Event.EnableTipChats,
                is Event.ShowErrors -> { state -> state }
            }
        }
    }
}