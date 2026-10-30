package com.flipcash.app.messenger.internal.invites

import android.content.ClipboardManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.core.extensions.setSensitiveText
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.messenger.internal.trackInviteLinkCreated
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ApproveNewMembersStore
import com.flipcash.shared.chat.GroupCardLinks
import com.flipcash.shared.chat.HostInvite
import com.flipcash.shared.chat.HostInvites
import com.flipcash.shared.chat.HostInvites.Companion.isLostInviteAccess
import com.flipcash.shared.chat.InviteCreation
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Frames 1m to 1m″: the host's link card, Approve new members and Manage Invites (spec surface 1).
 *
 * Off, the card is the current invite link, made on the first Share Link, Copy Link or Show Group
 * Card rather than on open. On, it is the plain `/chat/{uuid}` link. The switch only picks which
 * one the card shows; it revokes nothing.
 *
 * What needs navigation is an [Effect] for the sheet, which has the navigators.
 */
@HiltViewModel
internal class InviteHostSheetViewModel @Inject constructor(
    private val hostInvites: HostInvites,
    private val approveStore: ApproveNewMembersStore,
    private val cardLinks: GroupCardLinks,
    private val resources: ResourceHelper,
    private val clipboardManager: ClipboardManager,
    private val analytics: FlipcashAnalytics,
) : ViewModel() {

    data class State(
        /** Approve new members. */
        val approve: Boolean = false,
        /**
         * The invite link the card shows while [approve] is off. [InviteLinkState.None] is a card
         * with no link yet, which the next tap fills.
         */
        val invite: InviteLinkState = InviteLinkState.Loading,
        /** The plain link the card shows while [approve] is on. */
        val plainUrl: String? = null,
    ) {
        val creating: Boolean get() = !approve && invite == InviteLinkState.Creating

        /** Revoke and Make New Link is there only for an invite link that exists. */
        val canRevoke: Boolean get() = !approve && invite is InviteLinkState.Ready
    }

    sealed interface Effect {
        /** The Invite People sheet on the current invite link. */
        data object ShareInvite : Effect

        /** The Invite People sheet on the plain link, as a member gets it. */
        data object ShareJoin : Effect

        /** The group card, whose link [GroupCardLinks] now holds. */
        data object ShowCard : Effect

        data object OpenManageInvites : Effect

        /** The plain link is on the clipboard; the conversation copies it, with its toast. */
        data object CopyJoin : Effect

        data object Close : Effect
    }

    private enum class Action { Share, Copy, Card }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    private var chatId: ChatId? = null
    private var current: HostInvite? = null
    private var pending: Job? = null

    fun open(chatId: ChatId) {
        if (this.chatId == chatId) return
        this.chatId = chatId
        current = hostInvites.current(chatId)
        _state.update {
            State(
                approve = it.approve,
                invite = current?.toState() ?: InviteLinkState.Loading,
                plainUrl = Linkify.groupChatInvite(chatId),
            )
        }
        viewModelScope.launch {
            approveStore.observe(chatId).collect { on -> _state.update { it.copy(approve = on) } }
        }
        viewModelScope.launch { refresh(chatId) }
    }

    /** Shows the current link, or an empty card when there is none. Nothing is made on open. */
    private suspend fun refresh(chatId: ChatId) {
        val loaded = hostInvites.load(chatId)
        if (loaded.exceptionOrNull()?.isLostInviteAccess() == true) {
            hostInvites.revokeAccess(chatId)
            _effects.send(Effect.Close)
            return
        }
        // A pending tap owns the card until it lands.
        if (pending?.isActive == true) return
        current = hostInvites.current(chatId)
        _state.update { it.copy(invite = current?.toState() ?: InviteLinkState.None) }
    }

    fun onApproveChanged(on: Boolean) {
        val chatId = chatId ?: return
        _state.update { it.copy(approve = on) }
        viewModelScope.launch { approveStore.set(chatId, on) }
    }

    fun onShare() = act(Action.Share)

    fun onCopy() = act(Action.Copy)

    fun onShowCard() = act(Action.Card)

    fun onManageInvites() {
        _effects.trySend(Effect.OpenManageInvites)
    }

    private fun act(action: Action) {
        val chatId = chatId ?: return
        if (pending?.isActive == true) return
        if (_state.value.approve) {
            plain(chatId, action)
            return
        }
        pending = viewModelScope.launch {
            val url = ensureInvite(chatId) ?: return@launch
            when (action) {
                Action.Share -> _effects.send(Effect.ShareInvite)
                Action.Copy -> clipboardManager.setSensitiveText(
                    text = url,
                    label = resources.getString(R.string.title_clipboardLabelGroupInviteLink),
                )
                Action.Card -> {
                    val ready = _state.value.invite as? InviteLinkState.Ready ?: return@launch
                    cardLinks.put(chatId, GroupCardLinks.Link(url, isInvite = true, expiresAt = ready.expiresAt))
                    _effects.send(Effect.ShowCard)
                }
            }
        }
    }

    private fun plain(chatId: ChatId, action: Action) {
        val url = _state.value.plainUrl ?: return
        when (action) {
            Action.Share -> _effects.trySend(Effect.ShareJoin)
            Action.Copy -> _effects.trySend(Effect.CopyJoin)
            Action.Card -> {
                cardLinks.put(chatId, GroupCardLinks.Link(url, isInvite = false))
                _effects.trySend(Effect.ShowCard)
            }
        }
    }

    /**
     * The current invite link's URL, made with the default settings when there is none (Open 9).
     * Null when no link came of it; the error has been shown.
     */
    private suspend fun ensureInvite(chatId: ChatId): String? {
        (_state.value.invite as? InviteLinkState.Ready)?.let { return it.url }
        _state.update { it.copy(invite = InviteLinkState.Creating) }
        // A load that failed could be hiding a link that exists; make none, or this would add a second.
        val loaded = hostInvites.load(chatId)
        loaded.exceptionOrNull()?.let { error ->
            _state.update { it.copy(invite = InviteLinkState.None) }
            if (error.isLostInviteAccess()) {
                hostInvites.revokeAccess(chatId)
                _effects.send(Effect.Close)
            } else {
                showInviteLinksNotLoaded(resources)
            }
            return null
        }
        hostInvites.current(chatId)?.let { found ->
            current = found
            return settle(found)
        }
        return when (val result = hostInvites.currentOrCreate(chatId).also { analytics.trackInviteLinkCreated(it) }) {
            is InviteCreation.Created -> {
                current = result.invite
                settle(result.invite) ?: run { failed(); null }
            }
            InviteCreation.TooMany -> {
                _state.update { it.copy(invite = InviteLinkState.None) }
                showTooManyInviteLinks(
                    resources,
                    onManageInvites = { _effects.trySend(Effect.OpenManageInvites) },
                )
                null
            }
            InviteCreation.NotAvailable -> {
                _state.update { it.copy(invite = InviteLinkState.None) }
                // 1e (Open 10): its button turns Approve new members on, so the host isn't sent
                // back here on the next open, and shares the plain link it now shows.
                showInviteLinksNotAvailable(
                    resources,
                    onShareJoinLink = {
                        onApproveChanged(true)
                        _effects.trySend(Effect.ShareJoin)
                    },
                )
                null
            }
            is InviteCreation.Failed -> {
                failed()
                null
            }
        }
    }

    private fun settle(invite: HostInvite): String? {
        val link = invite.toState()
        _state.update { it.copy(invite = link) }
        return (link as? InviteLinkState.Ready)?.url
    }

    private fun failed() {
        _state.update { it.copy(invite = InviteLinkState.None) }
        showInviteLinkNotCreated(resources)
    }

    /**
     * Revoke and Make New Link: surface 2's revoke confirmation, then an empty card until the next
     * tap makes a new current link.
     */
    fun onRevoke() {
        val chatId = chatId ?: return
        val invite = current ?: return
        showRevokeInviteLink(resources) {
            viewModelScope.launch {
                hostInvites.revoke(chatId, invite.invite.id)
                    .onSuccess {
                        current = null
                        _state.update { it.copy(invite = InviteLinkState.None) }
                    }
                    .onFailure { error ->
                        if (error.isLostInviteAccess()) {
                            hostInvites.revokeAccess(chatId)
                            _effects.send(Effect.Close)
                        } else {
                            BottomBarManager.showError(
                                title = resources.getString(R.string.title_inviteLinkNotRevoked),
                                message = resources.getString(R.string.description_inviteLinkNotCreated),
                            )
                        }
                    }
            }
        }
    }

    private fun HostInvite.toState(): InviteLinkState =
        url?.let { InviteLinkState.Ready(it, invite.expiresAt, invite.useCount, invite.maxUses) }
            ?: InviteLinkState.None
}
