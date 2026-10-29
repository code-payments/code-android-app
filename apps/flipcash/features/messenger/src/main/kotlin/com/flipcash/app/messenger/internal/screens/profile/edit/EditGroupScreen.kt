package com.flipcash.app.messenger.internal.screens.profile.edit

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.profile.GroupBalanceRequirements
import com.flipcash.app.messenger.internal.screens.profile.holdingLabel
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.GroupBalanceRole
import com.flipcash.shared.chat.isBalanceEditable
import com.flipcash.shared.chat.speakerMinimum
import com.flipcash.shared.common.ui.profile.BalanceRequirementsCard
import com.flipcash.shared.common.ui.profile.CoverAndPhoto
import com.flipcash.shared.common.ui.profile.FieldCard
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeScaffold

/**
 * The group's edit screen: its cover and picture, then a card for each text field, then the
 * balance requirements.
 *
 * The requirements' Join and Chat rows open [EditGroupBalanceRequirementScreen] only where
 * `EditChatRequest.rules` would be accepted: a public group created with a listener minimum
 * ([isBalanceEditable]). Anywhere else the card is shown and not editable. The Chat row reads the
 * group's own speaker rule, "None" without one.
 *
 * Every field opens its own editor and saves on its own, so nothing is held here. The screen is
 * the door to those editors and leaves the moment the viewer stops being allowed through it: if
 * `canEdit` goes false while it is open (a role change arriving on the stream), it pops. The
 * server re-checks on every write regardless.
 */
@Composable
internal fun EditGroupScreen(chatViewModel: ChatViewModel) {
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val state by chatViewModel.stateFlow.collectAsStateWithLifecycle()
    val group = state.subject as? ChatSubject.Group
    val canEdit = state.viewerState?.permissions?.canEdit

    LaunchedEffect(canEdit) {
        if (canEdit == false) flowNavigator.back()
    }

    val requirements = remember(group?.rules) { GroupBalanceRequirements.from(group?.rules) }
    val tokens = state.ruleTokens
    val balanceEditable = group != null && isBalanceEditable(group.rules, group.isPrivate)
    val grid = CodeTheme.dimens.staticGrid

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_editGroup),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = { flowNavigator.back() },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(vertical = grid.x5),
        ) {
            CoverAndPhoto(
                cover = group?.coverPicture,
                avatar = group?.picture,
                displayName = group?.groupTitle.orEmpty(),
                access = BlobAccessContext.Owned,
                changeCoverLabel = stringResource(R.string.action_changeCover),
                changePhotoLabel = stringResource(R.string.action_changePhoto),
                onChangeCover = { flowNavigator.navigateTo(ChatStep.EditGroupCover) },
                onChangePhoto = { flowNavigator.navigateTo(ChatStep.EditGroupPicture) },
            )

            Column(
                modifier = Modifier.padding(top = grid.x4),
                verticalArrangement = Arrangement.spacedBy(grid.x2),
            ) {
                FieldCard(
                    title = stringResource(R.string.label_editGroupName),
                    value = group?.groupTitle?.takeIf { it.isNotEmpty() },
                    placeholder = stringResource(R.string.placeholder_editGroupName),
                    onClick = { flowNavigator.navigateTo(ChatStep.EditGroupName) },
                )
                FieldCard(
                    title = stringResource(R.string.label_editGroupDescription),
                    value = group?.description?.takeIf { it.isNotEmpty() },
                    placeholder = stringResource(R.string.placeholder_editGroupDescription),
                    onClick = { flowNavigator.navigateTo(ChatStep.EditGroupDescription) },
                    maxLines = 3,
                )
            }

            if (requirements != null) {
                BalanceRequirementsCard(
                    modifier = Modifier.padding(top = grid.x6),
                    join = requirements.join?.let { holdingLabel(it, tokens) },
                    // The group's own speaker rule, not the profile's fallback to Join: this row
                    // edits that rule, so "None" has to mean there isn't one.
                    chat = group?.rules?.speakerMinimum?.let { holdingLabel(it, tokens) },
                    yourBalance = null,
                    compact = true,
                    footnote = stringResource(R.string.footer_groupBalanceRequirements),
                    onJoinClick = if (balanceEditable) {
                        { flowNavigator.navigateTo(ChatStep.EditGroupBalanceRequirement(GroupBalanceRole.Join)) }
                    } else null,
                    onChatClick = if (balanceEditable) {
                        { flowNavigator.navigateTo(ChatStep.EditGroupBalanceRequirement(GroupBalanceRole.Chat)) }
                    } else null,
                )
            }
        }
    }
}
