package com.flipcash.app.messenger.internal.invites

import android.content.ClipboardManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.core.extensions.setSensitiveText
import com.flipcash.app.messenger.internal.trackInviteLinkCreated
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.GroupCardLinks
import com.flipcash.shared.chat.HostInvite
import com.flipcash.shared.chat.HostInvites
import com.flipcash.shared.chat.HostInvites.Companion.isLostInviteAccess
import com.flipcash.shared.chat.InviteCreation
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
import kotlin.time.Instant

/** The invite link the share sheet is showing (frames 1b and 1f). */
internal sealed interface InviteLinkState {
    /** Fetching the invite the sheet was opened on. */
    data object Loading : InviteLinkState

    /** Nothing to show: the sheet is about to close. */
    data object None : InviteLinkState

    /** Frame 1b: the tap on Share Invite Link is making the link. */
    data object Creating : InviteLinkState

    /**
     * Frame 1f. The link is as good as the chat key, so [toString] leaves it out and nothing
     * here ever logs it.
     */
    class Ready(
        val url: String,
        val expiresAt: Instant?,
        val useCount: Long,
        val maxUses: Long,
    ) : InviteLinkState {
        override fun toString(): String = "Ready(useCount=$useCount, maxUses=$maxUses)"
    }
}

/**
 * Backs the invite variant of the share-to-chats sheet: the host's current link, or one invite
 * picked in Manage Invites. The sheet opens from the tap on Share Invite Link, so opening it
 * with no current link makes one (spec Decision 9, Open 9); opening it for a specific invite
 * never creates.
 *
 * What happens after a tap is an [Effect] for the sheet to carry out, because sharing needs the
 * share controller and the group's picture, which only the composition has.
 */
