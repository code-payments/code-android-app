package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.features.messenger.R

/**
 * What a DM counterparty's profile can act on.
 *
 * Its own type rather than [ChatProfileViewModel.Event] for the same reason [GroupProfileAction]
 * is one: the rows do different kinds of thing and answer to different view models. Blocking is
 * this screen's own business, while muting is the conversation's — the chat is what gets muted,
 * not the person — so naming the actions here lets one [com.flipcash.app.menu.MenuList] carry both
 * and the screen route each tap to whichever holds it.
 */
internal sealed interface ChatProfileAction {
    data object Block : ChatProfileAction
    data object Mute : ChatProfileAction
    data object Report : ChatProfileAction
}

internal data object ReportUser : FullMenuItem<ChatProfileAction>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Flag)

    override val name: String
        @Composable get() = stringResource(R.string.title_report)

    override val action: ChatProfileAction = ChatProfileAction.Report
}

internal data object BlockUser : FullMenuItem<ChatProfileAction>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Block)

    override val name: String
        @Composable get() = stringResource(R.string.title_block)

    override val action: ChatProfileAction = ChatProfileAction.Block
}

/** The DM's mute row. Shared definition; see [MuteChatItem]. */
internal val MuteDm = MuteChatItem<ChatProfileAction>(ChatProfileAction.Mute)
