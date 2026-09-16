package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.features.messenger.R

/**
 * The one thing a group's profile can act on.
 *
 * Its action is a [ChatViewModel.Event] rather than an event of the profile's own, because the
 * group is the conversation's — the leave has to clear the membership the transcript is reading,
 * and there is one view model holding it.
 */
internal data object LeaveChat : FullMenuItem<ChatViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.AutoMirrored.Outlined.Logout)

    override val name: String
        @Composable get() = stringResource(R.string.title_leaveChat)

    override val action: ChatViewModel.Event = ChatViewModel.Event.LeaveChat
}
