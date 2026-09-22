package com.flipcash.app.messenger.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.chat.ReportSubject
import com.flipcash.features.messenger.R
import com.flipcash.reporting.ReportDescription
import com.flipcash.reporting.ReportReason
import com.flipcash.services.controllers.ReportingController
import com.flipcash.services.models.ReportTarget
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.trace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Filing the report the flow has collected.
 *
 * Scoped to the flow rather than to either step, because the reason is chosen on one screen and —
 * for [ReportReason.Other] — described on the next, and the submit can outlive the step that asked
 * for it. Previously this lived in the chat's view model, which tied a report to the conversation
 * it happened to be opened from and kept a half-written one in chat state.
 *
 * No state of its own: both screens own their input, and the outcome is a bottom bar prompt rather
 * than something the flow renders, so there is nothing to hold across the exit.
 */
@HiltViewModel
internal class ReportViewModel @Inject constructor(
    private val reporting: ReportingController,
    private val resources: ResourceHelper,
) : ViewModel() {

    /** [details] is the reporter's own words, and only [ReportReason.Other] collects them. */
    fun submit(subject: ReportSubject, reason: ReportReason, details: String?) {
        val target = when (subject) {
            is ReportSubject.User -> ReportTarget.User(subject.userId)
            is ReportSubject.Chat -> ReportTarget.Chat(subject.chatId)
            is ReportSubject.Message -> ReportTarget.Message(
                chatId = subject.chatId,
                messageId = subject.messageId,
            )
        }

        // On the flow's scope rather than the caller's: the screen is being left in the same frame
        // this is called, so a scope tied to it would cancel the request that leaving is for.
        viewModelScope.launch {
            reporting.report(target, ReportDescription.build(reason, details))
                .onSuccess {
                    // The contract makes a duplicate report a no-op that answers OK, so this says
                    // the same thing whether or not the report was the first one. That is the
                    // intended reading: telling someone they had already reported this would be
                    // answering a question they did not ask.
                    BottomBarManager.showMessage(
                        title = resources.getString(R.string.prompt_title_reportSubmitted),
                        message = resources.getString(R.string.prompt_description_reportSubmitted),
                    )
                }
                .onFailure {
                    trace("failed to report - ${it.localizedMessage}")
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_failedToReport),
                        message = resources.getString(R.string.error_description_failedToReport),
                    )
                }
        }
    }
}
