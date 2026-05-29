package com.flipcash.app.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.flipcash.core.R
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.rememberedClickable

@Composable
fun InviteContactScreen(phoneNumber: String) {
    val controller = LocalInviteController.current
    val dismiss = LocalBottomSheetDismissDispatcher.current
    val channels by controller.channels.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = CodeTheme.dimens.inset,
                vertical = CodeTheme.dimens.grid.x4,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.title_inviteContact),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
            modifier = Modifier.padding(bottom = CodeTheme.dimens.grid.x4),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            channels.forEach { channel ->
                ChannelItem(
                    channel = channel,
                    onClick = {
                        controller.launch(channel, phoneNumber)
                        dismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun ChannelItem(
    channel: InviteChannel,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .rememberedClickable(onClick = onClick)
            .padding(CodeTheme.dimens.grid.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        if (channel.icon != null) {
            AsyncImage(
                model = channel.icon,
                contentDescription = channel.label,
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x10),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = channel.label,
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x10),
                tint = CodeTheme.colors.textMain,
            )
        }
        Text(
            text = channel.label,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textMain,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
