package com.flipcash.app.messenger.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.core.toast.SystemToastController
import com.flipcash.features.messenger.R
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.QueryOptions
import com.flipcash.shared.chat.GroupRosters
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.SilenceState
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.trace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The host's moderation actions on one member, shared by the places that offer them: a message's
 * selection bar, a member's profile menu, the Members list swipe and the member sheet. Held apart
 * from the chat's view models so neither needs a new dependency and all ask the same question with
 * the same words.
 *
 * Also holds which members are restricted, for the swipe and the sheet: the roster does not say,
 * so a host's screens read it once with [loadRestrictions] and apply [GroupRestrictionWrites] on top.
 */
@HiltViewModel
internal class MemberModerationViewModel @Inject constructor(
    private val chatController: ChatController,
    private val resources: ResourceHelper,
    private val groupRosters: GroupRosters,
    private val analytics: FlipcashAnalytics,
    private val toastController: SystemToastController,
    private val restrictionWrites: GroupRestrictionWrites,
) : ViewModel() {

    private val _restrictions = MutableStateFlow<Map<ID, SilenceState>>(emptyMap())

    /** Each restricted member of the chat last passed to [loadRestrictions], by user id. */
    val restrictions: StateFlow<Map<ID, SilenceState>> = _restrictions.asStateFlow()

    /** Each full roster the app holds, by chat. A chat never loaded is absent. */
    val rosters: StateFlow<Map<ChatId, List<ChatMember>>> get() = groupRosters.state

    /** [chatId]'s roster if one is already held, so a caller can skip waiting on a read. */
    fun cachedRoster(chatId: ChatId): List<ChatMember>? = groupRosters.state.value[chatId]

    /**
     * [chatId]'s roster, read first when none is held. Null when the read fails, which callers
     * treat as "membership unconfirmed".
     */
    suspend fun ensureRoster(chatId: ChatId): List<ChatMember>? {
        cachedRoster(chatId)?.let { return it }
        _rosterFailed.update { it - chatId }
        return groupRosters.load(chatId).getOrNull().also { roster ->
            if (roster == null) _rosterFailed.update { it + chatId }
        }
    }

    private val _rosterFailed = MutableStateFlow<Set<ChatId>>(emptySet())

    /** Chats whose last roster read failed, so the check reads "couldn't check" rather than pending. */
    val rosterFailed: StateFlow<Set<ChatId>> = _rosterFailed.asStateFlow()

    private var restrictionsFor: ChatId? = null

    /** Writes that landed while the read was in flight, applied over its answer. */
    private var writesDuringLoad: MutableMap<ID, SilenceState?>? = null

    init {
        viewModelScope.launch {
            restrictionWrites.writes.collect { write ->
                if (write.chatId != restrictionsFor) return@collect
                writesDuringLoad?.put(write.userId, write.silence)
                _restrictions.update { it.with(write.userId, write.silence) }
            }
        }
    }

    /**
     * Reads every page of [chatId]'s restricted users, once per view model. Only silences are kept:
     * a removed member is off the roster, so has no row or portrait to mark. A failed read leaves
     * the members unmarked and is tried again next time.
     */
    fun loadRestrictions(chatId: ChatId) {
        if (restrictionsFor == chatId) return
        restrictionsFor = chatId
        _restrictions.value = emptyMap()
        val pending = mutableMapOf<ID, SilenceState?>()
        writesDuringLoad = pending
        viewModelScope.launch {
            val loaded = mutableMapOf<ID, SilenceState>()
            var options = QueryOptions()
            while (true) {
                val page = chatController.getRestrictedUsers(chatId, options).getOrElse {
                    trace("failed to load restrictions - ${it.localizedMessage}")
                    if (restrictionsFor == chatId) restrictionsFor = null
                    writesDuringLoad = null
                    return@launch
                }
                for (user in page.users) {
                    val userId = user.userProfile.userId ?: continue
                    val silence = user.silence ?: continue
                    if (user.ban == null) loaded[userId] = silence
                }
                if (!page.hasMore || page.pagingToken == null) break
                options = options.copy(token = page.pagingToken)
            }
            if (restrictionsFor != chatId) return@launch
            _restrictions.value = pending.entries.fold(loaded.toMap()) { acc, (userId, silence) ->
                acc.with(userId, silence)
            }
            writesDuringLoad = null
        }
    }

    /**
     * Lifts [userId]'s restriction in [chatId] at once, with the Restricted list's toast. No confirm:
     * it gives back what Restrict took, and Restrict is a tap away.
     */
    fun lift(chatId: ChatId, userId: ID, name: String) {
        viewModelScope.launch {
            // The sheet closes on the tap and can take a scope with it before the answer lands.
            withContext(NonCancellable) {
                chatController.unsilenceUser(chatId, userId)
                    .onSuccess {
                        restrictionWrites.lifted(chatId, userId)
                        toastController.showToast(R.string.toast_unsilenced, name, replacePrevious = true)
                    }
                    .onFailure {
                        trace("failed to lift restriction - ${it.localizedMessage}")
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_failedToUnsilence),
                            message = resources.getString(R.string.error_description_failedToUnsilence),
                        )
                    }
            }
        }
    }

    /**
     * Asks the host to confirm removing [userId] from [chatId] with the app's bottom-bar alert, the
     * one Block uses. Confirming bans them: the contract has no kick that lets them back in, which
     * is why the subtitle says so. On success the member leaves the held roster at once; the other members hear of it as a
     * count-only `MembershipChanged`, which makes [GroupRosters] read the roster again.
     */
    fun confirmRemove(chatId: ChatId, userId: ID, firstName: String, groupTitle: String) {
        BottomBarManager.showAlert(
            title = resources.getString(R.string.prompt_title_removeMember, firstName),
            message = resources.getString(R.string.prompt_description_removeMember, groupTitle),
            actions = listOf(
                BottomBarAction(text = resources.getString(R.string.action_removeMember)) {
                    remove(chatId, userId)
                },
            ),
            showCancel = true,
        )
    }

    private fun remove(chatId: ChatId, userId: ID) {
        viewModelScope.launch {
            // The alert closes on the tap and this scope can go with the screen before the answer lands.
            withContext(NonCancellable) {
                chatController.banUser(chatId, userId).also {
                    analytics.trackMemberRemoved(it)
                }.onSuccess {
                    groupRosters.remove(chatId, userId)
                }.onFailure {
                    trace("failed to remove member - ${it.localizedMessage}")
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_failedToRemoveMember),
                        message = resources.getString(R.string.error_description_failedToRemoveMember),
                    )
                }
            }
        }
    }
}

private fun Map<ID, SilenceState>.with(userId: ID, silence: SilenceState?): Map<ID, SilenceState> =
    toMutableMap().apply { if (silence != null) put(userId, silence) else remove(userId) }