@HiltViewModel
internal class InviteLinkViewModel @Inject constructor(
    private val hostInvites: HostInvites,
    private val resources: ResourceHelper,
    private val clipboardManager: ClipboardManager,
    private val analytics: FlipcashAnalytics,
    private val cardLinks: GroupCardLinks,
) : ViewModel() {

    enum class Action { Share, Copy, Send }

    sealed interface Effect {
        /** Hand [url] to the system share sheet. */
        class Share(val url: String) : Effect {
            override fun toString(): String = "Share"
        }

        /** Send [url] to the chats picked in Recent Chats. */
        class Send(val url: String) : Effect {
            override fun toString(): String = "Send"
        }

        /** The link is on the clipboard. */
        data object Copied : Effect

        /** Frame 1e's offer: switch to the plain join link. */
        data object OpenJoinLink : Effect

        /** Frame 1c's offer: the host revokes a link in Manage Invites. */
        data object OpenManageInvites : Effect

        /** There is nothing to show, or the error that said so is done with; go back. */
        data object Close : Effect
    }

    data class State(val link: InviteLinkState = InviteLinkState.Loading)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    private var chatId: ChatId? = null
    private var specific: String? = null
    private var job: Job? = null

    /**
     * Hands the link showing to the group card about to open, in memory (the card's route is
     * saved). False while there is no link yet, so the sheet opens no card.
     */
    fun handOverCard(): Boolean {
        val chatId = chatId ?: return false
        val link = _state.value.link as? InviteLinkState.Ready ?: return false
        cardLinks.put(chatId, GroupCardLinks.Link(link.url, isInvite = true, expiresAt = link.expiresAt))
        return true
    }

    /**
     * Starts on [chatId]'s current link, or on the invite [inviteIdHex] when Manage Invites picked
     * one. Opening on what is already showing does nothing; a different invite reloads.
     */
    fun open(chatId: ChatId, inviteIdHex: String?) {
        if (this.chatId == chatId && specific == inviteIdHex) return
        this.chatId = chatId
        specific = inviteIdHex
        job?.cancel()
        val known = if (inviteIdHex == null) hostInvites.current(chatId)?.toState() else null
        _state.update {
            State(
                link = known
                    // No current link: the tap on the row made this sheet to make one.
                    ?: if (inviteIdHex == null) InviteLinkState.Creating else InviteLinkState.Loading,
            )
        }
        job = viewModelScope.launch { load(chatId, inviteIdHex) }
    }

    private suspend fun load(chatId: ChatId, inviteIdHex: String?) {
        val loaded = hostInvites.load(chatId)
        loaded.exceptionOrNull()?.let { error ->
            if (error.isLostInviteAccess()) {
                // The viewer is no longer allowed to invite: back to the profile, as Manage Invites does.
                hostInvites.revokeAccess(chatId)
                _effects.send(Effect.Close)
                return
            }
        }
        if (inviteIdHex != null) {
            val picked = hostInvites.state.value[chatId].orEmpty().firstOrNull { it.invite.id.toHex() == inviteIdHex }
            val link = picked?.toState()
            if (link is InviteLinkState.Ready) {
                _state.update { it.copy(link = link) }
            } else {
                _state.update { it.copy(link = InviteLinkState.None) }
                _effects.send(Effect.Close)
            }
            return
        }
        if (loaded.isFailure) {
            // A load that failed could be hiding a link that exists; make none, or this would add a second.
            _state.update { it.copy(link = InviteLinkState.None) }
            showInviteLinksNotLoaded(resources) { _effects.trySend(Effect.Close) }
            return
        }
        val current = hostInvites.current(chatId)?.toState()
        if (current != null) {
            _state.update { it.copy(link = current) }
            return
        }
        _state.update { it.copy(link = InviteLinkState.Creating) }
        create(chatId)
    }

    private suspend fun create(chatId: ChatId) {
        when (val result = hostInvites.currentOrCreate(chatId).also { analytics.trackInviteLinkCreated(it) }) {
            is InviteCreation.Created -> {
                val link = result.invite.toState()
                _state.update { it.copy(link = link) }
                if (link !is InviteLinkState.Ready) failed()
            }
            InviteCreation.TooMany -> {
                _state.update { it.copy(link = InviteLinkState.None) }
                showTooManyInviteLinks(
                    resources,
                    onManageInvites = {
                        _effects.trySend(Effect.OpenManageInvites)
                    },
                    onCancel = { _effects.trySend(Effect.Close) },
                )
            }
            InviteCreation.NotAvailable -> {
                _state.update { it.copy(link = InviteLinkState.None) }
                showInviteLinksNotAvailable(
                    resources,
                    // 1e: share the plain link this once, leaving Approve New Members off.
                    onShareJoinLink = { _effects.trySend(Effect.OpenJoinLink) },
                    onCancel = { _effects.trySend(Effect.Close) },
                )
            }
            is InviteCreation.Failed -> failed()
        }
    }

    private fun failed() {
        _state.update { it.copy(link = InviteLinkState.None) }
        showInviteLinkNotCreated(resources) { _effects.trySend(Effect.Close) }
    }

    fun onShare() = act(Action.Share)

    fun onCopy() = act(Action.Copy)

    fun onSend() = act(Action.Send)

    // The tiles are disabled until the link is ready, so a tap that gets here without one is a
    // stale gesture; it has nothing to act on.
    private fun act(action: Action) {
        val link = _state.value.link as? InviteLinkState.Ready ?: return
        perform(action, link.url)
    }

    private fun perform(action: Action, url: String) {
        when (action) {
            Action.Share -> _effects.trySend(Effect.Share(url))
            Action.Send -> _effects.trySend(Effect.Send(url))
            Action.Copy -> {
                clipboardManager.setSensitiveText(
                    text = url,
                    label = resources.getString(R.string.title_clipboardLabelGroupInviteLink),
                )
                _effects.trySend(Effect.Copied)
            }
        }
    }

    private fun HostInvite.toState(): InviteLinkState =
        url?.let { InviteLinkState.Ready(it, invite.expiresAt, invite.useCount, invite.maxUses) }
            ?: InviteLinkState.None
}

/** An invite's id as hex: what a nav step carries in place of the link. The id is not the secret. */
internal fun com.flipcash.services.models.chat.InviteId.toHex(): String =
    bytes.joinToString("") { "%02x".format(it) }
