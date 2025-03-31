package xyz.flipchat.app.features.chat.info

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Title
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.getcode.ui.core.ContextMenuAction
import xyz.flipchat.app.R


sealed interface RoomControlAction : ContextMenuAction {
    data class MessageFee(override val onSelect: () -> Unit) : RoomControlAction, ContextMenuAction.Single {
        override val isDestructive: Boolean = false
        override val delayUponSelection: Boolean = true
        override val title: String
            @Composable get() = stringResource(R.string.action_changeMessagingFee)
        override val painter: Painter
            @Composable get() = painterResource(R.drawable.ic_kin_white_small)
    }

    data class CloseRoom(override val onSelect: () -> Unit) : RoomControlAction, ContextMenuAction.Single {
        override val isDestructive: Boolean = false
        override val delayUponSelection: Boolean = true
        override val title: String
            @Composable get() = stringResource(R.string.action_closeFlipchatTemporarily)
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Outlined.Bedtime)
    }

    data class OpenRoom(override val onSelect: () -> Unit) : RoomControlAction, ContextMenuAction.Single {
        override val isDestructive: Boolean = false
        override val delayUponSelection: Boolean = true
        override val title: String
            @Composable get() = stringResource(R.string.action_reopenFlipchat)
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Outlined.LightMode)
    }

    data class LeaveRoom(override val onSelect: () -> Unit) : RoomControlAction, ContextMenuAction.Single {
        override val isDestructive: Boolean = false
        override val delayUponSelection: Boolean = true
        override val title: String
            @Composable get() = stringResource(R.string.action_leaveRoom)
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.AutoMirrored.Outlined.Logout)
    }

    data class ChangeName(override val onSelect: () -> Unit) : RoomControlAction, ContextMenuAction.Single {
        override val isDestructive: Boolean = false
        override val delayUponSelection: Boolean = true
        override val title: String
            @Composable get() = stringResource(R.string.action_changeRoomName)
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Outlined.Title)
    }

    data class ChangeDescription(override val onSelect: () -> Unit) : RoomControlAction, ContextMenuAction.Single {
        override val isDestructive: Boolean = false
        override val delayUponSelection: Boolean = true
        override val title: String
            @Composable get() = stringResource(R.string.action_changeRoomDescription)
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Outlined.Description)
    }
}