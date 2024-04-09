package com.getcode.view.main.tip

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.ed25519.Ed25519
import com.getcode.manager.SessionManager
import com.getcode.network.TipController
import com.getcode.network.repository.urlEncode
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.bytes
import com.getcode.utils.nonce
import com.getcode.vendor.Base58
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TipConnectViewModel @Inject constructor(
    tipController: TipController,
    resources: ResourceHelper,
) : BaseViewModel2<TipConnectViewModel.State, TipConnectViewModel.Event>(
    initialState = State("", "", false),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val username: String,
        val xMessage: String,
        val connected: Boolean
    )

    sealed interface Event {
        data class UpdateUsername(val username: String): Event
        data class UpdateMessage(val message: String): Event
        data object PostToX: Event
        data class OpenX(val intent: Intent): Event
        data class SetConnected(val connected: Boolean): Event
    }

    init {

        tipController.connectedAccount
            .onEach {
                dispatchEvent(Event.UpdateUsername(it.orEmpty()))
                dispatchEvent(Event.SetConnected(it != null))
            }.launchIn(viewModelScope)

        val authority = SessionManager.getOrganizer()?.tray?.owner?.getCluster()?.authority
        val tipAddress = SessionManager.getOrganizer()?.primaryVault
            ?.let { Base58.encode(it.byteArray) }

        if (tipAddress != null && authority != null) {
            val nonce = UUID.randomUUID()
            val signature = Base58.encode(Ed25519.sign(nonce.bytes.toByteArray(), authority.keyPair))
            val encodedNonce = Base58.encode(nonce.bytes.toByteArray())
            val verificationMessage = "$tipAddress:$encodedNonce:$signature"

            val message = """
                ${resources.getString(R.string.action_connect_to_x_message)}
                
                CodeAccount:$verificationMessage
            """.trimIndent()
            dispatchEvent(Event.UpdateMessage(message))
        }

        eventFlow
            .filterIsInstance<Event.PostToX>()
            .map {
                // build intent
                val url = "https://www.twitter.com/intent/tweet?text=${stateFlow.value.xMessage.urlEncode()}"
                Intent(Intent.ACTION_VIEW).apply { setData(Uri.parse(url)) }
            }.onEach {
                dispatchEvent(Event.OpenX(it))
            }.launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.UpdateUsername -> { state -> state.copy(username = event.username) }
                is Event.UpdateMessage -> { state -> state.copy(xMessage = event.message) }
                is Event.SetConnected -> { state -> state.copy(connected = event.connected) }
                is Event.OpenX -> { state -> state }
                Event.PostToX -> { state -> state }
            }
        }
    }
}