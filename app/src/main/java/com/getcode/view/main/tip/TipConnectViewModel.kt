package com.getcode.view.main.tip

import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.analytics.Action
import com.getcode.analytics.AnalyticsService
import com.getcode.network.TipController
import com.getcode.util.IntentUtils
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class TipConnectViewModel @Inject constructor(
    resources: ResourceHelper,
    tipController: TipController,
    analytics: AnalyticsService,
) : BaseViewModel2<TipConnectViewModel.State, TipConnectViewModel.Event>(
    initialState = State(null, ""),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val reason: IdentityConnectionReason?,
        val xMessage: String,
    )

    sealed interface Event {
        data class OnReasonChanged(val reason: IdentityConnectionReason) : Event
        data class UpdateMessage(val message: String) : Event
        data object PostToX : Event
        data class OpenX(val intent: Intent) : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.OnReasonChanged>()
            .map { it.reason }
            .mapNotNull {
                val verificationMessage = tipController.generateTipVerification() ?: return@mapNotNull null
                when (it) {
                    IdentityConnectionReason.TipCard -> {
                        """
                            ${resources.getString(R.string.subtitle_linkingTwitter)}
                            
                            $verificationMessage
                        """.trimIndent()
                    }

                    IdentityConnectionReason.IdentityReveal -> {
                        """
                            ${resources.getString(R.string.subtitle_linkingTwitterToRevealIdentity)}
                            
                            $verificationMessage
                        """.trimIndent()
                    }
                }
            }.onEach {
                dispatchEvent(Event.UpdateMessage(it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PostToX>()
            .map { stateFlow.value.xMessage }
            .map { IntentUtils.tweet(it) }
            .onEach {
                analytics.action(Action.MessageCodeOnX)
                dispatchEvent(Event.OpenX(it))
                tipController.startVerification()
            }.launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnReasonChanged -> { state -> state.copy(reason = event.reason) }
                is Event.UpdateMessage -> { state -> state.copy(xMessage = event.message) }
                is Event.OpenX -> { state -> state }
                Event.PostToX -> { state -> state }
            }
        }
    }
}