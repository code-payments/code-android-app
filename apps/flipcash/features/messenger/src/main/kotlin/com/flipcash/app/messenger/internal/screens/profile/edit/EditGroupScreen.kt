package com.flipcash.app.messenger.internal.screens.profile.edit

import android.os.Parcelable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.menu.MenuList
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.profile.GroupBalanceRequirements
import com.flipcash.app.messenger.internal.screens.profile.holdingLabel
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.GroupBalanceRole
import com.flipcash.shared.common.ui.profile.BalanceRequirementsCard
import com.getcode.theme.CodeTheme
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeScaffold

/**
 * The group's edit list — node 10187:110373.
 *
 * Two rows, because `EditChatRequest` has two fields. The design's other rows (Membership Card,
 * Description, Social Links) are left out rather than stubbed: `EditChatRequest` in flipcash2
 * 0.11.0 carries `title` and `picture` only, so each of them would be a row that opens onto
 * nothing the server would accept.
 *
 * The design titles this screen "Edit Community"; it is titled "Edit Group" here because "Group"
 * is what the rest of the app calls this thing in front of the user — "New Public Group", "Group
 * Name" — and one screen switching vocabulary mid-flow is worse than differing from the mock.
 *
 * Under the rows sits the Balance Requirements card, always shown and reading "None" for a rule
 * the group lacks, whose Join and Chat rows open [EditGroupBalanceRequirementScreen]. The card
 * reads the group off [chatViewModel], as the profile's does; the rows hold nothing of their own —
 * each destination owns its edit and its failure. The list itself is not gated — `canEdit` gates the door to it on
 * [com.flipcash.app.messenger.internal.screens.profile.GroupProfileScreen], and the server
 * re-checks on the write regardless.
 */
@Composable
internal fun EditGroupScreen(chatViewModel: ChatViewModel) {
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val chatState by chatViewModel.stateFlow.collectAsStateWithLifecycle()
    val group = chatState.subject as? ChatSubject.Group
    val requirements = remember(group?.rules) { GroupBalanceRequirements.from(group?.rules) }

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_editGroup),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = { flowNavigator.back() },
            )
        },
    ) { innerPadding ->
        MenuList(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            items = editGroupItems(),
            showChevrons = true,
            footer = {
                BalanceRequirementsCard(
                    modifier = Modifier.padding(
                        horizontal = CodeTheme.dimens.inset,
                        vertical = CodeTheme.dimens.grid.x6,
                    ),
                    join = requirements?.join?.let { holdingLabel(it, chatState.ruleTokens) },
                    chat = requirements?.chat?.let { holdingLabel(it, chatState.ruleTokens) },
                    yourBalance = null,
                    onJoinClick = {
                        flowNavigator.navigateTo(ChatStep.EditGroupBalanceRequirement(GroupBalanceRole.Join))
                    },
                    onChatClick = {
                        flowNavigator.navigateTo(ChatStep.EditGroupBalanceRequirement(GroupBalanceRole.Chat))
                    },
                )
            },
            onItemClick = { item ->
                when (item.action) {
                    EditGroupAction.Picture -> flowNavigator.navigateTo(ChatStep.EditGroupPicture)
                    EditGroupAction.Name -> flowNavigator.navigateTo(ChatStep.EditGroupName)
                }
            },
        )
    }
}
