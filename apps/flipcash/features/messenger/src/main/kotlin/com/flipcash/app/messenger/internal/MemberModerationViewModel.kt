package com.flipcash.app.messenger.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.features.messenger.R
import com.flipcash.services.controllers.ChatController
import com.flipcash.shared.chat.GroupRosters
import com.flipcash.services.models.chat.ChatId
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.trace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The host's Remove action, shared by the two places that offer it: a message's selection bar and a
 * member's profile menu. Held apart from the chat's view models so neither needs a new dependency
 * and both ask the same question with the same words.
 */
@HiltViewModel
internal class MemberModerationViewModel @Inject constructor(
    private val chatController: ChatController,
    private val resources: ResourceHelper,
    private val groupRosters: GroupRosters,
    private val analytics: FlipcashAnalytics,
) : ViewModel() {

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
