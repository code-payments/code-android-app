package com.flipcash.app.messenger.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.core.toast.SystemToastController
import com.flipcash.features.messenger.R
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatId
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
 * Backs the silence sheet for one member of one group.
 *
 * Fire-and-forget like [MuteChatViewModel]: the sheet closes on the tap, and a failure is reported
 * by the error bar over whatever is on screen by then. Nothing is stored on success; the silence
 * is the silenced member's state, and the host's Silenced list applies it locally.
 */
@HiltViewModel
internal class SilenceUserViewModel @Inject constructor(
    private val chatController: ChatController,
    private val resources: ResourceHelper,
    private val toastController: SystemToastController,
    private val analytics: FlipcashAnalytics,
) : ViewModel() {

    fun silence(chatId: ChatId, userId: ID, firstName: String, option: SilenceOption) {
        viewModelScope.launch {
            // Scoped to the sheet's entry, which is gone by the time the answer lands.
            withContext(NonCancellable) {
                chatController.silenceUser(chatId, userId, option.toSilenceState()).also {
                    analytics.trackMemberSilenced(option, it)
                }.onSuccess {
                    toastController.showToast(option.toastRes, firstName, replacePrevious = true)
                }.onFailure {
                    trace("failed to silence user - ${it.localizedMessage}")
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_failedToSilence),
                        message = resources.getString(R.string.error_description_failedToSilence),
                    )
                }
            }
        }
    }
}
