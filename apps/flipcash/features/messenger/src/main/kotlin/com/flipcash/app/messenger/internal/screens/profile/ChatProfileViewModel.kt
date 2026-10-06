package com.flipcash.app.messenger.internal.screens.profile

import android.content.ClipboardManager
import androidx.lifecycle.viewModelScope
import com.flipcash.app.blocklist.BlocklistCoordinator
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.extensions.setText
import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.app.core.toast.SystemToastController
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.features.messenger.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.chat.E2eePolicy
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.isMutedAt
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class ChatProfileViewModel @Inject constructor(
    private val contactCoordinator: ContactCoordinator,
    private val userManager: UserManager,
    private val featureFlags: FeatureFlagController,
    private val blocklist: BlocklistCoordinator,
    private val profiles: ProfileController,
    private val dispatchers: DispatcherProvider,
    private val resources: ResourceHelper,
    private val chatCoordinator: ChatCoordinator,
    private val tipPaymentDelegate: TipPaymentDelegate,
    private val e2eePolicy: E2eePolicy,
    private val startChattingPayer: StartChattingPayer,
    private val clipboardManager: ClipboardManager,
    private val toastController: SystemToastController,
) : BaseViewModel<ChatProfileViewModel.State, ChatProfileViewModel.Event>(
    initialState = State(selfId = userManager.accountId),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val participant: ChatParticipant? = null,
        val joinDate: Instant? = null,
        val processingState: LoadingSuccessState = LoadingSuccessState(),
        /** The viewer's own user id, so the screen can tell their own profile from someone else's. */
        val selfId: ID? = null,
        /**
         * The participant is the server's whole profile. A chat member or a contact carries only a
         * name, a username and a photo, so a missing bio or cover means nothing until this is true.
         */
        val isFullProfileLoaded: Boolean = false,
        /**
         * The profile fetch has finished, whether it succeeded or not. The start-chatting fee is a
         * field of the full profile, so the pinned action waits for this rather than state a
         * regional fee the person did not set.
         */
        val profileSettled: Boolean = false,
        /** The tip DM with this person, derived rather than fetched; it may not exist yet. */
        val dmChatId: ChatId? = null,
        /** Whether that DM has members, which is what makes it exist. */
        val dmExists: Boolean = false,
        val isBlocked: Boolean = false,
        val isMuted: Boolean = false,
        /** Null until it has loaded. */
        val fee: Fiat? = null,
        /** The E2EE footer's claim for the DM, decided by the policy from its metadata. */
        val isEncrypted: Boolean = false,
        /** The token a start-chatting payment would come from. */
        val token: Token? = null,
        val paymentSheetVisible: Boolean = false,
        val sendProgress: LoadingSuccessState = LoadingSuccessState(),
        /**
         * People whose fee was paid, or is being paid, from this view model. The fee sheet's own
         * show and dismiss reset [sendProgress], so this is what remembers that a payment is out:
         * a payment can only be repeated once the DM it bought has had time to appear, and never
         * while it is in flight.
         */
        val paidRecipients: Set<ID> = emptySet(),
        /**
         * A start-chatting payment is out, or paid and waiting for its chat: the fee sheet stays up
         * and cannot be dismissed until the chat is ready, or the payment fails.
         */
        val paymentInProgress: Boolean = false,
    ) {
        /** Null for your own profile, for anyone but a tip user, and until [profileSettled]. */
        val pinnedAction: ProfilePinnedAction?
            get() {
                val person = participant as? ChatParticipant.TipUser ?: return null
                if (!profileSettled) return null
                return resolvePinnedAction(
                    isSelf = person.userId == selfId,
                    isBlocked = isBlocked,
                    dmExists = dmExists,
                    fee = fee,
                    paid = person.userId in paidRecipients,
                )
            }

        val menuItems: List<ChatProfileAction>
            get() = profileMenuItems(isBlocked = isBlocked, hasDm = dmExists)
    }

    sealed interface Event {
        /**
         * [isFullProfile] says the participant is the server's whole profile, as a link's lookup
         * hands over, so its join date is current and there is no need to fetch it again. A
         * partial participant (a chat member, a contact) renders at once and is fetched.
         */
        data class OnParticipantSet(
            val participant: ChatParticipant,
            val isFullProfile: Boolean = false,
        ) : Event
        /** The fetch for a partial participant failed; the cached one stays. */
        data object ProfileFetchFailed : Event
        data class JoinDateLoaded(val joinDate: Instant?) : Event
        /** The participant as the server has them, replacing the cached one the screen opened on. */
        data class ProfileLoaded(val participant: ChatParticipant.TipUser) : Event
        data object BlockUser : Event
        data class BlockConfirmed(val participant: ChatParticipant.TipUser) : Event
        data class BlockProcessing(val loading: Boolean = false, val success: Boolean = false): Event
        data object BlockSuccessful: Event

        data class DmChatIdResolved(val chatId: ChatId?) : Event
        data class DmExistsChanged(val exists: Boolean) : Event
        data class BlockedChanged(val isBlocked: Boolean) : Event
        data class ChatStateChanged(val isMuted: Boolean, val isEncrypted: Boolean) : Event
        data class FeeLoaded(val fee: Fiat?) : Event
        data class TokenUpdated(val token: Token) : Event

        /** The pinned button was tapped; what it does is [State.pinnedAction]'s. */
        data object PinnedActionTapped : Event
        data object Unblock : Event
        /** The slide on the fee sheet was confirmed. */
        data object ConfirmStartChatting : Event
        /** The fee for [recipient] is out; nothing may pay it again until the DM shows or it fails. */
        data class PaymentStarted(val recipient: ID) : Event
        /** The payment for [recipient] did not go through, so it may be tried again. */
        data class PaymentFailed(val recipient: ID) : Event
        data object ShowPaymentSheet : Event
        data object DismissPaymentSheet : Event

        /** The paid chat is ready, or has been given up on: the sheet may go. */
        data object PaymentFinished : Event
        data class PaymentProgress(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event
        /** An Add Money alert action: asks for the deposit screen, which arrives as [OpenScreen]. */
        data object PresentDepositOptions : Event

        /** Outgoing: the host decides whether that is a pop back to the chat or a push. */
        data class OpenChat(val chatId: ChatId) : Event
        /**
         * Outgoing: no fee is known, so the amount is entered on the chat's keypad.
         *
         * TODO(profile-refresh slice 5): this route goes away when the chat becomes by-chat-id only.
         */
        data class OpenSendCash(val participant: ChatParticipant.TipUser) : Event
        data class OpenScreen(val route: AppRoute, val asSheet: Boolean = false) : Event
    }

    /** Puts the person's profile link on the clipboard, the one Share hands out. */
    fun copyLink() {
        val person = stateFlow.value.participant as? ChatParticipant.TipUser ?: return
        clipboardManager.setText(
            text = Linkify.tipcard(TipCardOwner.preferringUsername(person.profile.username, person.userId)),
            label = resources.getString(R.string.title_clipboardLabelTipCardLink),
        )
        toastController.showToast(R.string.action_copied, replacePrevious = true)
    }

    init {
        eventFlow
            .filterIsInstance<Event.OnParticipantSet>()
            .filter { it.participant is ChatParticipant.TipUser }
            // Not distinct: a repeat of the same participant (a second visit in a flow that keeps
            // this view model) has to fetch. Latest: a fetch for someone the screen has moved on
            // from is dropped rather than landing on the next person.
            .collectLatestIn(viewModelScope) { event ->
                val (userId, profile) = event.participant as ChatParticipant.TipUser
                if (event.isFullProfile) {
                    dispatchEvent(Event.JoinDateLoaded(profile.joinedAt))
                    return@collectLatestIn
                }
                // The participant came from the cache, which carries no join date and may carry a
                // roster page's blank name. The server profile replaces it whole; the cached one
                // stays when the fetch fails.
                val fetched = profiles.getProfileForUser(userId).getOrNull()
                if (fetched != null) {
                    dispatchEvent(Event.ProfileLoaded(ChatParticipant.TipUser(userId, fetched)))
                } else {
                    dispatchEvent(Event.ProfileFetchFailed)
                }
                dispatchEvent(Event.JoinDateLoaded(fetched?.joinedAt ?: profile.joinedAt))
            }

        eventFlow
            .filterIsInstance<Event.OnParticipantSet>()
            .map { it.participant }
            .filterIsInstance<ChatParticipant.Contact>()
            .distinctUntilChanged()
            .map { it.contact }
            .onEach { contact ->
                // TODO:
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.BlockUser>()
            .mapNotNull { stateFlow.value.participant }
            .filterIsInstance<ChatParticipant.TipUser>()
            .onEach { participant ->
                BottomBarManager.showAlert(
                    // "Block ?" is what this read for an account with no display name.
                    title = resources.getString(
                        R.string.prompt_title_blockUser,
                        participant.name ?: resources.getString(R.string.title_unnamedUser),
                    ),
                    message = resources.getString(R.string.prompt_description_blockUser),
                    actions = listOf(
                        BottomBarAction(
                            text = resources.getString(R.string.action_block),
                        ) {
                            dispatchEvent(Event.BlockConfirmed(participant))
                        }
                    ),
                    showCancel = true,
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.BlockConfirmed>()
            .map { it.participant }
            .onEach { participant ->
                dispatchEvent(Event.BlockProcessing(loading = true))
                blocklist.blockUser(participant.userId)
                    .onSuccess {
                        dispatchEvent(Event.BlockProcessing(success = true))
                        delay(500.milliseconds)
                        dispatchEvent(Event.BlockSuccessful)
                    }
                    .onFailure {
                        dispatchEvent(Event.BlockProcessing())
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_failedToBlock),
                            message = resources.getString(R.string.error_description_failedToBlock),
                        )
                    }
            }
            .launchIn(viewModelScope)

        initProfileObservers()
        initPinnedActionHandlers()
    }

    private fun initProfileObservers() {
        val userIds = stateFlow
            .map { (it.participant as? ChatParticipant.TipUser)?.userId }
            .filterNotNull()
            .distinctUntilChanged()

        userIds
            .onEach { userId ->
                dispatchEvent(Event.DmChatIdResolved(chatCoordinator.generateChatId(userId).getOrNull()))
            }
            .launchIn(viewModelScope)

        userIds
            .flatMapLatest { blocklist.observeIsBlocked(it) }
            .onEach { dispatchEvent(Event.BlockedChanged(it)) }
            .launchIn(viewModelScope)

        // Nullable on purpose: moving to a person with no DM yet has to end the last person's
        // subscriptions, and coming back to one has to start them again.
        val chatIds = stateFlow.map { it.dmChatId }.distinctUntilChanged()

        // A block hides the DM but leaves its members, so this stays true through one; the pinned
        // action puts blocked first for that reason.
        chatIds
            .flatMapLatest { chatId ->
                if (chatId == null) {
                    emptyFlow()
                } else {
                    chatCoordinator.observeMembers(chatId).map { it.isNotEmpty() }.distinctUntilChanged()
                }
            }
            .onEach { dispatchEvent(Event.DmExistsChanged(it)) }
            .launchIn(viewModelScope)

        chatIds
            .flatMapLatest { chatId ->
                if (chatId == null) {
                    emptyFlow()
                } else {
                    chatCoordinator.observeMetadata(chatId)
                        .map { membership ->
                            Event.ChatStateChanged(
                                // Evaluated on emission; a timed mute that lapses has no event of its own.
                                isMuted = membership?.metadata?.viewerState.isMutedAt(),
                                isEncrypted = membership?.metadata?.let(e2eePolicy::shouldEncrypt) ?: false,
                            )
                        }
                        .distinctUntilChanged()
                }
            }
            .onEach { dispatchEvent(it) }
            .launchIn(viewModelScope)

        stateFlow
            .map { state -> (state.participant as? ChatParticipant.TipUser)?.takeIf { state.profileSettled } }
            .distinctUntilChanged()
            .flatMapLatest { person ->
                if (person == null) emptyFlow() else tipPaymentDelegate.startChattingFee(person.profile)
            }
            .onEach { dispatchEvent(Event.FeeLoaded(it)) }
            .launchIn(viewModelScope)

        startChattingPayer.observeSelectedToken()
            .onEach { dispatchEvent(Event.TokenUpdated(it)) }
            .launchIn(viewModelScope)
    }

    private fun initPinnedActionHandlers() {
        eventFlow
            .filterIsInstance<Event.PinnedActionTapped>()
            .onEach {
                val state = stateFlow.value
                when (val action = state.pinnedAction) {
                    ProfilePinnedAction.Unblock -> dispatchEvent(Event.Unblock)
                    ProfilePinnedAction.OpenChat -> state.dmChatId?.let { dispatchEvent(Event.OpenChat(it)) }
                    is ProfilePinnedAction.StartChatting -> startChatting(action.fee)
                    ProfilePinnedAction.OpeningChat, null -> Unit
                }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Unblock>()
            .mapNotNull { (stateFlow.value.participant as? ChatParticipant.TipUser)?.userId }
            .onEach { userId ->
                blocklist.unblock(userId).onFailure {
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_failedToUnblock),
                        message = resources.getString(R.string.error_description_failedToUnblock),
                    )
                }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PresentDepositOptions>()
            .onEach {
                startChattingPayer.depositRoute()?.let { dispatchEvent(Event.OpenScreen(it)) }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ConfirmStartChatting>()
            .onEach { confirmStartChatting() }
            .launchIn(viewModelScope)
    }

    /** The gate first, then the fee sheet when the fee is known and the chat's keypad when not. */
    private suspend fun startChatting(fee: Fiat?) {
        val person = stateFlow.value.participant as? ChatParticipant.TipUser ?: return
        val mayProceed = startChattingPayer.mayProceed(
            onAddMoney = { dispatchEvent(Event.PresentDepositOptions) },
            onDiscoverCurrencies = {
                dispatchEvent(Event.OpenScreen(AppRoute.Token.Discovery, asSheet = true))
            },
        )
        if (!mayProceed) return
        if (fee != null) {
            dispatchEvent(Event.ShowPaymentSheet)
        } else {
            dispatchEvent(Event.OpenSendCash(person))
        }
    }

    /**
     * Pays, then waits for the DM to appear, all off the event collector: the wait can run to
     * [MEMBERS_TIMEOUT], and a collector held that long stalls every other event, including a
     * second tap that would pay again. The person and chat are read once, up front.
     */
    private fun confirmStartChatting() {
        val state = stateFlow.value
        val person = state.participant as? ChatParticipant.TipUser ?: return
        val fee = state.fee ?: return
        if (!state.sendProgress.isIdle || person.userId in state.paidRecipients) return
        val knownChatId = state.dmChatId

        dispatchEvent(Event.PaymentStarted(person.userId))
        dispatchEvent(Event.PaymentProgress(loading = true))
        viewModelScope.launch {
            startChattingPayer.pay(
                recipient = person.userId,
                fee = fee,
                onAddMoney = { dispatchEvent(Event.PresentDepositOptions) },
            ).onSuccess { paidChatId ->
                dispatchEvent(Event.PaymentProgress(success = true))
                delay(SUCCESS_HOLD)
                val chatId = paidChatId ?: knownChatId
                if (chatId == null) {
                    dispatchEvent(Event.PaymentFinished)
                    dispatchEvent(Event.DismissPaymentSheet)
                    return@onSuccess
                }
                // The server creates the DM from the payment, so its members arrive a moment
                // after. The sheet stays up, showing its success, until they do. If they never do
                // (as on iOS) the sheet goes and the profile stays: the pinned action flips to Open
                // Chat when they land.
                val arrived = withTimeoutOrNull(MEMBERS_TIMEOUT) {
                    chatCoordinator.observeMembers(chatId).first { it.isNotEmpty() }
                }
                dispatchEvent(Event.PaymentFinished)
                dispatchEvent(Event.DismissPaymentSheet)
                // Not if the screen has moved on to someone else in the meantime.
                val stillHere = (stateFlow.value.participant as? ChatParticipant.TipUser)?.userId == person.userId
                if (arrived != null && stillHere) dispatchEvent(Event.OpenChat(chatId))
            }.onFailure { cause ->
                dispatchEvent(Event.PaymentFailed(person.userId))
                dispatchEvent(Event.PaymentProgress())
                // A blocked payment has said why already.
                if (cause !is StartChattingPayer.PaymentBlocked) {
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_cashFailedToSend),
                        message = resources.getString(R.string.error_description_cashFailedToSend),
                    )
                }
            }
        }
    }

    companion object {
        private val SUCCESS_HOLD = 400.milliseconds
        private val MEMBERS_TIMEOUT = 10.seconds


        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnParticipantSet -> { state -> state.withParticipantSet(event) }
                is Event.JoinDateLoaded -> { state -> state.copy(joinDate = event.joinDate) }
                is Event.ProfileLoaded -> { state ->
                    // A fetch that lands after the screen moved on is someone else's.
                    if ((state.participant as? ChatParticipant.TipUser)?.userId != event.participant.userId) {
                        state
                    } else state.copy(
                        participant = event.participant,
                        isFullProfileLoaded = true,
                        profileSettled = true,
                    )
                }
                Event.ProfileFetchFailed -> { state -> state.copy(profileSettled = true) }
                is Event.DmChatIdResolved -> { state -> state.copy(dmChatId = event.chatId) }
                is Event.DmExistsChanged -> { state -> state.copy(dmExists = event.exists) }
                is Event.BlockedChanged -> { state -> state.copy(isBlocked = event.isBlocked) }
                is Event.ChatStateChanged -> { state ->
                    state.copy(isMuted = event.isMuted, isEncrypted = event.isEncrypted)
                }
                is Event.FeeLoaded -> { state -> state.copy(fee = event.fee) }
                is Event.TokenUpdated -> { state -> state.copy(token = event.token) }
                is Event.PaymentStarted -> { state ->
                    state.copy(
                        paidRecipients = state.paidRecipients.toMutableSet().apply { add(event.recipient) },
                        paymentInProgress = true,
                    )
                }
                is Event.PaymentFailed -> { state ->
                    state.copy(
                        paidRecipients = state.paidRecipients.toMutableSet().apply { remove(event.recipient) },
                        paymentInProgress = false,
                    )
                }
                Event.ShowPaymentSheet -> { state ->
                    if (state.paymentInProgress) state
                    else state.copy(paymentSheetVisible = true, sendProgress = LoadingSuccessState())
                }
                // Not while a payment is out: the sheet is the only place its progress shows.
                Event.DismissPaymentSheet -> { state ->
                    if (state.paymentInProgress) state
                    else state.copy(paymentSheetVisible = false, sendProgress = LoadingSuccessState())
                }
                Event.PaymentFinished -> { state -> state.copy(paymentInProgress = false) }
                is Event.PaymentProgress -> { state ->
                    state.copy(sendProgress = LoadingSuccessState(event.loading, event.success))
                }
                is Event.BlockProcessing -> { state ->
                    val current = state.processingState
                    state.copy(
                        processingState = current.copy(
                            loading = event.loading,
                            success = event.success,
                        )
                    )
                }
                is Event.BlockUser,
                is Event.BlockConfirmed,
                is Event.BlockSuccessful,
                Event.PinnedActionTapped,
                Event.Unblock,
                Event.ConfirmStartChatting,
                Event.PresentDepositOptions,
                is Event.OpenChat,
                is Event.OpenSendCash,
                is Event.OpenScreen -> { state -> state }
            }
        }
    }
}

