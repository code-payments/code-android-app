package com.flipcash.app.messenger.internal.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.flipcash.app.messenger.internal.MuteOption
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.MuteState
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.ListItem
import androidx.compose.material3.Text

/**
 * How long to mute this conversation for.
 *
 * One row per [MuteOption] and nothing else, because the contract admits exactly two mute shapes —
 * a deadline, or forever — and every row here is one of the two. There is no row for unmuting: that
 * is a separate request, offered from the profile only while a mute is actually in force.
 *
 * The deadline is minted on the tap rather than when the sheet opens, so a sheet left sitting still
 * means what it says when it is finally used.
 */
@Composable
internal fun MuteChatSheet(
    onMute: (MuteState) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_muteChat),
            titleAlignment = Alignment.CenterHorizontally,
            endContent = { AppBarDefaults.Close(onClick = onDismiss) },
        )

        // Worth saying because muting here does less than the word usually promises: the push still
        // arrives and the message still lands in the thread, and only the notification is held back.
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(bottom = CodeTheme.dimens.grid.x2),
            text = stringResource(R.string.subtitle_muteChat),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        for (option in MuteOption.entries) {
            // No icon: nothing in the icon set says "8 hours", and a stand-in drawable would be
            // decoration the row has to explain around.
            ListItem(
                headline = stringResource(option.labelRes),
                icon = null,
                showChevron = false,
                onClick = { onMute(option.toMuteState()) },
            )
        }
    }
}
