package com.flipcash.app.tipping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.analytics.events.GroupEvents
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.features.tipping.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.ChoiceRow

/**
 * Node 10127:117987 — the two ways to start a chat, behind the Chats list's "+".
 *
 * A chooser rather than a direct push because there are now two kinds of chat to start and they
 * share no entry UI: a group is created here and then invited into, a DM is opened by resolving
 * someone's handle. Neither screen has any state of its own to carry, so this one holds no view
 * model — it pushes and is popped.
 */
@Composable
fun NewChatScreen() {
    val navigator = LocalCodeNavigator.current
    val analytics = rememberAnalytics()

    Column(modifier = Modifier.fillMaxSize()) {
        AppBarWithTitle(
            title = stringResource(R.string.title_startNewChat),
            titleAlignment = Alignment.CenterHorizontally,
            onBackIconClicked = { navigator.pop() },
        )

        Column(
            modifier = Modifier
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(top = CodeTheme.dimens.grid.x3),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            ChoiceRow(
                label = stringResource(R.string.action_createPublicGroup),
                icon = R.drawable.ic_group_3,
                onClick = {
                    analytics.track(GroupEvents.newOpened())
                    navigator.push(AppRoute.Messaging.NewGroup)
                },
            )
            ChoiceRow(
                label = stringResource(R.string.action_findByUsername),
                icon = R.drawable.ic_at,
                onClick = { navigator.push(AppRoute.Messaging.FindByUsername) },
            )
        }
    }
}
