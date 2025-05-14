package com.flipcash.app.myaccount.internal

import android.content.ClipboardManager
import androidx.lifecycle.viewModelScope
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.extensions.setText
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.menu.MenuItem
import com.flipcash.features.myaccount.R
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.solana.keys.base58
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.base58
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

private val FullMenuList = buildList {
    add(AccessKey)
    add(DeleteAccount)
}

@HiltViewModel
internal class MyAccountScreenViewModel @Inject constructor(
    userManager: UserManager,
    featureFlagController: FeatureFlagController,
    resources: ResourceHelper,
    authManager: AuthManager,
    clipboardManager: ClipboardManager,
) : BaseViewModel2<MyAccountScreenViewModel.State, MyAccountScreenViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    internal data class State(
        val isBetaEnabled: Boolean = false,
        val showAccountInfo: Boolean = false,
        val accountId: String? = null,
        val publicKey: String? = null,
        val items: List<MenuItem<Event>> = FullMenuList
    )

    internal sealed interface Event {
        data class OnUserAssociated(val userId: String?, val publicKey: String?) : Event
        data class OnBetaFeaturesUnlocked(val unlocked: Boolean) : Event
        data object OnTitleClicked: Event
        data class ToggleAccountInfo(val show: Boolean) : Event
        data object OnAccessKeyClicked : Event
        data object OnViewAccessKey: Event
        data object OnDeleteAccountClicked : Event
        data object OnAccountDeleted : Event
        data object CopyPublicKey : Event
        data object CopyAccountId : Event
    }

    init {
        userManager.state
            .map { it.accountId to it.cluster }
            .onEach { (id, cluster) ->
                val userId = id?.base58
                val publicKey = cluster?.authorityPublicKey?.base58()

                dispatchEvent(Event.OnUserAssociated(userId, publicKey))
            }.launchIn(viewModelScope)

        combine(
            featureFlagController.observeOverride(),
            userManager.state.map { it.flags?.isStaff == true }
        ) { override, isStaff ->
            override || isStaff
        }.map {
            dispatchEvent(Event.OnBetaFeaturesUnlocked(it))
        }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnTitleClicked>()
            .filter { stateFlow.value.isBetaEnabled }
            .map { stateFlow.value.showAccountInfo }
            .onEach { dispatchEvent(Event.ToggleAccountInfo(!it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnDeleteAccountClicked>()
            .onEach {
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = resources.getString(R.string.prompt_title_deleteAccount),
                        subtitle = resources.getString(R.string.prompt_description_deleteAccount),
                        positiveText = resources.getString(R.string.action_deleteAccount),
                        tertiaryText = resources.getString(R.string.action_cancel),
                        onPositive = {
                            viewModelScope.launch {
                                delay(150) // wait for dismiss
                                authManager.deleteAndLogout()
                                    .onSuccess { dispatchEvent(Event.OnAccountDeleted) }
                                    .onFailure {
                                        TopBarManager.showMessage(
                                            TopBarManager.TopBarMessage(
                                                title = resources.getString(R.string.error_title_failedToDeleteAccount),
                                                message = resources.getString(R.string.error_description_failedToDeleteAccount),
                                            )
                                        )
                                    }
                            }
                        }
                    )
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CopyPublicKey>()
            .mapNotNull { stateFlow.value.publicKey }
            .onEach {
                clipboardManager.setText(
                    text = it,
                    label = resources.getString(R.string.title_clipboardLabelPublicKey)
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CopyAccountId>()
            .mapNotNull { stateFlow.value.accountId }
            .onEach {
                clipboardManager.setText(
                    text = it,
                    label = resources.getString(R.string.title_clipboardLabelAccountId)
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAccessKeyClicked>()
            .onEach {
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = resources.getString(R.string.prompt_title_viewAccessKey),
                        subtitle = resources.getString(R.string.prompt_description_viewAccessKey),
                        positiveText = resources.getString(R.string.action_viewAccessKey),
                        negativeText = resources.getString(R.string.action_cancel),
                        onPositive = { dispatchEvent(Event.OnViewAccessKey) },
                    )
                )
            }.launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnUserAssociated -> { state ->
                    state.copy(
                        accountId = event.userId,
                        publicKey = event.publicKey,
                    )
                }

                Event.OnViewAccessKey,
                Event.CopyPublicKey,
                Event.CopyAccountId,
                Event.OnTitleClicked,
                Event.OnDeleteAccountClicked,
                Event.OnAccountDeleted,
                Event.OnAccessKeyClicked -> { state -> state }

                is Event.OnBetaFeaturesUnlocked -> { state ->
                    state.copy(isBetaEnabled = event.unlocked)
                }

                is Event.ToggleAccountInfo -> { state ->
                    state.copy(showAccountInfo = event.show)
                }
            }
        }
    }
}