package com.flipcash.app.directsend.internal

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.contacts.ContactCoordinator.ContactState
import com.flipcash.app.contacts.device.DeviceContact
import com.flipcash.app.contacts.device.PickedContactData
import com.flipcash.app.core.send.SendStep
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.permissions.PickedContact
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.directsend.R
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
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
    private val tokenCoordinator: TokenCoordinator,
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

        data class NavigateToChat(val contact: DeviceContact) : Event
        data class NavigateToDirectSend(val contact: DeviceContact) : Event
        data object NavigateToDiscovery : Event
    }

    private val messengerEnabled = featureFlags.observe(FeatureFlag.Messenger)

    init {
        combine(
            userManager.state,
            featureFlags.observe(FeatureFlag.PhoneNumberSend),
            featureFlags.observe(FeatureFlag.ContactPickerMode),
            contactCoordinator.state,
        ) { userState, phoneNumberSendFlag, contactPickerMode, contactState ->
            val hasLinkedPhone = userState.userProfile?.verifiedPhoneNumber != null
            val phoneNumberSendEnabled = phoneNumberSendFlag ||
                    userState.flags?.enablePhoneNumberSend == true
            val hasContacts = contactState.contacts.isNotEmpty()
            val needsContacts = phoneNumberSendEnabled && !hasContacts && !contactState.hasEverSynced

            val steps = buildList {
                if (!hasLinkedPhone) add(SendStep.PhoneGate)
                if (needsContacts) add(SendStep.ContactsGate)
                add(SendStep.ContactList)
            }
            Event.StepsUpdated(steps = steps, isPickerMode = contactPickerMode)
        }.onEach { event ->
            dispatchEvent(event)
        }.launchIn(viewModelScope)

        combine(
            contactCoordinator.state,
            stateFlow.map { it.searchState }.distinctUntilChanged().flatMapLatest { snapshotFlow { it.text } }
        ) { contactState, searchText ->
            generateListItems(contactState, searchText.toString())
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
            .onEach { (contact, isOnFlipcash) ->
                if (isOnFlipcash) {
                    if (messengerEnabled.value) {
                        dispatchEvent(Event.NavigateToChat(contact))
                    } else {
                        if (!tokenCoordinator.hasGiveableBalance()) {
                            BottomBarManager.showInfo(
                                title = resources.getString(R.string.title_noBalanceYet),
                                message = resources.getString(R.string.description_noBalanceYet),
                                actions = listOf(
                                    BottomBarAction(
                                        text = resources.getString(R.string.action_discoverCurrencies)
                                    ) {
                                        dispatchEvent(Event.NavigateToDiscovery)
                                    },
                                ),
                                showCancel = true,
                            )
                            return@onEach
                        }
                        dispatchEvent(Event.NavigateToDirectSend(contact))
                    }
                } else {
                    dispatchEvent(Event.SendInvite(contact))
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ContactRemoved>()
            .onEach { event -> contactCoordinator.removeContact(event.e164) }
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

    private fun generateListItems(
        contactState: ContactState,
        searchString: String,
    ): List<ContactListItem> = buildList {
        val allContacts = contactState.contacts.values.toList()
        val filtered = if (searchString.isBlank()) {
            allContacts
        } else {
            allContacts.filter {
                it.displayName.contains(searchString, ignoreCase = true) ||
                        it.e164.contains(searchString, ignoreCase = true)
            }
        }

        val flipcash = filtered
            .filter { it.e164 in contactState.flipcashE164s }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
        val other = filtered
            .filter { it.e164 !in contactState.flipcashE164s }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })

        if (flipcash.isNotEmpty()) {
            add(ContactListItem.Header(resources.getString(R.string.title_flipcashContacts)))
            flipcash.forEach { add(ContactListItem.ContactRow(it, isOnFlipcash = true)) }
        }
        if (other.isNotEmpty()) {
            add(ContactListItem.Header(resources.getString(R.string.title_nonFlipcashContacts)))
            other.forEach { add(ContactListItem.ContactRow(it, isOnFlipcash = false)) }
        }
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
                is Event.NavigateToDirectSend -> { state -> state }
                is Event.NavigateToDiscovery -> { state -> state }
            }
        }
    }
}
