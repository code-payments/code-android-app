package com.flipcash.app.messenger.internal.invites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.HostInvite
import com.flipcash.shared.chat.HostInvites
import com.flipcash.shared.chat.HostInvites.Companion.isLostInviteAccess
import com.flipcash.shared.chat.InviteCreation
import com.flipcash.shared.chat.InviteSettings
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
import kotlin.time.Clock

/** Frames 2a to 2e: the host's invite links, Active then Inactive. */
@HiltViewModel
internal class ManageInvitesViewModel @Inject constructor(
    private val hostInvites: HostInvites,
    private val resources: ResourceHelper,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        /** The first load failed, so an empty list means nothing was fetched rather than no links. */
        val failed: Boolean = false,
        val active: List<HostInvite> = emptyList(),
        val inactive: List<HostInvite> = emptyList(),
        /** The link the invite sheet shares; its row carries the Current chip. */
        val current: HostInvite? = null,
        val creating: Boolean = false,
    ) {
        val isEmpty: Boolean get() = !loading && !failed && active.isEmpty() && inactive.isEmpty()
    }

    sealed interface Effect {
        /** The viewer lost `can_invite`; back to the profile. */
        data object LostAccess : Effect

        /** A link was made; close the New Link sheet. */
        data object Created : Effect
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    private var chatId: ChatId? = null

    fun open(chatId: ChatId) {
        if (this.chatId == chatId) return
        this.chatId = chatId
        refresh()
    }

    fun refresh() {
        val chatId = chatId ?: return
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            hostInvites.load(chatId)
                .onSuccess { publish(chatId) }
                .onFailure { error ->
                    if (error.isLostInviteAccess()) {
                        lostAccess(chatId)
                    } else {
                        _state.update { it.copy(loading = false, failed = true) }
                        BottomBarManager.showError(
                            title = resources.getString(R.string.title_inviteLinksNotLoaded),
                            message = resources.getString(R.string.description_inviteLinkNotCreated),
                        )
                    }
                }
        }
    }

    /** Asks first, because revoking cannot be undone (frame 2c). */
    fun confirmRevoke(invite: HostInvite) {
        showRevokeInviteLink(resources) { revoke(invite) }
    }

    private fun revoke(invite: HostInvite) {
        val chatId = chatId ?: return
        viewModelScope.launch {
            hostInvites.revoke(chatId, invite.invite.id)
                .onSuccess { publish(chatId) }
                .onFailure { error ->
                    if (error.isLostInviteAccess()) {
                        lostAccess(chatId)
                    } else {
                        BottomBarManager.showError(
                            title = resources.getString(R.string.title_inviteLinkNotRevoked),
                            message = resources.getString(R.string.description_inviteLinkNotCreated),
                        )
                    }
                }
        }
    }

    /** Frame 2d's Create Link. The new link becomes the sheet's current link. */
    fun create(settings: InviteSettings) {
        val chatId = chatId ?: return
        if (_state.value.creating) return
        _state.update { it.copy(creating = true) }
        viewModelScope.launch {
            when (val result = hostInvites.create(chatId, settings)) {
                is InviteCreation.Created -> {
                    _state.update { it.copy(creating = false) }
                    publish(chatId)
                    _effects.send(Effect.Created)
                }
                InviteCreation.TooMany -> {
                    _state.update { it.copy(creating = false) }
                    showTooManyInviteLinks(resources)
                }
                InviteCreation.NotAvailable -> {
                    _state.update { it.copy(creating = false) }
                    showInviteLinksNotAvailable(resources)
                }
                is InviteCreation.Failed -> {
                    _state.update { it.copy(creating = false) }
                    showInviteLinkNotCreated(resources)
                }
            }
        }
    }

    private fun publish(chatId: ChatId) {
        val (active, inactive) = HostInvites.sections(hostInvites.state.value[chatId].orEmpty(), Clock.System.now())
        _state.update {
            it.copy(
                loading = false,
                failed = false,
                active = active,
                inactive = inactive,
                current = hostInvites.current(chatId),
            )
        }
    }

    private fun lostAccess(chatId: ChatId) {
        hostInvites.revokeAccess(chatId)
        _effects.trySend(Effect.LostAccess)
    }
}
