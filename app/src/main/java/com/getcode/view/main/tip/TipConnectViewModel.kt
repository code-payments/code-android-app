package com.getcode.view.main.tip

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.network.TipController
import com.getcode.network.repository.urlEncode
import com.getcode.util.IntentUtils
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class TipConnectViewModel @Inject constructor(
    resources: ResourceHelper,
    tipController: TipController,
) : BaseViewModel2<TipConnectViewModel.State, TipConnectViewModel.Event>(
    initialState = State(""),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val xMessage: String,
    )

    sealed interface Event {
        data class UpdateMessage(val message: String): Event
        data object PostToX: Event
        data class OpenX(val intent: Intent): Event
    }

    init {
        val verificationMessage = tipController.generateTipVerification()
        if (verificationMessage != null) {
            val message = """
                ${resources.getString(R.string.subtitle_linkingTwitter)}
                
                $verificationMessage
            """.trimIndent()
            dispatchEvent(Event.UpdateMessage(message))
        }

        eventFlow
            .filterIsInstance<Event.PostToX>()
            .map { stateFlow.value.xMessage }
            .map { IntentUtils.tweet(it) }
            .onEach {
                dispatchEvent(Event.OpenX(it))
            }.launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.UpdateMessage -> { state -> state.copy(xMessage = event.message) }
                is Event.OpenX -> { state -> state }
                Event.PostToX -> { state -> state }
            }
        }
    }
}