package com.flipcash.app.deposit.internal

import android.content.ClipboardManager
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.extensions.setText
import com.flipcash.features.deposit.R
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.providers.TokenMetadataProvider
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.util.resources.ResourceHelper
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.opencode.internal.solana.extensions.timelockSwapAccounts
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
internal class DepositViewModel @Inject constructor(
    userManager: UserManager,
    tokenController: TokenMetadataProvider,
    clipboardManager: ClipboardManager,
    resources: ResourceHelper,
    dispatchers: DispatcherProvider,
    featureFlags: FeatureFlagController,
) : BaseViewModel2<DepositViewModel.State, DepositViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    internal data class State(
        val selectedTokenAddress: Mint? = null,
        val tokenName: String? = null,
        val depositAddress: String = "",
        val isCopied: Boolean = false
    )

    internal sealed interface Event {
        data class OnMintSelected(val mint: Mint) : Event
        data class OnTokenChanged(val address: String, val name: String) : Event
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
                    viewModelScope.launch {
                        val directDeposit = featureFlags.get(FeatureFlag.DepositUsdc)
                        val address = if (result.token.address == Mint.usdf && directDeposit) {
                            val usdfSwapAccounts = userManager.accountCluster?.let {
                                Token.usdf.timelockSwapAccounts(it.authorityPublicKey)
                            }
                            usdfSwapAccounts?.ata?.publicKey?.base58()
                        } else {
                            userManager.accountCluster?.depositAddressFor(result.token)?.base58()
                        }

                        if (address == null) {
                            BottomBarManager.showError(
                                title = resources.getString(R.string.error_title_tokenNotFound),
                                message = resources.getString(R.string.error_description_tokenNotFound),
                            ) {
                                dispatchEvent(Event.Exit)
                            }
                            return@launch
                        }
                        val tokenName = when {
                            directDeposit && result.token.address == Mint.usdf -> {
                                resources.getString(R.string.displayName_usdc)
                            }
                            result.token.address == Mint.usdf -> {
                                resources.getString(R.string.displayName_usdf)
                            }
                            else -> result.token.name
                        }
                        dispatchEvent(Event.OnTokenChanged(address, tokenName))
                    }
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

                is Event.OnTokenChanged -> { state ->
                    state.copy(depositAddress = event.address, tokenName = event.name)
                }

                is Event.CopyAddress -> { state -> state }
                is Event.SetCopied -> { state -> state.copy(isCopied = event.isCopied) }
                is Event.Exit -> { state -> state }
            }
        }
    }
}