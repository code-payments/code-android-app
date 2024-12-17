package xyz.flipchat.app.features.chat.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastForEachIndexed
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.ui.components.chat.messagecontents.MessageControlAction
import xyz.flipchat.app.R

internal data class MessageActionContextSheet(val actions: List<MessageControlAction>) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        Column(
            modifier = Modifier
                .background(Color(0xFF171921))
                .padding(top = CodeTheme.dimens.inset)
                .navigationBarsPadding()
        ) {
            actions.fastForEachIndexed { index, action ->
                Text(
                    text = when (action) {
                        is MessageControlAction.Copy -> stringResource(R.string.action_copyMessage)
                        is MessageControlAction.Delete -> stringResource(R.string.action_deleteMessage)
                        is MessageControlAction.RemoveUser -> stringResource(
                            R.string.action_removeUser,
                            action.name
                        )

                        is MessageControlAction.ReportUserForMessage -> stringResource(R.string.action_report)
                        is MessageControlAction.MuteUser -> stringResource(
                            R.string.action_muteUser,
                            action.name
                        )

                        is MessageControlAction.Reply -> stringResource(R.string.action_reply)
                    },
                    style = CodeTheme.typography.textMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navigator.hide()
                            action.onSelect()
                        }
                        .padding(
                            horizontal = CodeTheme.dimens.inset,
                            vertical = CodeTheme.dimens.grid.x3
                        )
                )
                if (index < actions.lastIndex) {
                    Divider(color = White05)
                }
            }
        }
    }
}