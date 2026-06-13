package com.flipcash.app.directsend.internal

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.contacts.ContactCoordinator.ContactState
import com.flipcash.app.contacts.device.DeviceContact
import com.flipcash.app.contacts.device.PickedContactData
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.send.SendStep
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.payments.PurchaseMethodController
import com.flipcash.app.phone.PhoneUtils
import com.flipcash.app.permissions.PickedContact
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.directsend.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatSummary
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.decodeBase58
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
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
internal class SendFlowViewModel @Inject constructor(
    private val userManager: UserManager,
    featureFlags: FeatureFlagController,
    private val contactCoordinator: ContactCoordinator,
    chatCoordinator: ChatCoordinator,
    private val tokenCoordinator: TokenCoordinator,
    private val phoneUtils: PhoneUtils,
    private val resources: ResourceHelper,
    purchaseMethodController: PurchaseMethodController,
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

        data class NavigateToChat(val identifier: AppRoute.ChatIdentifier) : Event
        data class NavigateToDirectSend(val contact: DeviceContact) : Event
        data object PresentDepositOptions : Event
        data class NavigateToUsdfDepositOption(val route: AppRoute): Event
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
            stateFlow.map { it.searchState }.distinctUntilChanged().flatMapLatest { snapshotFlow { it.text } },
            chatCoordinator.feed,
            tokenCoordinator.tokens,
            featureFlags.observe(FeatureFlag.Messenger),
        ) { contactState, searchText, chatFeed, tokens, messengerOn ->
            val tokensByMint = tokens.associateBy { it.address }
            generateListItems(contactState, searchText.toString(), chatFeed, tokensByMint, messengerOn)
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
                    if (featureFlags.get(FeatureFlag.Messenger)) {
                        val identifier = if (contact.e164.isNotEmpty()) {
                            AppRoute.ChatIdentifier.ByContact(contact.e164, contact.displayName, row.chatId)
                        } else {
                            AppRoute.ChatIdentifier.ByChatId(row.chatId!!)
                        }
                        dispatchEvent(Event.NavigateToChat(identifier))
                    } else {
                        if (!tokenCoordinator.hasGiveableBalance()) {
                            BottomBarManager.showInfo(
                                title = resources.getString(R.string.title_noBalanceYet),
                                message = resources.getString(R.string.description_noBalanceYet),
                                actions = listOf(
                                    BottomBarAction(
                                        text = resources.getString(R.string.action_depositFunds)
                                    ) {
                                        dispatchEvent(Event.PresentDepositOptions)
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
            .filterIsInstance<Event.PresentDepositOptions>()
            .onEach {
                purchaseMethodController.presentDepositOptions()?.let { route ->
                    dispatchEvent(Event.NavigateToUsdfDepositOption(route))
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
        chatFeed: List<ChatSummary>,
        tokensByMint: Map<Mint, Token>,
        messengerEnabled: Boolean,
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

        val selfId = userManager.accountId
        val contactsByE164 = contactState.contacts

        val flipcashRows = if (messengerEnabled) {
            val dmChats = chatFeed.filter { it.metadata.type == ChatType.DM }
            // Index chat feed by chatId (base58) for reliable lookup via dmChatIds
            val chatById = dmChats.associateBy { it.metadata.chatId.toString() }
            // Track which chat IDs are consumed by device-contact rows
            val consumedChatIds = mutableSetOf<String>()

            // 1. Device contacts on Flipcash — enriched with chat preview when a DM exists
            val deviceContactRows = filtered
                .filter { it.e164 in contactState.flipcashE164s }
                .map { contact ->
                    val chatIdStr = contactState.dmChatIds[contact.e164]
                    val chat = chatIdStr?.let { chatById[it] }
                    if (chatIdStr != null) consumedChatIds += chatIdStr
                    val preview = chat?.let { formatPreview(it, selfId, tokensByMint) }
                    val chatId = chat?.metadata?.chatId
                        ?: chatIdStr?.let { runCatching { ChatId(it.decodeBase58()) }.getOrNull() }
                    ContactListItem.ContactRow(
                        contact = contact,
                        isOnFlipcash = true,
                        lastMessagePreview = preview,
                        unreadCount = chat?.unreadCount ?: 0,
                        chatId = chatId,
                        lastActivity = chat?.metadata?.lastActivity,
                    )
                }

            // 2. Non-contact DMs (chats not matched to a device contact)
            val nonContactRows = dmChats
                .filter { it.metadata.chatId.toString() !in consumedChatIds }
                .mapNotNull { summary ->
                    val otherMember = summary.metadata.members
                        .firstOrNull { it.userId != selfId } ?: return@mapNotNull null
                    val phone = otherMember.userProfile.verifiedPhoneNumber
                    val formattedPhone = phone?.let { phoneUtils.formatNumber(it) }
                    val displayName = otherMember.userProfile.displayName?.takeIf { it.isNotBlank() }
                        ?: formattedPhone
                        ?: "Unknown Contact"

                    val contact = DeviceContact.unknownContact(
                        e164 = phone.orEmpty(),
                        displayName = displayName,
                        displayNumber = formattedPhone,
                    )
                    if (searchString.isNotBlank() &&
                        !contact.displayName.contains(searchString, ignoreCase = true)) {
                        return@mapNotNull null
                    }
                    val preview = formatPreview(summary, selfId, tokensByMint)
                    ContactListItem.ContactRow(
                        contact = contact,
                        isOnFlipcash = true,
                        lastMessagePreview = preview,
                        unreadCount = summary.unreadCount,
                        chatId = summary.metadata.chatId,
                        lastActivity = summary.metadata.lastActivity,
                    )
                }

            (deviceContactRows + nonContactRows)
                .sortedWith(
                    compareByDescending<ContactListItem.ContactRow> { it.lastActivity }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.contact.displayName }
                )
        } else {
            // Driven by device contacts on Flipcash
            filtered
                .filter { it.e164 in contactState.flipcashE164s }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
                .map { ContactListItem.ContactRow(contact = it, isOnFlipcash = true) }
        }

        val flipcashE164s = flipcashRows.mapTo(mutableSetOf()) { it.contact.e164 }
            .plus(contactState.flipcashE164s)

        val other = filtered
            .filter { it.e164 !in flipcashE164s }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })

        if (flipcashRows.isNotEmpty()) {
            add(ContactListItem.Header(resources.getString(R.string.title_flipcashContacts)))
            addAll(flipcashRows)
        }
        if (other.isNotEmpty()) {
            add(ContactListItem.Header(resources.getString(R.string.title_nonFlipcashContacts)))
            other.forEach { add(ContactListItem.ContactRow(it, isOnFlipcash = false)) }
        }
    }

    private fun formatPreview(
        summary: ChatSummary,
        selfId: ID?,
        tokensByMint: Map<Mint, Token>,
    ): String? {
        val lastMsg = summary.metadata.lastMessage ?: return null
        val sentBySelf = lastMsg.senderId != null && lastMsg.senderId == selfId
        return lastMsg.content.firstOrNull()?.let { content ->
            when (content) {
                is MessageContent.Text -> content.text.takeIf { it.isNotEmpty() }
                is MessageContent.Cash -> {
                    val formatted = content.amount.formatted()
                    val name = content.tokenName.ifBlank {
                        tokensByMint[content.mint]?.name.orEmpty()
                    }
                    val label = if (name.isNotBlank()) "$formatted of $name" else formatted
                    if (sentBySelf) "You sent $label" else "You received $label"
                }
            }
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
                is Event.PresentDepositOptions -> { state -> state }
                is Event.NavigateToUsdfDepositOption -> { state -> state }
            }
        }
    }
}
