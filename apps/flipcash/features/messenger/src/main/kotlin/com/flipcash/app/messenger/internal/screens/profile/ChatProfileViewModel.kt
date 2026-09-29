package com.flipcash.app.messenger.internal.screens.profile

import androidx.lifecycle.viewModelScope
import com.flipcash.app.blocklist.BlocklistCoordinator
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.features.messenger.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

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
    )

    sealed interface Event {
        /**
         * [fromServer] says the participant's profile was just fetched, as a link's lookup does, so
         * its join date is current and there is no need to fetch it again.
         */
        data class OnParticipantSet(
            val participant: ChatParticipant,
            val fromServer: Boolean = false,
        ) : Event
        data class JoinDateLoaded(val joinDate: Instant?) : Event
        /** The participant as the server has them, replacing the cached one the screen opened on. */
        data class ProfileLoaded(val participant: ChatParticipant.TipUser) : Event
        data object BlockUser : Event
        data class BlockConfirmed(val participant: ChatParticipant.TipUser) : Event
        data class BlockProcessing(val loading: Boolean = false, val success: Boolean = false): Event
        data object BlockSuccessful: Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.OnParticipantSet>()
            .filter { it.participant is ChatParticipant.TipUser }
            .distinctUntilChanged()
            .onEach { event ->
                val (userId, profile) = event.participant as ChatParticipant.TipUser
                if (event.fromServer) {
                    dispatchEvent(Event.JoinDateLoaded(profile.joinedAt))
                    return@onEach
                }
                // The participant came from the cache, which carries no join date and may carry a
                // roster page's blank name. The server profile replaces it whole; the cached one
                // stays when the fetch fails.
                val fetched = profiles.getProfileForUser(userId).getOrNull()
                if (fetched != null) {
                    dispatchEvent(Event.ProfileLoaded(ChatParticipant.TipUser(userId, fetched)))
                }
                dispatchEvent(Event.JoinDateLoaded(fetched?.joinedAt ?: profile.joinedAt))
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
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnParticipantSet -> { state -> state.copy(participant = event.participant) }
                is Event.JoinDateLoaded -> { state -> state.copy(joinDate = event.joinDate) }
                is Event.ProfileLoaded -> { state -> state.copy(participant = event.participant) }
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