/**
 * Another person resets everything that was the last one's; the same person again keeps what is
 * known, so the pinned button and bio do not vanish while the background refetch lands.
 */
private fun ChatProfileViewModel.State.withParticipantSet(
    event: ChatProfileViewModel.Event.OnParticipantSet,
): ChatProfileViewModel.State {
    val incoming = event.participant
    val sameUser = incoming is ChatParticipant.TipUser &&
        (participant as? ChatParticipant.TipUser)?.userId == incoming.userId
    if (sameUser) {
        return copy(
            // The server's profile stays rather than being swapped for the cached one.
            participant = if (isFullProfileLoaded && !event.isFullProfile) participant else incoming,
            isFullProfileLoaded = isFullProfileLoaded || event.isFullProfile,
            profileSettled = profileSettled || event.isFullProfile,
        )
    }
    return ChatProfileViewModel.State(
        participant = incoming,
        selfId = selfId,
        isFullProfileLoaded = event.isFullProfile,
        profileSettled = event.isFullProfile,
        // Not per person: the selected token, and who has been paid.
        token = token,
        paidRecipients = paidRecipients,
    )
}

private fun <T> Flow<T>.collectLatestIn(scope: CoroutineScope, block: suspend (T) -> Unit) {
    scope.launch { collectLatest(block) }
}
