package com.flipcash.app.messenger.internal.invites

import android.content.ClipboardManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.extensions.setText
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.HostInvite
import com.flipcash.shared.chat.HostInvites
import com.flipcash.shared.chat.InviteCreation
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

/** The invite link the share sheet is showing (frames 1b and 1f). */
internal sealed interface InviteLinkState {
    /** Fetching the host's invites, to see whether a current link exists. */
    data object Loading : InviteLinkState

    /** No current link yet. The first Share or Copy tap makes one (spec Decision 9). */
    data object None : InviteLinkState

    /** Frame 1b: the first tap is making the link. */
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
 * picked in Manage Invites, and the first-tap creation of a current link when there is none.
 *
 * What happens after a tap is an [Effect] for the sheet to carry out, because sharing needs the
 * share controller and the group's picture, which only the composition has.
 */
@HiltViewModel
internal class InviteLinkViewModel @Inject constructor(
    private val hostInvites: HostInvites,
    private val resources: ResourceHelper,
    private val clipboardManager: ClipboardManager,
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

        /** The invite the sheet was opened on cannot be shown; go back. */
        data object Close : Effect
    }

    data class State(
        val link: InviteLinkState = InviteLinkState.Loading,
        /** `CreateInvite` answered DENIED: Share and Copy show 1e again instead of calling it. */
        val unavailable: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    private var chatId: ChatId? = null
    private var specific: String? = null
    private var loaded = false

    /** Starts on [chatId]'s current link, or on the invite [inviteIdHex] when Manage Invites picked one. */
    fun open(chatId: ChatId, inviteIdHex: String?) {
        if (this.chatId == chatId && specific == inviteIdHex) return
        this.chatId = chatId
        specific = inviteIdHex
        loaded = false
        _state.update { State() }
        viewModelScope.launch { load(chatId) }
    }

    private suspend fun load(chatId: ChatId): Boolean {
        loaded = hostInvites.load(chatId).isSuccess
        val link = specific
            ?.let { id -> hostInvites.state.value[chatId].orEmpty().firstOrNull { it.invite.id.toHex() == id } }
            ?: hostInvites.current(chatId).takeIf { specific == null }
        _state.update { it.copy(link = link?.toState() ?: InviteLinkState.None) }
        if (specific != null && link?.canShare != true) {
            _effects.send(Effect.Close)
        }
        return loaded
    }

    fun onShare() = act(Action.Share)

    fun onCopy() = act(Action.Copy)

    /** The sheet's Send, which makes the link first when a chat was picked before there was one. */
    fun onSend() = act(Action.Send)

    private fun act(action: Action) {
        val chatId = chatId ?: return
        val current = _state.value
        when {
            current.unavailable -> showInviteLinksNotAvailable(resources) { _effects.trySend(Effect.OpenJoinLink) }
            current.link is InviteLinkState.Ready -> perform(action, current.link.url)
            current.link is InviteLinkState.None && specific == null -> create(chatId, action)
        }
    }

    private fun create(chatId: ChatId, action: Action) {
        _state.update { it.copy(link = InviteLinkState.Creating) }
        viewModelScope.launch {
            // A load that failed on open could be hiding a link that exists; make none until one
            // has gone through, or the tap would add a second.
            if (!loaded && !load(chatId)) {
                _state.update { it.copy(link = InviteLinkState.None) }
                showInviteLinkNotCreated(resources)
                return@launch
            }
            val ready = (_state.value.link as? InviteLinkState.Ready)
            if (ready != null) {
                perform(action, ready.url)
                return@launch
            }
            when (val result = hostInvites.currentOrCreate(chatId)) {
                is InviteCreation.Created -> {
                    val link = result.invite.toState()
                    _state.update { it.copy(link = link) }
                    (link as? InviteLinkState.Ready)?.let { perform(action, it.url) }
                }
                InviteCreation.TooMany -> {
                    _state.update { it.copy(link = InviteLinkState.None) }
                    showTooManyInviteLinks(resources)
                }
                InviteCreation.NotAvailable -> {
                    _state.update { it.copy(link = InviteLinkState.None, unavailable = true) }
                    showInviteLinksNotAvailable(resources) { _effects.trySend(Effect.OpenJoinLink) }
                }
                is InviteCreation.Failed -> {
                    _state.update { it.copy(link = InviteLinkState.None) }
                    showInviteLinkNotCreated(resources)
                }
            }
        }
    }

    private fun perform(action: Action, url: String) {
        when (action) {
            Action.Share -> _effects.trySend(Effect.Share(url))
            Action.Send -> _effects.trySend(Effect.Send(url))
            Action.Copy -> {
                clipboardManager.setText(
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
