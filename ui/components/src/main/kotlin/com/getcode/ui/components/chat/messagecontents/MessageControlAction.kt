package com.getcode.ui.components.chat.messagecontents

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.getcode.ui.components.R
import com.getcode.ui.components.contextmenu.ContextMenuAction

data class MessageControls(
    val actions: List<MessageControlAction> = emptyList()
) {
    val hasAny: Boolean
        get() = actions.isNotEmpty()
}


sealed interface MessageControlAction: ContextMenuAction {
    data class Copy(override val onSelect: () -> Unit) : MessageControlAction {
        override val isDestructive: Boolean = false
        override val delayUponSelection: Boolean = false

        override val title: String
            @Composable get() = stringResource(R.string.action_copyMessage)

        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Default.ContentCopy)
    }

    data class Reply(override val onSelect: () -> Unit) : MessageControlAction {
        override val isDestructive: Boolean = false
        override val delayUponSelection: Boolean = false

        override val title: String
            @Composable get() = stringResource(R.string.action_reply)
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.AutoMirrored.Default.Reply)
    }

    data class Tip(override val onSelect: () -> Unit) : MessageControlAction {
        override val isDestructive: Boolean = false
        override val title: String
            @Composable get() = stringResource(R.string.action_giveTip)
        override val painter: Painter
            @Composable get() = painterResource(R.drawable.ic_kin_white_small)
        override val delayUponSelection: Boolean = true
    }

    data class Delete(override val onSelect: () -> Unit) : MessageControlAction {
        override val isDestructive: Boolean = true
        override val title: String
            @Composable get() = stringResource(R.string.action_deleteMessage)
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Default.Delete)
        override val delayUponSelection: Boolean = false
    }

    data class RemoveUser(val name: String, override val onSelect: () -> Unit) :
        MessageControlAction {
        override val isDestructive: Boolean = true
        override val title: String
            @Composable get() = stringResource(R.string.action_removeUser, name)
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Default.PersonRemove)
        override val delayUponSelection: Boolean = false
    }

    data class MuteUser(val name: String, override val onSelect: () -> Unit) :
        MessageControlAction {
        override val isDestructive: Boolean = true

        override val title: String
            @Composable get() = stringResource(
                R.string.action_muteUser,
                name
            )

        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Default.VoiceOverOff)

        override val delayUponSelection: Boolean = false
    }

    data class ReportUserForMessage(val name: String, override val onSelect: () -> Unit) :
        MessageControlAction {
        override val isDestructive: Boolean = true
        override val title: String
            @Composable get() = stringResource(R.string.action_report)
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Default.Flag)
        override val delayUponSelection: Boolean = false
    }

    data class BlockUser(val name: String, override val onSelect: () -> Unit) :
        MessageControlAction {
        override val isDestructive: Boolean = true
        override val title: String
            @Composable get() = stringResource(R.string.action_blockUser)
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Default.Block)
        override val delayUponSelection: Boolean = false
    }

    data class UnblockUser(val name: String, override val onSelect: () -> Unit) :
        MessageControlAction {
        override val isDestructive: Boolean = false
        override val title: String
            @Composable get() = stringResource(R.string.action_unblockUser)
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Default.Person)
        override val delayUponSelection: Boolean = false
    }
}