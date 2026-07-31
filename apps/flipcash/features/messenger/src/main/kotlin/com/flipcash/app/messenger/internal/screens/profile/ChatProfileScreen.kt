package com.flipcash.app.messenger.internal.screens.profile

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.menu.MenuList
import com.flipcash.app.messenger.internal.screens.components.ParticipantAvatar
import com.flipcash.features.messenger.R
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import com.getcode.util.DateUtils
import com.getcode.view.LoadingSuccessState
import kotlin.time.Instant


@Composable
internal fun ChatProfileScreen(viewModel: ChatProfileViewModel) {
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    CodeScaffold(
        topBar = {
            AppBarWithTitle(backButton = true, onBackIconClicked = { flowNavigator.back() })
        },
    ) { innerPadding ->
        MenuList(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            items = state.menuItems,
            header = {
                ProfileHeader(
                    participant = state.participant,
                    joinDate = state.joinDate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = CodeTheme.dimens.grid.x7),
                )
            },
            onItemClick = { viewModel.dispatchEvent(it.action) },
            endSlot = { item ->
                val loading = item.action == ChatProfileViewModel.Event.BlockUser &&
                    state.processingState.state == LoadingSuccessState.State.Loading
                if (loading) {
                    CodeCircularProgressIndicator(
                        strokeWidth = CodeTheme.dimens.thickBorder,
                        color = CodeTheme.colors.textSecondary,
                        modifier = Modifier.size(CodeTheme.dimens.staticGrid.x5),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = CodeTheme.colors.textSecondary,
                    )
                }
            },
        )
    }
}

@Composable
private fun ProfileHeader(
    participant: ChatParticipant?,
    joinDate: Instant?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ParticipantAvatar(
            participant = participant,
            modifier = Modifier
                .size(CodeTheme.dimens.staticGrid.x17)
                .clip(CircleShape),
        )
        Text(
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
            text = participant?.displayName.orEmpty(),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        joinDate?.let { instant ->
            Text(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                text = stringResource(
                    R.string.subtitle_joinedDate,
                    DateUtils.getDate(instant.toEpochMilliseconds(), "MMMM yyyy"),
                ),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}
