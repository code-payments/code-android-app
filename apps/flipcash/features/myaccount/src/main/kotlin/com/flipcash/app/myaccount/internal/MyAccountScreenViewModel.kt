package com.flipcash.app.myaccount.internal

import android.content.ClipboardManager
import androidx.lifecycle.viewModelScope
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.extensions.setText
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.menu.MenuItem
import com.flipcash.features.myaccount.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.solana.keys.base58
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.base64
import com.getcode.view.BaseViewModel
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
    add(VerifyPhone)
    add(VerifyEmail)
    add(LogOut)
    add(DeleteAccount)
}

@HiltViewModel
internal class MyAccountScreenViewModel @Inject constructor(
    userManager: UserManager,
    featureFlagController: FeatureFlagController,
    resources: ResourceHelper,
    authManager: AuthManager,
    clipboardManager: ClipboardManager,
    dispatchers: DispatcherProvider,
) : BaseViewModel<MyAccountScreenViewModel.State, MyAccountScreenViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    internal data class State(
        val isBetaEnabled: Boolean = false,
        val showAccountInfo: Boolean = false,
        val accountId: String? = null,
        val publicKey: String? = null,
        val pushToken: String? = null,
        val linkForPayment: Boolean = false,
        val items: List<MenuItem<Event>> = FullMenuList
    )

    internal sealed interface Event {
        data class OnUserAssociated(
            val userId: String?,
            val publicKey: String?,
            val pushToken: String? = null,
            val linkForPayment: Boolean = false,
        ) : Event

        data class OnBetaFeaturesUnlocked(val unlocked: Boolean) : Event
        data object OnTitleClicked : Event
        data class ToggleAccountInfo(val show: Boolean) : Event
        data object OnAccessKeyClicked : Event
        data object OnViewAccessKey : Event
        data object OnVerifyEmailClicked : Event
        data object OnVerifyPhoneClicked : Event
        data class ConnectPhoneClicked(val linkForPayment: Boolean) : Event
        data object OnDeleteAccountClicked : Event
        data object OnAccountDeleted : Event
        data object CopyPublicKey : Event
        data object CopyAccountId : Event
        data object CopyPushToken : Event
        data object OnLogOutClicked : Event
        data object OnLoggedOutCompletely : Event
    }

    init {
        combine(
            userManager.state,
            featureFlagController.observe(FeatureFlag.PhoneNumberSend),
        ) { state, sendEnabled ->
            val userId = state.accountId?.base64
            val publicKey = state.cluster?.authorityPublicKey?.base58()

            val linkForPayment = sendEnabled ||
                    state.flags?.enablePhoneNumberSend == true

            dispatchEvent(
                Event.OnUserAssociated(
                    userId = userId,
                    publicKey = publicKey,
                    pushToken = state.pushToken,
                    linkForPayment = linkForPayment,
                )
            )

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
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.prompt_title_deleteAccount),
                    message = resources.getString(R.string.prompt_description_deleteAccount),
                    actions = listOf(
                        BottomBarAction(resources.getString(R.string.action_deleteAccount)) {
                            viewModelScope.launch {
                                delay(150) // wait for dismiss
                                authManager.deleteAndLogout()
                                    .onSuccess { dispatchEvent(Event.OnAccountDeleted) }
                                    .onFailure {
                                        BottomBarManager.showError(
                                            title = resources.getString(R.string.error_title_failedToDeleteAccount),
                                            message = resources.getString(R.string.error_description_failedToDeleteAccount),
                                        )
                                    }
                            }
                        }
                    ),
                    showCancel = true,
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
            .filterIsInstance<Event.CopyPushToken>()
            .mapNotNull { stateFlow.value.pushToken }
            .onEach {
                clipboardManager.setText(
                    text = it,
                    label = resources.getString(R.string.title_clipboardLabelPushToken)
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAccessKeyClicked>()
            .onEach {
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.prompt_title_viewAccessKey),
                    message = resources.getString(R.string.prompt_description_viewAccessKey),
                    showScrim = true,
                    showCancel = true,
                    actions = listOf(
                        BottomBarAction(
                            text = resources.getString(R.string.action_viewAccessKey),
                            onClick = { dispatchEvent(Event.OnViewAccessKey) }
                        )
                    ),
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnVerifyPhoneClicked>()
            .onEach {
                dispatchEvent(Event.ConnectPhoneClicked(stateFlow.value.linkForPayment))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnLogOutClicked>()
            .onEach {
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.prompt_title_logout),
                    message = resources.getString(R.string.prompt_description_logout),
                    actions = listOf(
                        BottomBarAction(resources.getString(R.string.action_logout)) {
                            viewModelScope.launch {
                                delay(150) // wait for dismiss
                                authManager.logout()
                                    .onSuccess {
                                        dispatchEvent(Event.OnLoggedOutCompletely)
                                    }
                                    .onFailure {
                                        BottomBarManager.showError(
                                            title = resources.getString(R.string.error_title_failedToLogOut),
                                            message = resources.getString(R.string.error_description_failedToLogOut),
                                        )
                                    }
                            }
                        },
                    ),
                    showCancel = true,
                )
            }.launchIn(viewModelScope)
    }

    internal companion object {
        private fun buildItemList(
            isBetaEnabled: Boolean,
        ): List<MenuItem<Event>> {
            return if (isBetaEnabled) {
                FullMenuList
            } else {
                FullMenuList.filterNot { item -> item is VerifyEmail || item is VerifyPhone }
            }
        }

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnUserAssociated -> { state ->
                    state.copy(
                        accountId = event.userId,
                        publicKey = event.publicKey,
                        pushToken = event.pushToken,
                        linkForPayment = event.linkForPayment,
                    )
                }

                Event.OnLogOutClicked,
                Event.OnLoggedOutCompletely,
                Event.OnVerifyPhoneClicked,
                is Event.ConnectPhoneClicked,
                Event.OnVerifyEmailClicked,
                Event.OnViewAccessKey,
                Event.CopyPublicKey,
                Event.CopyAccountId,
                Event.CopyPushToken,
                Event.OnTitleClicked,
                Event.OnDeleteAccountClicked,
                Event.OnAccountDeleted,
                Event.OnAccessKeyClicked -> { state -> state }

                is Event.OnBetaFeaturesUnlocked -> { state ->
                    state.copy(
                        isBetaEnabled = event.unlocked,
                        items = buildItemList(event.unlocked)
                    )
                }

                is Event.ToggleAccountInfo -> { state ->
                    state.copy(showAccountInfo = event.show)
                }
            }
        }
    }
}