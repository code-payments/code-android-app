package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import com.flipcash.app.menu.FullMenuItem
import com.flipcash.features.messenger.R

internal data object BlockUser: FullMenuItem<ChatProfileViewModel.Event>() {
    override val icon: Painter
        @Composable get() = rememberVectorPainter(Icons.Outlined.Block)

    override val name: String
        @Composable get() = stringResource(R.string.title_block)
    override val action: ChatProfileViewModel.Event = ChatProfileViewModel.Event.BlockUser
}