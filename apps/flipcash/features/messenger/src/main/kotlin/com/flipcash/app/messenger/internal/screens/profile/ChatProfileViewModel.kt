package com.flipcash.app.messenger.internal.screens.profile

import androidx.lifecycle.viewModelScope
import com.flipcash.app.blocklist.BlocklistCoordinator
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.menu.MenuItem
import com.flipcash.features.messenger.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

private val ProfileMenuItems = buildList {
    add(BlockUser)
}

@HiltViewModel
internal class ChatProfileViewModel @Inject constructor(
    private val contactCoordinator: ContactCoordinator,
    private val userManager: UserManager,
    private val featureFlags: FeatureFlagController,
    private val blocklist: BlocklistCoordinator,
    private val profiles: ProfileController,
    private val dispatchers: DispatcherProvider,
    private val resources: ResourceHelper,
) : BaseViewModel<ChatProfileViewModel.State, ChatProfileViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    data class State(
        val participant: ChatParticipant? = null,
        val joinDate: Instant? = null,
        /** Whether to offer a conversation with this person — see [Event.OnParticipantSet]. */
        val canOpenChat: Boolean = false,
        val menuItems: List<MenuItem<Event>> = ProfileMenuItems,
        val processingState: LoadingSuccessState = LoadingSuccessState(),
    )

    sealed interface Event {
        /**
         * The profile this screen is for.
         *
         * [fromConversation] is whether [participant] is the counterparty of the chat behind this
         * screen. When they are, there is no conversation to offer — it is the transcript the
         * reader backs out to. When they are not, as for a group member or a tip card link's owner,
         * the reader may have no chat with them at all, and this is where it starts.
         */
        data class OnParticipantSet(
            val participant: ChatParticipant,
            val fromConversation: Boolean,
        ) : Event

        /** Open the conversation with this person. Answered by the flow, which owns the navigator. */
        data object OpenChat : Event
        data class JoinDateLoaded(val joinDate: Instant?) : Event
        data object BlockUser : Event
        data class BlockConfirmed(val participant: ChatParticipant.TipUser) : Event
        data class BlockProcessing(val loading: Boolean = false, val success: Boolean = false): Event
        data object BlockSuccessful: Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.OnParticipantSet>()
            .map { it.participant }
            .filterIsInstance<ChatParticipant.TipUser>()
            .distinctUntilChanged()
            .onEach { (userId, profile) ->
                // The cached member profile doesn't carry a join date, so resolve it from the
                // server profile, falling back to whatever the participant already had.
                val joinDate = profiles.getProfileForUser(userId).getOrNull()?.joinedAt
                    ?: profile.joinedAt
                dispatchEvent(Event.JoinDateLoaded(joinDate))
            }
            .launchIn(viewModelScope)

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
                        participant.name.orEmpty(),
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
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnParticipantSet -> { state ->
                    state.copy(
                        participant = event.participant,
                        // A device contact carries no Flipcash identity, so there is no user id to
                        // derive a chat from even when the profile is not this chat's own.
                        canOpenChat = !event.fromConversation &&
                            event.participant is ChatParticipant.TipUser,
                    )
                }
                is Event.OpenChat -> { state -> state }
                is Event.JoinDateLoaded -> { state -> state.copy(joinDate = event.joinDate) }
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
                is Event.BlockSuccessful -> { state -> state }
            }
        }
    }
}