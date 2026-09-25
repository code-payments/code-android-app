package com.flipcash.app.messenger.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.analytics.MuteDuration
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.ChatEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.analytics.chatResult
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.currentChatListFeed
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.trace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Backs the mute sheet for one chat, named by id rather than read off a chat flow's view model.
 *
 * The sheet is opened from the chat list as well as from the profiles, and the list has no
 * conversation behind it, so everything here is keyed by the id the route carries. Previously the
 * requests lived on `ChatViewModel`, which only the profiles could reach.
 */
@HiltViewModel
internal class MuteChatViewModel @Inject constructor(
    private val chatCoordinator: ChatCoordinator,
    private val resources: ResourceHelper,
    private val analytics: FlipcashAnalytics,
) : ViewModel() {

    /**
     * The viewer state the feed already holds for [chatId], read without waiting.
     *
     * Seeds [observeViewerState] so the sheet opens with its unmute row already decided. Starting
     * from `null` would open it without the row and then grow it by one as the store answered.
     */
    fun currentViewerState(chatId: ChatId): ViewerState? =
        chatCoordinator.currentChatListFeed()
            ?.firstOrNull { it.metadata.chatId == chatId }
            ?.metadata?.viewerState

    /**
     * Observed rather than read once: a mute made on another device arrives on the stream, and a
     * timed one can lapse under the open sheet.
     */
    fun observeViewerState(chatId: ChatId): Flow<ViewerState?> =
        chatCoordinator.observeMetadata(chatId)
            .map { it?.metadata?.viewerState }
            .distinctUntilChanged()

    /**
     * Nothing to report on success: `mute` stores the viewer state it answers with, and every
     * surface showing the mute reads that store.
     */
    fun mute(chatId: ChatId, chatType: ChatType, option: MuteOption) = request(
        errorTitle = R.string.error_title_failedToMute,
        errorDescription = R.string.error_description_failedToMute,
    ) {
        chatCoordinator.mute(chatId, option.toMuteState()).also { result ->
            analytics.track(
                ChatEvents.muted(
                    chatType = chatType.analytics,
                    duration = option.analytics,
                    state = result.analyticsState,
                    error = result.exceptionOrNull()?.chatResult,
                )
            )
        }
    }

    fun unmute(chatId: ChatId, chatType: ChatType) = request(
        errorTitle = R.string.error_title_failedToUnmute,
        errorDescription = R.string.error_description_failedToUnmute,
    ) {
        chatCoordinator.unmute(chatId).also { result ->
            analytics.track(
                ChatEvents.unmuted(
                    chatType = chatType.analytics,
                    state = result.analyticsState,
                    error = result.exceptionOrNull()?.chatResult,
                )
            )
        }
    }

    private fun request(
        errorTitle: Int,
        errorDescription: Int,
        call: suspend () -> Result<Unit>,
    ) {
        viewModelScope.launch {
            // The sheet closes on the tap, and this view model is scoped to the sheet's entry, so
            // its scope is cancelled while the request is still in flight. Cancelling there would
            // drop the answer the store is written from, and the error bar with it.
            withContext(NonCancellable) {
                call().onFailure {
                    trace("failed to change chat mute - ${it.localizedMessage}")
                    BottomBarManager.showError(
                        title = resources.getString(errorTitle),
                        message = resources.getString(errorDescription),
                    )
                }
            }
        }
    }
}

private val Result<*>.analyticsState: AnalyticsState
    get() = if (isSuccess) AnalyticsState.SUCCESS else AnalyticsState.FAILURE

private val MuteOption.analytics: MuteDuration
    get() = when (this) {
        MuteOption.OneHour -> MuteDuration.ONE_HOUR
        MuteOption.EightHours -> MuteDuration.EIGHT_HOURS
        MuteOption.OneWeek -> MuteDuration.ONE_WEEK
        MuteOption.Forever -> MuteDuration.ALWAYS
    }
