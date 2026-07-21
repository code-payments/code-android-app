package com.flipcash.app.directsend.internal

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.contacts.device.PickedContactData
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.core.send.SendStep
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.permissions.PickedContact
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.directsend.R
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
internal class SendFlowViewModel @Inject constructor(
    private val userManager: UserManager,
    featureFlags: FeatureFlagController,
    private val contactCoordinator: ContactCoordinator,
    chatCoordinator: ChatCoordinator,
    tokenCoordinator: TokenCoordinator,
    private val contactListBuilder: ContactListBuilder,
    private val resources: ResourceHelper,
) : BaseViewModel<SendFlowViewModel.State, SendFlowViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {

    data class State @OptIn(ExperimentalMaterial3Api::class) constructor(
        val steps: List<SendStep> = listOf(SendStep.ContactList),
        val currentStep: SendStep? = null,
        val searchState: TextFieldState = TextFieldState(),
        val isPickerMode: Boolean = false,
        val contactSyncState: LoadingSuccessState = LoadingSuccessState(),
        val listItems: List<ContactListItem> = emptyList(),
        val hasCalloutBeenDismissed: Boolean = true,
    )

    sealed interface Event {
        data class StepsUpdated(val steps: List<SendStep>, val isPickerMode: Boolean) : Event
        data class OnStepChanged(val step: SendStep) : Event

        data object ContactsGranted : Event
        data class ContactsPicked(val contacts: List<PickedContact>) : Event
        data class OnItemsPopulated(val items: List<ContactListItem>) : Event
        data class ContactSyncStateUpdated(
            val loading: Boolean = false,
            val success: Boolean = false,
            val error: Boolean = false,
        ) : Event

        data object ContactSyncComplete : Event
        data class OnContactClicked(val contact: ContactListItem.ContactRow) : Event
        data class ContactRemoved(val e164: String) : Event
        data class SendInvite(val contact: DeviceContact) : Event

        data class NavigateToChat(val identifier: ChatIdentifier) : Event
        data object ContactsRevoked : Event

        /** User tapped dismiss on the permission callout. */
        data object DismissedPermissionCallout : Event

        /** Observed dismissal state from [ContactCoordinator.isPermissionCalloutDismissed]. */
        data class CalloutDismissalChanged(val dismissed: Boolean) : Event
    }

    init {
        combine(
            userManager.state,
            featureFlags.observe(FeatureFlag.PhoneNumberSend),
            featureFlags.observe(FeatureFlag.ContactPickerMode),
            contactCoordinator.state,
            chatCoordinator.feed(ChatType.CONTACT_DM),
        ) { userState, phoneNumberSendFlag, contactPickerMode, contactState, chats ->
            val hasLinkedPhone = userState.userProfile?.verifiedPhoneNumber != null
            val phoneNumberSendEnabled = phoneNumberSendFlag ||
                    userState.flags?.enablePhoneNumberSend == true
            val hasContacts = contactState.contacts.isNotEmpty()
            val needsContacts =
                phoneNumberSendEnabled && !hasContacts && !contactState.hasEverSynced

            val steps = buildList {
                if (!hasLinkedPhone) add(SendStep.PhoneGate)
                if (needsContacts && chats.isEmpty()) add(SendStep.ContactsGate)
                add(SendStep.ContactList)
            }
            Event.StepsUpdated(steps = steps, isPickerMode = contactPickerMode)
        }.onEach { event ->
            dispatchEvent(event)
        }.launchIn(viewModelScope)

        // Base list — expensive to build (filter + sort), so it only recomputes
        // when contacts, search, the chat feed, or tokens change.
        val baseItems = combine(
            contactCoordinator.state,
            stateFlow
                .map { it.searchState }
                .distinctUntilChanged()
                .flatMapLatest { snapshotFlow { it.text } },
            chatCoordinator.feed(ChatType.CONTACT_DM),
            tokenCoordinator.tokens,
        ) { contactState, searchText, chatFeed, tokens ->
            contactListBuilder.build(
                contactState = contactState,
                searchString = searchText.toString(),
                chatFeed = chatFeed,
                tokensByMint = tokens.associateBy { it.address },
            )
        }

        // Typing is ephemeral and flips on/off frequently — overlay it on top of
        // the base list with a cheap map rather than rebuilding + re-sorting the
        // whole list on every typing tick. distinctUntilChanged means we only
        // recompute when the set of typing chats changes.
        val typingChatIds = chatCoordinator.state
            .map { chatState ->
                val selfId = userManager.accountId
                chatState.typingIndicators
                    .filterValues { typists -> typists.any { it.userId != selfId } }
                    .keys
            }
            .distinctUntilChanged()

        combine(baseItems, typingChatIds) { items, typing ->
            if (typing.isEmpty()) {
                items
            } else {
                items.map { item ->
                    val conversation = (item as? ContactListItem.ContactRow)?.conversation
                    if (conversation != null && conversation.chatId in typing) {
                        item.copy(conversation = conversation.copy(isTyping = true))
                    } else {
                        item
                    }
                }
            }
        }.onEach { items ->
            dispatchEvent(Event.OnItemsPopulated(items))
        }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ContactsGranted>()
            .onEach {
                dispatchEvent(Event.ContactSyncStateUpdated(loading = true))
                contactCoordinator.sync()
                    .onSuccess {
                        dispatchEvent(Event.ContactSyncStateUpdated(success = true))
                        delay(1.seconds)
                        dispatchEvent(Event.ContactSyncComplete)
                    }
                    .onFailure {
                        dispatchEvent(Event.ContactSyncStateUpdated(error = true))
                        delay(1.seconds)
                    }
                dispatchEvent(Event.ContactSyncStateUpdated())
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ContactsPicked>()
            .map {
                it.contacts.map { contact ->
                    PickedContactData(
                        phoneNumber = contact.phoneNumber,
                        displayName = contact.displayName,
                        photoUri = contact.photoUri,
                    )
                }
            }
            .onEach { contacts ->
                dispatchEvent(Event.ContactSyncStateUpdated(loading = true))
                contactCoordinator.addPickedContacts(contacts)
                    .onSuccess {
                        dispatchEvent(Event.ContactSyncStateUpdated(success = true))
                        delay(1.seconds)
                        dispatchEvent(Event.ContactSyncComplete)
                    }
                    .onFailure {
                        dispatchEvent(Event.ContactSyncStateUpdated(error = true))
                        delay(1.seconds)
                    }
                dispatchEvent(Event.ContactSyncStateUpdated())
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnContactClicked>()
            .map { it.contact }
            .onEach { row ->
                val (contact, isOnFlipcash) = row
                if (isOnFlipcash) {
                    val identifier = if (contact.e164.isNotEmpty()) {
                        ChatIdentifier.ByContact(
                            contact = contact,
                            chatId = row.conversation?.chatId
                        )
                    } else {
                        ChatIdentifier.ByChatId(row.conversation!!.chatId)
                    }
                    dispatchEvent(Event.NavigateToChat(identifier))
                } else {
                    dispatchEvent(Event.SendInvite(contact))
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ContactRemoved>()
            .onEach { event -> contactCoordinator.removeContact(event.e164) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ContactsRevoked>()
            .onEach { contactCoordinator.clearServerContactSetIfRevoked() }
            .launchIn(viewModelScope)

        // Observe the persisted callout-dismissal state so the callout hides when
        // dismissed and re-appears if the coordinator ever expires the dismissal.
        contactCoordinator.isPermissionCalloutDismissed
            .onEach { dismissed -> dispatchEvent(Event.CalloutDismissalChanged(dismissed)) }
            .launchIn(viewModelScope)

        // Persist the dismissal when the user taps dismiss.
        eventFlow
            .filterIsInstance<Event.DismissedPermissionCallout>()
            .onEach { contactCoordinator.onPermissionCalloutDismissed() }
            .launchIn(viewModelScope)

        combine(
            contactCoordinator.state,
            stateFlow.map { it.currentStep },
        ) { contactState, currentStep ->
            contactState to currentStep
        }
            .filter { (cs, _) -> cs.hasDiscoveredFlipcashContacts && cs.flipcashE164s.isNotEmpty() }
            .filter { (_, step) -> step is SendStep.ContactList }
            .take(1)
            .onEach { (contactState, _) ->
                val count = contactState.flipcashE164s.size
                contactCoordinator.consumeContactsDiscovery()
                BottomBarManager.showInfo(
                    title = resources.getQuantityString(
                        R.plurals.prompt_title_contactsAlreadyOnFlipcash,
                        count,
                        count.toString(),
                    ),
                    message = resources.getString(R.string.prompt_description_contactsAlreadyOnFlipcash),
                )
            }
            .launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.StepsUpdated -> { state ->
                    state.copy(steps = event.steps, isPickerMode = event.isPickerMode)
                }

                is Event.OnStepChanged -> { state ->
                    state.copy(currentStep = event.step)
                }

                is Event.ContactsGranted -> { state -> state }
                is Event.ContactsPicked -> { state -> state }
                is Event.ContactSyncStateUpdated -> { state ->
                    state.copy(
                        contactSyncState = LoadingSuccessState(
                            event.loading,
                            event.success,
                            event.error
                        )
                    )
                }

                is Event.ContactRemoved -> { state -> state }
                is Event.ContactSyncComplete -> { state -> state }
                is Event.OnItemsPopulated -> { state -> state.copy(listItems = event.items) }
                is Event.OnContactClicked -> { state -> state }
                is Event.SendInvite -> { state -> state }
                is Event.NavigateToChat -> { state -> state }
                is Event.ContactsRevoked -> { state -> state }
                is Event.DismissedPermissionCallout -> { state -> state }
                is Event.CalloutDismissalChanged -> { state ->
                    state.copy(hasCalloutBeenDismissed = event.dismissed)
                }
            }
        }
    }
}
