package com.flipcash.app.messenger

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flipcash.app.messenger.internal.SilenceUserViewModel
import com.flipcash.app.messenger.internal.screens.SilenceUserSheet
import com.flipcash.services.models.chat.ChatId
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher
import com.getcode.opencode.model.core.ID

/** The silence sheet behind `AppRoute.Messaging.SilenceUser`, opened from a message or a profile. */
@Composable
fun SilenceUserScreen(chatId: ChatId, userId: ID, firstName: String, groupTitle: String) {
    val viewModel = hiltViewModel<SilenceUserViewModel>()
    // Exit through the sheet so it animates down rather than having its scene deleted mid-frame.
    val dismissSheet = LocalBottomSheetDismissDispatcher.current

    SilenceUserSheet(
        firstName = firstName,
        groupTitle = groupTitle,
        onSilence = {
            viewModel.silence(chatId, userId, firstName, it)
            dismissSheet()
        },
        onDismiss = dismissSheet,
    )
}
