package com.flipcash.app.messenger.internal.invites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.messenger.internal.trackInviteLinkCreated
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ApproveNewMembersStore
import com.flipcash.shared.chat.HostInvite
import com.flipcash.shared.chat.HostInvites
import com.flipcash.shared.chat.HostInvites.Companion.isLostInviteAccess
import com.flipcash.shared.chat.InviteCreation
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.vibration.Vibrator
import com.getcode.view.PaymentSuccessHoldDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Frame 1m: the host's link card, Approve New Members and Manage Invites (spec surface 1).
 *
 * Off, the card is the current invite link, made on the first Share Link rather than on open. On,
 * it is the plain `/chat/{uuid}` link. The switch only picks which one the card shows; it revokes
 * nothing.
 *
 * What needs navigation is an [Effect] for the sheet, which has the navigators.
 */
@HiltViewModel
internal class InviteHostSheetViewModel @Inject constructor(
    private val hostInvites: HostInvites,
    private val approveStore: ApproveNewMembersStore,
    private val resources: ResourceHelper,
    private val analytics: FlipcashAnalytics,
    private val vibrator: Vibrator,
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
        /** This tap made the link; the button shows its checkmark while the sheet holds the new URL. */
        val success: Boolean = false,
    ) {
        val creating: Boolean get() = !approve && invite == InviteLinkState.Creating
    }

    sealed interface Effect {
        /** The Invite People sheet on the current invite link. */
        data object ShareInvite : Effect

        /** The Invite People sheet on the plain link, as a member gets it. */
        data object ShareJoin : Effect

        data object OpenManageInvites : Effect

        data object Close : Effect
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    private var chatId: ChatId? = null
    private var pending: Job? = null

    fun open(chatId: ChatId) {
        if (this.chatId == chatId) return
        this.chatId = chatId
        _state.update {
            State(
                approve = it.approve,
                invite = hostInvites.current(chatId)?.toState() ?: InviteLinkState.Loading,
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
        _state.update { it.copy(invite = hostInvites.current(chatId)?.toState() ?: InviteLinkState.None) }
    }

    fun onApproveChanged(on: Boolean) {
        val chatId = chatId ?: return
        _state.update { it.copy(approve = on) }
        viewModelScope.launch { approveStore.set(chatId, on) }
    }

    fun onManageInvites() {
        _effects.trySend(Effect.OpenManageInvites)
    }

    fun onShare() {
        val chatId = chatId ?: return
        if (pending?.isActive == true) return
        if (_state.value.approve) {
            _effects.trySend(Effect.ShareJoin)
            return
        }
        pending = viewModelScope.launch {
            val ensured = ensureInvite(chatId) ?: return@launch
            if (ensured.created) {
                // Show the new URL and the checkmark for the standard success hold before the full
                // sheet takes over. An existing link opens it at once.
                _state.update { it.copy(success = true) }
                vibrator.vibrate()
                delay(PaymentSuccessHoldDuration)
            }
            _effects.send(Effect.ShareInvite)
        }
    }

    /**
     * The current invite link's URL, made with the default settings when there is none (Open 9).
     * Null when no link came of it; the error has been shown.
     */
    private suspend fun ensureInvite(chatId: ChatId): Ensured? {
        (_state.value.invite as? InviteLinkState.Ready)?.let { return Ensured(it.url, created = false) }
        _state.update { it.copy(invite = InviteLinkState.Creating) }
        vibrator.tick()
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
        hostInvites.current(chatId)?.let { return settle(it)?.let { url -> Ensured(url, created = false) } }
        return when (val result = hostInvites.currentOrCreate(chatId).also { analytics.trackInviteLinkCreated(it) }) {
            is InviteCreation.Created -> settle(result.invite)?.let { Ensured(it, created = true) } ?: run { failed(); null }
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
                // 1e: its button shares the plain link this once. Approve New Members stays as it
                // was; only the host's tap on the switch turns it on.
                showInviteLinksNotAvailable(
                    resources,
                    onShareJoinLink = { _effects.trySend(Effect.ShareJoin) },
                )
                null
            }
            is InviteCreation.Failed -> {
                failed()
                null
            }
        }
    }

    /** [created] is true when this tap made the link, rather than finding one. */
    private data class Ensured(val url: String, val created: Boolean)

    private fun settle(invite: HostInvite): String? {
        val link = invite.toState()
        _state.update { it.copy(invite = link) }
        return (link as? InviteLinkState.Ready)?.url
    }

    private fun failed() {
        _state.update { it.copy(invite = InviteLinkState.None) }
        showInviteLinkNotCreated(resources)
    }

    private fun HostInvite.toState(): InviteLinkState =
        url?.let { InviteLinkState.Ready(it, invite.expiresAt, invite.useCount, invite.maxUses) }
            ?: InviteLinkState.None
}
