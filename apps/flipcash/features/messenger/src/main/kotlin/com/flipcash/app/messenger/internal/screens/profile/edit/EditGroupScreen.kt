package com.flipcash.app.messenger.internal.screens.profile.edit

import android.os.Parcelable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.menu.MenuList
import com.flipcash.features.messenger.R
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeScaffold

/**
 * The group's edit list — node 10187:110373.
 *
 * Two rows, because the contract has two fields. The design's other rows (Membership Card,
 * Description, Social Links) are left out rather than stubbed: `EditChatRequest` in flipcash2
 * 0.11.0 carries `title` and `picture` only, so each of them would be a row that opens onto
 * nothing the server would accept.
 *
 * The design titles this screen "Edit Community"; it is titled "Edit Group" here because "Group"
 * is what the rest of the app calls this thing in front of the user — "New Public Group", "Group
 * Name" — and one screen switching vocabulary mid-flow is worse than differing from the mock.
 *
 * Stateless beyond navigation: both rows own their own edit and their own failure, so there is
 * nothing here to hold. The list itself is not gated — `canEdit` gates the door to it on
 * [com.flipcash.app.messenger.internal.screens.profile.GroupProfileScreen], and the server
 * re-checks on the write regardless.
 */
@Composable
internal fun EditGroupScreen() {
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_editGroup),
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
            onItemClick = { item ->
                when (item.action) {
                    EditGroupAction.Picture -> flowNavigator.navigateTo(ChatStep.EditGroupPicture)
                    EditGroupAction.Name -> flowNavigator.navigateTo(ChatStep.EditGroupName)
                }
            },
        )
    }
}
