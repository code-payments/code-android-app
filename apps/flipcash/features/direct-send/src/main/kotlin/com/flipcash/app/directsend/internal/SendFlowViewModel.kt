package com.flipcash.app.directsend.internal

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.contacts.ContactCoordinator.ContactState
import com.flipcash.app.contacts.device.DeviceContact
import com.flipcash.app.contacts.device.PickedContactData
import com.flipcash.app.core.extensions.flatMapResult
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.send.SendStep
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.permissions.PickedContact
import com.flipcash.features.directsend.R
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.toFiat
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
internal class SendFlowViewModel @Inject constructor(
    private val userManager: UserManager,
    featureFlags: FeatureFlagController,
    private val contactCoordinator: ContactCoordinator,
    private val resources: ResourceHelper,
    private val exchange: Exchange,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    private val transactionController: TransactionController,
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
        val sendProgress: LoadingSuccessState = LoadingSuccessState(),
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
        data class SendCashToContact(val contact: DeviceContact) : Event

        data class NavigateToAmountEntry(
            val e164: String,
            val displayName: String,
        ) : Event

        data class ResolveCompleted(val contact: DeviceContact, val authority: PublicKey) : Event
        data class ResolveFailed(val e164: String) : Event

        data class OnSendRequested(
            val amount: Fiat,
            val token: Token,
            val destinationOwner: PublicKey,
        ) : Event
        data class SendStateUpdated(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event
        data class SendComplete(val amount: Fiat) : Event
        data object ContactNotResolved : Event
    }

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
            stateFlow.map { it.searchState }.flatMapLatest { snapshotFlow { it.text } }
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
                    dispatchEvent(Event.SendCashToContact(contact))
                } else {
                    dispatchEvent(Event.SendInvite(contact))
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.SendCashToContact>()
            .map { it.contact }
            .flatMapLatest { contact ->
                flow {
                    dispatchEvent(Event.NavigateToAmountEntry(
                        e164 = contact.e164,
                        displayName = contact.displayName,
                    ))

                    contactCoordinator.resolve(contact.e164)
                        .onSuccess { authority ->
                            dispatchEvent(Event.ResolveCompleted(contact, authority))
                        }
                        .onFailure {
                            dispatchEvent(Event.ResolveFailed(contact.e164))
                        }
                    emit(Unit)
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ContactRemoved>()
            .onEach { event -> contactCoordinator.removeContact(event.e164) }
            .launchIn(viewModelScope)

        contactCoordinator.state
            .filter { it.hasDiscoveredFlipcashContacts && it.flipcashE164s.isNotEmpty() }
            .filter { stateFlow.value.currentStep is SendStep.ContactList }
            .take(1)
            .onEach { contactState ->
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

        eventFlow.filterIsInstance<Event.OnSendRequested>()
            .onEach { (amount, token, destination) ->
                viewModelScope.launch {
                    val owner = userManager.accountCluster ?: return@launch
                    val rate = exchange.preferredRate

                    dispatchEvent(Event.SendStateUpdated(loading = true))

                    val source = owner.withTimelockForToken(token)

                    val verifiedFiat = verifiedFiatCalculator.compute(
                        amount = amount,
                        token = token,
                        rate = rate,
                    ).getOrElse { error ->
                        dispatchEvent(Event.SendStateUpdated())
                        val (title, message) = when (error) {
                            is ComputeVerifiedFiatError.AmountBelowMinimum -> {
                                R.string.error_title_amountTooSmall to R.string.error_description_amountTooSmall
                            }
                            else -> {
                                R.string.error_title_staleRates to R.string.error_description_staleRates
                            }
                        }
                        BottomBarManager.showAlert(
                            title = resources.getString(title),
                            message = resources.getString(message),
                        )
                        return@launch
                    }

                    transactionController.directTransfer(
                        amount = verifiedFiat,
                        token = token,
                        source = source,
                        destinationOwner = destination,
                    ).fold(
                        onSuccess = {
                            Result.success(verifiedFiat)
                        },
                        onFailure = { Result.failure(it) }
                    ).onSuccess { amount ->
                        timber.log.Timber.d("directTransfer success, dispatching checkmark")
                        dispatchEvent(Event.SendStateUpdated(success = true))
                        delay(400)
                        timber.log.Timber.d("dispatching SendComplete")
                        dispatchEvent(
                            Dispatchers.Main,
                            Event.SendComplete(amount.localFiat.nativeAmount)
                        )
                        timber.log.Timber.d("SendComplete dispatched")
                    }.onFailure {
                        dispatchEvent(Event.SendStateUpdated())
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_cashFailedToSend),
                            message = resources.getString(R.string.error_description_cashFailedToSend),
                        )
                    }
                }
            }.launchIn(viewModelScope)
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
                is Event.SendCashToContact -> { state -> state }
                is Event.NavigateToAmountEntry -> { state -> state.copy(sendProgress = LoadingSuccessState()) }
                is Event.ResolveCompleted -> { state -> state }
                is Event.ResolveFailed -> { state -> state }
                is Event.OnSendRequested -> { state -> state }
                is Event.SendStateUpdated -> { state ->
                    state.copy(
                        sendProgress = LoadingSuccessState(
                            event.loading,
                            event.success,
                        )
                    )
                }
                is Event.SendComplete -> { state -> state }
                Event.ContactNotResolved -> { state -> state }
            }
        }
    }
}
