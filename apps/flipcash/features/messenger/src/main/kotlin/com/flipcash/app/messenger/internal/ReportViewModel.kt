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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Filing the report the flow has collected.
 *
 * Scoped to the flow rather than to either step, because the reason is chosen on one screen and —
 * for [ReportReason.Other] — described on the next, and the submit outlives the step that asked for
 * it. Previously this lived in the chat's view model, which tied a report to the conversation it
 * happened to be opened from and kept a half-written one in chat state.
 *
 * The flow stays up until the report has been acknowledged. It used to close in the same frame the
 * submit was called, which left the confirmation to land on whatever was behind it — telling
 * someone their report was sent on a screen that had nothing to do with reporting, and saying
 * nothing at all if the send failed.
 */
@HiltViewModel
internal class ReportViewModel @Inject constructor(
    private val reporting: ReportingController,
    private val resources: ResourceHelper,
) : ViewModel() {

    private val _submitting = MutableStateFlow(false)

    /** True while the report is in flight, so the button can say so rather than look ignored. */
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    // Buffered rather than a shared flow: this fires once, from a bottom bar callback that can
    // outlive the composition collecting it, and losing it would strand the flow open.
    private val _confirmed = Channel<Unit>(Channel.BUFFERED)

    /**
     * Emits once the report has been acknowledged, which is what closes the flow.
     *
     * Dismissing the confirmation, not the send returning: the acknowledgement is the point of
     * staying, so leaving before it is read would waste it.
     */
    val confirmed: Flow<Unit> = _confirmed.receiveAsFlow()

    /** [details] is the reporter's own words, and only [ReportReason.Other] collects them. */
    fun submit(subject: ReportSubject, reason: ReportReason, details: String?) {
        // A second press while the first is in flight would file the same report twice and answer
        // with two confirmations stacked on each other.
        if (_submitting.value) return

        val target = when (subject) {
            is ReportSubject.User -> ReportTarget.User(subject.userId)
            is ReportSubject.Chat -> ReportTarget.Chat(subject.chatId)
            is ReportSubject.Message -> ReportTarget.Message(
                chatId = subject.chatId,
                messageId = subject.messageId,
            )
        }

        viewModelScope.launch {
            _submitting.value = true
            reporting.report(target, ReportDescription.build(reason, details))
                .onSuccess {
                    // The contract makes a duplicate report a no-op that answers OK, so this says
                    // the same thing whether or not the report was the first one. That is the
                    // intended reading: telling someone they had already reported this would be
                    // answering a question they did not ask.
                    BottomBarManager.showSuccess(
                        title = resources.getString(R.string.prompt_title_reportSubmitted),
                        message = resources.getString(R.string.prompt_description_reportSubmitted),
                        onDismiss = { _confirmed.trySend(Unit) },
                    )
                }
                .onFailure {
                    trace("failed to report - ${it.localizedMessage}")
                    // No confirmation, so the flow stays where it is and the reason is still
                    // picked — the retry is one press, not the whole flow again.
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_failedToReport),
                        message = resources.getString(R.string.error_description_failedToReport),
                    )
                }
            _submitting.value = false
        }
    }
}
