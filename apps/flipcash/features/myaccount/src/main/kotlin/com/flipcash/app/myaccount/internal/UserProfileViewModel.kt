package com.flipcash.app.myaccount.internal

import android.content.ClipboardManager
import androidx.lifecycle.viewModelScope
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.extensions.setText
import com.flipcash.core.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ContactVerificationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.SocialAccount
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.uuid
import com.getcode.solana.keys.base58
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.base64
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class UserProfileViewModel @Inject constructor(
    private val userManager: UserManager,
    private val contactController: ContactVerificationController,
    private val contactCoordinator: ContactCoordinator,
    private val profileController: ProfileController,
    private val resources: ResourceHelper,
    clipboardManager: ClipboardManager,
    dispatchers: DispatcherProvider,
) : BaseViewModel<UserProfileViewModel.State, UserProfileViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    internal data class State(
        val displayName: String? = null,
        val phoneNumber: String? = null,
        val emailAddress: String? = null,
        val phoneLinkedForPayment: Boolean = false,
        val socialAccounts: List<SocialAccount> = emptyList(),
        val publicKey: String? = null,
        val accountId: String? = null,
        val pushToken: String? = null,
    )

    internal sealed interface Event {
        data class OnProfileUpdated(
            val displayName: String?,
            val phone: String?,
            val email: String?,
            val linkedForPayment: Boolean,
            val socialAccounts: List<SocialAccount>,
        ) : Event

        data object UnlinkPhoneClicked : Event
        data object UnlinkEmailClicked : Event
        data object ConnectPhoneClicked : Event
        data object ConnectEmailClicked : Event
        data object ReplacePhoneClicked : Event
        data object ReplaceEmailClicked : Event
        data class UnlinkSocialAccountClicked(val account: SocialAccount.TwitterX) : Event
        data object NavigateToPhoneVerification : Event
        data object NavigateToEmailVerification : Event

        data class OnAccountInfoUpdated(
            val publicKey: String?,
            val accountId: String?,
            val pushToken: String?,
        ) : Event

        data object CopyPublicKey : Event
        data object CopyAccountId : Event
        data object CopyPushToken : Event
    }

    init {
        combine(
            userManager.state,
            contactCoordinator.isLinkedForPayment,
        ) { state, linkedForPayment ->
            val profile = state.userProfile

            dispatchEvent(
                Event.OnProfileUpdated(
                    displayName = profile?.displayName,
                    phone = profile?.verifiedPhoneNumber,
                    email = profile?.verifiedEmailAddress,
                    linkedForPayment = linkedForPayment,
                    socialAccounts = profile?.socialAccounts.orEmpty(),
                )
            )
        }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.UnlinkPhoneClicked>()
            .onEach {
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.prompt_title_unlinkPhone),
                    message = resources.getString(R.string.prompt_description_unlinkPhone),
                    actions = listOf(
                        BottomBarAction(resources.getString(R.string.action_unlinkPhone)) {
                            viewModelScope.launch {
                                delay(150)
                                unlinkPhone()
                            }
                        }
                    ),
                    showCancel = true,
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.UnlinkEmailClicked>()
            .onEach {
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.prompt_title_unlinkEmail),
                    message = resources.getString(R.string.prompt_description_unlinkEmail),
                    actions = listOf(
                        BottomBarAction(resources.getString(R.string.action_unlinkEmail)) {
                            viewModelScope.launch {
                                delay(150)
                                unlinkEmail()
                            }
                        }
                    ),
                    showCancel = true,
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.UnlinkSocialAccountClicked>()
            .onEach { event ->
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.prompt_title_unlinkSocialAccount),
                    message = resources.getString(R.string.prompt_description_unlinkSocialAccount),
                    actions = listOf(
                        BottomBarAction(resources.getString(R.string.action_unlinkAccount)) {
                            viewModelScope.launch {
                                delay(150)
                                unlinkSocialAccount(event.account)
                            }
                        }
                    ),
                    showCancel = true,
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ConnectPhoneClicked>()
            .onEach { dispatchEvent(Event.NavigateToPhoneVerification) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ReplacePhoneClicked>()
            .onEach { dispatchEvent(Event.NavigateToPhoneVerification) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ConnectEmailClicked>()
            .onEach { dispatchEvent(Event.NavigateToEmailVerification) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ReplaceEmailClicked>()
            .onEach { dispatchEvent(Event.NavigateToEmailVerification) }
            .launchIn(viewModelScope)

        userManager.state
            .onEach { state ->
                dispatchEvent(
                    Event.OnAccountInfoUpdated(
                        publicKey = state.cluster?.authorityPublicKey?.base58(),
                        accountId = state.accountId?.uuid?.toString()?.uppercase(),
                        pushToken = state.pushToken,
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
            .filterIsInstance<Event.CopyPushToken>()
            .mapNotNull { stateFlow.value.pushToken }
            .onEach {
                clipboardManager.setText(
                    text = it,
                    label = resources.getString(R.string.title_clipboardLabelPushToken)
                )
            }.launchIn(viewModelScope)
    }

    private suspend fun unlinkPhone() {
        val phone = userManager.profile?.verifiedPhoneNumber ?: return
        contactController.unlink(ContactMethod.Phone(phone))
            .onFailure {
                BottomBarManager.showError(
                    title = resources.getString(R.string.error_title_failedToUnlinkPhone),
                    message = resources.getString(R.string.error_description_failedToUnlinkPhone),
                )
            }.onSuccess {
                BottomBarManager.showSuccess(
                    title = resources.getString(R.string.prompt_title_phoneUnlinked),
                    message = resources.getString(R.string.prompt_description_phoneUnlinked),
                )
            }
    }

    private suspend fun unlinkEmail() {
        val email = userManager.profile?.verifiedEmailAddress ?: return
        contactController.unlink(ContactMethod.Email(email))
            .onFailure {
                BottomBarManager.showError(
                    title = resources.getString(R.string.error_title_failedToUnlinkEmail),
                    message = resources.getString(R.string.error_description_failedToUnlinkEmail),
                )
            }.onSuccess {
                BottomBarManager.showSuccess(
                    title = resources.getString(R.string.prompt_title_emailUnlinked),
                    message = resources.getString(R.string.prompt_description_emailUnlinked),
                )
            }
    }

    private suspend fun unlinkSocialAccount(account: SocialAccount.TwitterX) {
        profileController.unlinkTwitterXAccount(account)
            .onSuccess { profileController.updateUserProfile() }
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnProfileUpdated -> { state ->
                    state.copy(
                        displayName = event.displayName,
                        phoneNumber = event.phone,
                        emailAddress = event.email,
                        phoneLinkedForPayment = event.linkedForPayment,
                        socialAccounts = event.socialAccounts,
                    )
                }

                is Event.OnAccountInfoUpdated -> { state ->
                    state.copy(
                        publicKey = event.publicKey,
                        accountId = event.accountId,
                        pushToken = event.pushToken,
                    )
                }

                Event.UnlinkPhoneClicked,
                Event.UnlinkEmailClicked,
                Event.ConnectPhoneClicked,
                Event.ConnectEmailClicked,
                Event.ReplacePhoneClicked,
                Event.ReplaceEmailClicked,
                is Event.UnlinkSocialAccountClicked,
                Event.NavigateToPhoneVerification,
                Event.NavigateToEmailVerification,
                Event.CopyPublicKey,
                Event.CopyAccountId,
                Event.CopyPushToken -> { state -> state }
            }
        }
    }
}
