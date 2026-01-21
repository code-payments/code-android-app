package com.flipcash.app.deposit.internal

import android.content.ClipData
import android.content.ClipboardManager
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.extensions.setText
import com.flipcash.features.deposit.R
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
internal class DepositViewModel @Inject constructor(
    userManager: UserManager,
    tokenController: TokenController,
    clipboardManager: ClipboardManager,
    resources: ResourceHelper,
) : BaseViewModel2<DepositViewModel.State, DepositViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    internal data class State(
        val selectedTokenAddress: Mint? = null,
        val depositAddress: String = "",
        val isCopied: Boolean = false
    )

    internal sealed interface Event {
        data class OnMintSelected(val mint: Mint) : Event
        data class OnDepositAddressChanged(val address: String) : Event
        data object CopyAddress : Event
        data class SetCopied(val isCopied: Boolean) : Event
        data object Exit: Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.OnMintSelected>()
            .mapNotNull { tokenController.getTokenMetadata(it.mint) }
            .onResult(
                onSuccess = { result ->
                    val address = userManager.accountCluster?.depositAddressFor(result.token)?.base58()
                    if (address == null) {
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_tokenNotFound),
                            message = resources.getString(R.string.error_description_tokenNotFound),
                        ) {
                            dispatchEvent(Event.Exit)
                        }
                        return@onResult
                    }
                    dispatchEvent(Event.OnDepositAddressChanged(address))
                },
                onError = {
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_tokenNotFound),
                        message = resources.getString(R.string.error_description_tokenNotFound),
                    ) {
                        dispatchEvent(Event.Exit)
                    }
                }
            )
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CopyAddress>()
            .onEach {
                clipboardManager.setText(
                    text = stateFlow.value.depositAddress,
                    label = resources.getString(R.string.title_clipboardLabelDepositAddress),
                )
            }
            .onEach {
                dispatchEvent(Event.SetCopied(true))
                delay(2.seconds)
                dispatchEvent(Event.SetCopied(false))
            }.launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnMintSelected -> { state ->
                    state.copy(selectedTokenAddress = event.mint)
                }

                is Event.OnDepositAddressChanged -> { state ->
                    state.copy(depositAddress = event.address)
                }

                is Event.CopyAddress -> { state -> state }
                is Event.SetCopied -> { state -> state.copy(isCopied = event.isCopied) }
                is Event.Exit -> { state -> state }
            }
        }
    }
}