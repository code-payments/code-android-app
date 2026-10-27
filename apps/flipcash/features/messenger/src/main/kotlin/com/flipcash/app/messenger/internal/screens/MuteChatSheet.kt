package com.flipcash.app.messenger.internal.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.flipcash.app.messenger.internal.MuteOption
import com.flipcash.app.messenger.internal.SilenceOption
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.ChoiceRow

/**
 * How long to mute this conversation for.
 *
 * One row per [MuteOption], drawn as the chats flow's other choice screen draws its rows — a card
 * per choice rather than a settings list, matching [GroupInviteSheet] and iOS' sheet of the same
 * name.
 *
 * The contract admits exactly two mute shapes, a deadline or forever, so every duration row is one
 * of the two. "Never" is the unmute: still its own request rather than a mute of zero length, and
 * it reads as a duration only because every label here names the state it produces rather than the
 * act that gets there.
 *
 * The deadline is minted on the tap rather than when the sheet opens, so a sheet left sitting still
 * means what it says when it is finally used.
 *
 * @param isMuted whether the chat is muted *right now*, which is what decides the unmute row. Read
 * live by the caller, so a mute that lapses under an open sheet drops the row with it.
 */
@Composable
internal fun MuteChatSheet(
    isMuted: Boolean,
    onMute: (MuteOption) -> Unit,
    onUnmute: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Pinned on the way out, because the store converges before the sheet has finished animating
    // away: read live, the list would gain or lose its first row while the user is still watching
    // it. Null while the sheet is idle, so a lapsing mute still drops the row.
    var pinnedOffersUnmute: Boolean? by remember { mutableStateOf(null) }
    val offersUnmute = pinnedOffersUnmute ?: isMuted

    DurationChoiceSheet(
        title = stringResource(R.string.title_muteChat),
        // Worth saying because muting here does less than the word usually promises: the push still
        // arrives and the message still lands in the thread, and only the notification is held back.
        subtitle = stringResource(R.string.subtitle_muteChat),
        onDismiss = onDismiss,
    ) {
        // Unmuting first: it is the one row that undoes what the others did, and it is only
        // here at all while there is something to undo.
        if (offersUnmute) {
            ChoiceRow(
                label = stringResource(R.string.action_muteFor_never),
                icon = rememberVectorPainter(Icons.Outlined.Notifications),
                onClick = {
                    pinnedOffersUnmute = offersUnmute
                    onUnmute()
                },
            )
        }
        for (option in MuteOption.entries) {
            ChoiceRow(
                label = stringResource(option.labelRes),
                // A clock for the rows that name a length, and the bell-slash for the one that
                // doesn't: nothing in the icon set says "8 hours", and what the timed rows have
                // in common is that they end.
                icon = rememberVectorPainter(
                    if (option.duration != null) Icons.Outlined.Schedule
                    else Icons.Outlined.NotificationsOff
                ),
                onClick = {
                    pinnedOffersUnmute = offersUnmute
                    onMute(option)
                },
            )
        }
    }
}

/**
 * The sheet both duration pickers share: a title with a close button, one line saying what the
 * choice does, and a card per row. Mute Notifications and Silence a member differ in their words
 * and their request, not in how they look.
 */
@Composable
internal fun DurationChoiceSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    rows: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        AppBarWithTitle(
            title = title,
            titleAlignment = Alignment.CenterHorizontally,
            endContent = { AppBarDefaults.Close(onClick = onDismiss) },
        )

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset),
            text = subtitle,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(top = CodeTheme.dimens.grid.x4, bottom = CodeTheme.dimens.grid.x4),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
            content = rows,
        )
    }
}

/**
 * How long to silence [firstName] in [groupTitle]. The rows are [SilenceOption]; picking one is the
 * whole action, so there is no confirm and no unsilence row here: lifting a silence lives on the
 * Silenced list.
 */
@Composable
internal fun SilenceUserSheet(
    firstName: String,
    groupTitle: String,
    onSilence: (SilenceOption) -> Unit,
    onDismiss: () -> Unit,
) {
    DurationChoiceSheet(
        title = stringResource(R.string.title_silenceUser, firstName),
        subtitle = stringResource(R.string.subtitle_silenceUser, groupTitle),
        onDismiss = onDismiss,
    ) {
        for (option in SilenceOption.entries) {
            ChoiceRow(
                label = stringResource(option.labelRes),
                icon = rememberVectorPainter(
                    if (option.duration != null) Icons.Outlined.Schedule else Icons.Outlined.Block
                ),
                onClick = { onSilence(option) },
            )
        }
    }
}
