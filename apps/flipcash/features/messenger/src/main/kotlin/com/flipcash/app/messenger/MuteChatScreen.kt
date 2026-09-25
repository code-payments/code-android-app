package com.flipcash.app.messenger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.messenger.internal.MuteChatViewModel
import com.flipcash.app.messenger.internal.screens.MuteChatSheet
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.chat.ui.rememberIsMuted
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher

/**
 * The mute sheet for [chatId], behind `AppRoute.Messaging.MuteChat`, wherever it was opened from:
 * a chat profile, a group profile, or a swipe on the chat list. [chatType] is for the mute events
 * only; the requests themselves are keyed by id.
 */
@Composable
fun MuteChatScreen(chatId: ChatId, chatType: ChatType) {
    val viewModel = hiltViewModel<MuteChatViewModel>()
    val viewerStates = remember(viewModel, chatId) { viewModel.observeViewerState(chatId) }
    val initial = remember(viewModel, chatId) { viewModel.currentViewerState(chatId) }
    val viewerState by viewerStates.collectAsStateWithLifecycle(initialValue = initial)
    // Exit through the sheet so it animates down rather than having its scene deleted mid-frame.
    val dismissSheet = LocalBottomSheetDismissDispatcher.current

    MuteChatSheet(
        // Asked per composition rather than passed in as a boolean the caller computed: a timed
        // mute lapses with nothing sent to say so, and the sheet's unmute row is what would
        // otherwise be left standing over a chat that is already audible again.
        isMuted = rememberIsMuted(viewerState),
        // Dismissed on the tap rather than on the result: the request is fire-and-forget from here,
        // and a failure is reported by the error bar, which draws over whatever is on screen by then.
        onMute = {
            viewModel.mute(chatId, chatType, it)
            dismissSheet()
        },
        onUnmute = {
            viewModel.unmute(chatId, chatType)
            dismissSheet()
        },
        onDismiss = dismissSheet,
    )
}
