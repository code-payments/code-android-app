package com.flipcash.app.messenger.internal.screens.cash

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.app.messenger.internal.groupsend.GroupSendState
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.profile.chatterName
import com.flipcash.shared.payments.group.GroupSendPlan
import com.flipcash.shared.payments.group.GroupSendSplit
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Token
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.SlideToConfirm
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.view.LoadingSuccessState

/**
 * Select People, node 11293:6918: the group's members less the sender, checked one by one in the
 * order that decides the split. There is no Select All.
 */
@Composable
internal fun GroupSelectPeopleContent(
    candidates: List<ChatMember>,
    selected: List<ID>,
    onBack: () -> Unit,
    onToggle: (ChatMember) -> Unit,
    onSend: () -> Unit,
) {
    val selectedIds = selected.toSet()
    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_selectPeople),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = onBack,
            )
        },
        bottomBar = {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2),
                text = pluralStringResource(R.plurals.action_sendToPeople, selected.size, selected.size),
                enabled = selected.isNotEmpty(),
                onClick = onSend,
            )
        },
    ) { innerPadding ->
        val unknownName = stringResource(R.string.title_unnamedUser)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = CodeTheme.dimens.grid.x4),
        ) {
            item {
                Text(
                    modifier = Modifier
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(top = CodeTheme.dimens.grid.x2, bottom = CodeTheme.dimens.grid.x1),
                    text = stringResource(R.string.header_groupSendMembers),
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                )
            }
            items(candidates, key = { it.userId }) { member ->
                MemberCheckRow(
                    member = member,
                    name = chatterName(member.userProfile.displayName, member.userProfile.username, unknownName),
                    selected = member.userId in selectedIds,
                    onClick = { onToggle(member) },
                )
            }
        }
    }
}

/** One member, checked or not. The whole row takes the tap, like the invite sheet's chat rows. */
@Composable
private fun MemberCheckRow(
    member: ChatMember,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = CodeTheme.dimens.inset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        ContactAvatar(
            userProfile = member.userProfile,
            modifier = Modifier
                .requiredSize(CodeTheme.dimens.staticGrid.x6)
                .clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = name,
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Image(
                    painter = painterResource(if (selected) R.drawable.ic_checked else R.drawable.ic_unchecked),
                    contentDescription = null,
                )
            }
            HorizontalDivider(color = CodeTheme.colors.divider)
        }
    }
}

/**
 * The confirm sheet over Select People, node 11293:6918: who is being paid, the total, what each
 * gets, and the swipe. Dismissing it goes back to Select People; it can't be dismissed mid-send.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupSendConfirmSheet(
    groupSend: GroupSendState,
    recipients: List<ChatMember>,
    token: Token?,
    sendProgress: LoadingSuccessState,
    onConfirm: () -> Unit,
    onPickToken: () -> Unit,
    onDismiss: () -> Unit,
) {
    val locked by rememberUpdatedState(!sendProgress.isIdle)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !locked },
    )
    val total = groupSend.total ?: return
    val parts = (groupSend.plan as? GroupSendPlan.Pay)?.parts ?: return
    val unknownName = stringResource(R.string.title_unnamedUser)

    ModalBottomSheet(
        onDismissRequest = { if (!locked) onDismiss() },
        sheetState = sheetState,
        containerColor = CodeTheme.colors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(bottom = CodeTheme.dimens.grid.x4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            RecipientAvatars(recipients)

            val first = recipients.firstOrNull()?.let {
                chatterName(it.userProfile.displayName, it.userProfile.username, unknownName)
            }.orEmpty()
            val others = recipients.size - 1
            Text(
                text = if (others == 0) first
                else pluralStringResource(R.plurals.label_groupSendRecipients, others, first, others),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
                textAlign = TextAlign.Center,
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = total.formatted(),
                    style = CodeTheme.typography.displayMedium,
                    color = CodeTheme.colors.textMain,
                )
                Text(
                    text = eachLabel(parts, total.currencyCode),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
            }

            TokenSelectionPill(
                token = token,
                textStyle = CodeTheme.typography.textMedium,
                imageSize = CodeTheme.dimens.staticGrid.x3,
                onClick = { if (!locked) onPickToken() },
            )

            SlideToConfirm(
                onConfirm = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CodeTheme.dimens.grid.x3),
                enabled = sendProgress.isIdle,
                isLoading = sendProgress.loading,
                isSuccess = sendProgress.success,
                label = stringResource(R.string.action_swipeToSend),
            )
        }
    }
}

/** "$1.00 Each" when the parts are equal, else "~$0.33 Each" for the smallest. */
@Composable
private fun eachLabel(parts: List<Long>, currency: CurrencyCode): String {
    val smallest = GroupSendSplit.fiat(parts.min(), currency).formatted()
    return if (parts.distinct().size == 1) stringResource(R.string.label_groupSendEach, smallest)
    else stringResource(R.string.label_groupSendEachApprox, smallest)
}

/** The first few recipients' faces, overlapping, as in the design's pair of avatars. */
@Composable
private fun RecipientAvatars(recipients: List<ChatMember>) {
    val shown = recipients.take(3)
    val size = CodeTheme.dimens.staticGrid.x10
    val overlap = size / 3
    Box(contentAlignment = Alignment.CenterStart) {
        shown.forEachIndexed { index, member ->
            ContactAvatar(
                userProfile = member.userProfile,
                modifier = Modifier
                    .offset(x = (size - overlap) * index)
                    .requiredSize(size)
                    .clip(CircleShape)
                    .border(2.dp, CodeTheme.colors.background, CircleShape),
            )
        }
        // Reserve the width the offsets take, so the row stays centred.
        Box(Modifier.requiredSize(width = size + (size - overlap) * (shown.size - 1).coerceAtLeast(0), height = size))
    }
}
