package com.flipcash.app.tipping

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.analytics.events.GroupEvents
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.tipping.internal.NewChatViewModel
import com.flipcash.features.tipping.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.ChoiceRow

/**
 * Node 10127:117987 — the ways to start a chat, behind the Chats list's "+".
 *
 * A chooser rather than a direct push because the kinds of chat share no entry UI: a group is
 * created here and then invited into, a DM is opened by resolving someone's handle. The one thing
 * it holds is whether the account has a community currency, because a community chat is gated on
 * one and the form has nothing to offer without it (node 12370:77242).
 */
@Composable
fun NewChatScreen() {
    val navigator = LocalCodeNavigator.current
    val analytics = rememberAnalytics()
    val resources = LocalResources.current
    val viewModel = hiltViewModel<NewChatViewModel>()
    val hasCommunityCurrency by viewModel.hasCommunityCurrency.collectAsStateWithLifecycle()

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
                label = stringResource(R.string.action_findByUsername),
                icon = R.drawable.ic_at,
                onClick = { navigator.push(AppRoute.Messaging.FindByUsername) },
            )
            ChoiceRow(
                label = stringResource(R.string.action_createPrivateGroup),
                icon = R.drawable.ic_group_3,
                onClick = { navigator.push(AppRoute.Messaging.NewGroup(isPrivate = true)) },
            )
            ChoiceRow(
                label = stringResource(R.string.action_createPublicGroup),
                icon = R.drawable.ic_currency_coin,
                onClick = {
                    // Unknown counts as having one: the form keeps Create disabled until a
                    // currency is seated, which is a better answer than a sheet that may be wrong.
                    if (hasCommunityCurrency == false) {
                        presentNoCommunityCurrencies(resources, navigator)
                    } else {
                        analytics.track(GroupEvents.newOpened(null))
                        navigator.push(AppRoute.Messaging.NewGroup())
                    }
                },
            )
        }
    }
}

/** Node 12370:77242. Explore pushes the leaderboard onto this stack, so Back returns to New Chat. */
private fun presentNoCommunityCurrencies(resources: Resources, navigator: CodeNavigator) {
    BottomBarManager.showInfo(
        title = resources.getString(R.string.title_noCommunityCurrencies),
        message = resources.getString(R.string.description_noCommunityCurrencies),
        actions = listOf(
            BottomBarAction(text = resources.getString(R.string.action_exploreCurrencies)) {
                navigator.push(AppRoute.Token.Discovery)
            },
            BottomBarAction(
                text = resources.getString(R.string.action_dismiss),
                style = BottomBarManager.BottomBarButtonStyle.Text,
            ),
        ),
    )
}
