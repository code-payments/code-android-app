package com.flipcash.app.messenger.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.features.messenger.R
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.InviteToken
import com.flipcash.shared.chat.LobbyRequests
import com.flipcash.shared.chat.invite.DeadInviteReason
import com.flipcash.shared.chat.invite.GroupInviteLanding
import com.flipcash.shared.chat.invite.InviteJoinResult
import com.flipcash.shared.chat.invite.InviteLookup
import com.flipcash.shared.chat.invite.InviteRosterResult
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Instant

/** What the screen shows for an opened invite link (nodes 12846:59 to 12846:160). */
internal data class InviteLandingState(
    val phase: Phase = Phase.Loading,
    val group: ChatSubject.Group? = null,
    /** Rows loaded so far. Kept when a later page is refused, and never fetched for a dead link. */
    val members: List<ChatMember> = emptyList(),
    val memberCount: Long = 0,
    /** The link no longer admits anyone. [deadReason] says why, when it is known. */
    val dead: Boolean = false,
    val deadReason: DeadInviteReason? = null,
    val joining: Boolean = false,
    val requesting: Boolean = false,
    val requestedAt: Instant? = null,
) {
    enum class Phase { Loading, Preview, NotFound, Banned, LoadFailed }
}

/**
 * Opens an invite link: looks it up, shows the group, and joins on request. The secret is held
 * in this view model's memory only, never in state a log or a trace could print.
 */
@HiltViewModel
internal class InviteLandingViewModel @Inject constructor(
    private val landing: GroupInviteLanding,
    private val lobbyRequests: LobbyRequests,
    private val resources: ResourceHelper,
) : ViewModel() {

    sealed interface Effect {
        /** Land in the chat: the viewer is a member, already or just now. */
        data class OpenChat(val chatId: ChatId) : Effect
    }

    private val _state = MutableStateFlow(InviteLandingState())
    val state: StateFlow<InviteLandingState> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    private var secret: ByteArray? = null
    private var token: InviteToken? = null
    private var started = false

    /** Looks the link up once; the same secret again (a recomposition, a rotation) does nothing. */
    fun open(secret: ByteArray?) {
        if (started) return
        started = true
        if (secret == null) {
            _state.update { it.copy(phase = InviteLandingState.Phase.NotFound) }
            return
        }
        this.secret = secret
        viewModelScope.launch { lookup(secret) }
    }

    private suspend fun lookup(secret: ByteArray) {
        when (val result = landing.lookup(secret)) {
            is InviteLookup.AlreadyMember -> _effects.send(Effect.OpenChat(result.chatId))
            is InviteLookup.NotFound -> _state.update { it.copy(phase = InviteLandingState.Phase.NotFound) }
            is InviteLookup.Banned -> _state.update { it.copy(phase = InviteLandingState.Phase.Banned) }
            is InviteLookup.Failed -> _state.update { it.copy(phase = InviteLandingState.Phase.LoadFailed) }
            is InviteLookup.Found -> {
                token = result.token
                _state.update {
                    it.copy(
                        phase = InviteLandingState.Phase.Preview,
                        group = result.chat.toSubject(),
                        memberCount = result.chat.rosterSummary.memberCount,
                        dead = result.dead != null,
                        deadReason = result.dead,
                    )
                }
                // A dead link's roster is refused, so only a live one is asked for.
                if (result.dead == null) loadRoster(result.chat.chatId, result.token)
            }
        }
    }

    private suspend fun loadRoster(chatId: ChatId, token: InviteToken) {
        var page: PagingToken? = null
        do {
            when (val result = landing.roster(chatId, token, page)) {
                is InviteRosterResult.Page -> {
                    _state.update { it.copy(members = it.members + result.members, memberCount = result.memberCount) }
                    page = result.next
                }
                // The link stopped admitting people while the roster was being read.
                is InviteRosterResult.Denied -> {
                    markDead(reason = recheckReason())
                    return
                }
                is InviteRosterResult.Failed -> return
            }
        } while (page != null && _state.value.members.size < RosterRowCap)
    }

    /** The real status, asked for again; null when it can't be learned, which is not guessed. */
    private suspend fun recheckReason(): DeadInviteReason? =
        (secret?.let { landing.lookup(it) } as? InviteLookup.Found)?.dead

    private fun markDead(reason: DeadInviteReason?) {
        _state.update { it.copy(dead = true, deadReason = reason, joining = false) }
    }

    fun join() {
        val secret = secret ?: return
        val token = token ?: return
        val chatId = _state.value.group?.chatId ?: return
        if (_state.value.joining) return
        _state.update { it.copy(joining = true) }
        viewModelScope.launch {
            when (val result = landing.join(secret, chatId, token)) {
                is InviteJoinResult.Joined -> _effects.send(Effect.OpenChat(result.chatId))
                is InviteJoinResult.Dead -> markDead(result.reason)
                is InviteJoinResult.NotFound ->
                    _state.update { it.copy(phase = InviteLandingState.Phase.NotFound, joining = false) }
                is InviteJoinResult.Banned ->
                    _state.update { it.copy(phase = InviteLandingState.Phase.Banned, joining = false) }
                is InviteJoinResult.Failed -> {
                    _state.update { it.copy(joining = false) }
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_failedToJoinGroup),
                        message = resources.getString(R.string.error_description_failedToJoinGroupAlert),
                    )
                }
            }
        }
    }

    fun requestToJoin() {
        val chatId = _state.value.group?.chatId ?: return
        if (_state.value.requesting) return
        _state.update { it.copy(requesting = true) }
        viewModelScope.launch {
            lobbyRequests.request(chatId)
                .onSuccess { at -> _state.update { it.copy(requestedAt = at) } }
                .onFailure {
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_failedToRequestJoin),
                        message = resources.getString(R.string.error_description_failedToRequestJoin),
                    )
                }
            _state.update { it.copy(requesting = false) }
        }
    }

    fun cancelRequest() {
        val chatId = _state.value.group?.chatId ?: return
        if (_state.value.requesting) return
        _state.update { it.copy(requesting = true) }
        viewModelScope.launch {
            lobbyRequests.cancel(chatId).onSuccess { _state.update { it.copy(requestedAt = null) } }
            _state.update { it.copy(requesting = false) }
        }
    }

    private companion object {
        /** Enough rows for the grid and its count; the full list is the members' to open. */
        const val RosterRowCap = 200
    }
}

private fun ChatMetadata.toSubject() = ChatSubject.Group(
    chatId = chatId,
    groupTitle = title,
    picture = picture,
    memberCount = rosterSummary.memberCount,
    rules = rules,
    isMember = false,
    creator = creator,
    description = description,
    coverPicture = coverPicture,
    isPrivate = true,
)
