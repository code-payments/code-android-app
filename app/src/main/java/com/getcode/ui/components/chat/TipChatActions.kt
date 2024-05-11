package com.getcode.ui.components.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.getcode.LocalBetaFlags
import com.getcode.R
import com.getcode.model.MessageContent
import com.getcode.model.Verb
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton

@Composable
internal fun TipChatActions(
    contents: MessageContent.Exchange,
    showTipActions: Boolean,
    thankUser: () -> Unit,
    openMessageChat: () -> Unit
) {
    val tipChatsEnabled = LocalBetaFlags.current.tipsChatEnabled
    var thanked by remember(contents.thanked) {
        mutableStateOf(contents.thanked)
    }

    val sendThanks = {
        thanked = true
        thankUser()
    }

    if (showTipActions) {
    if (tipChatsEnabled && contents.verb is Verb.ReceivedTip) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            CodeButton(
                modifier = Modifier.weight(1f),
                enabled = !thanked,
                buttonState = if (thanked) ButtonState.Bordered else ButtonState.Filled,
                onClick = sendThanks,
                shape = CodeTheme.shapes.extraSmall,
                text = if (thanked) stringResource(R.string.action_thanked) else stringResource(
                    R.string.action_thank
                )
            )
            CodeButton(
                modifier = Modifier.weight(1f),
                buttonState = ButtonState.Filled,
                onClick = openMessageChat,
                shape = CodeTheme.shapes.extraSmall,
                text = stringResource(R.string.action_message)
            )
        }
    }
        }
}