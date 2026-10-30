package com.flipcash.app.messenger.internal.screens.cash

import com.flipcash.app.messenger.internal.swipepay.SwipePayActions
import com.flipcash.app.messenger.internal.swipepay.SwipePaySheet
import com.flipcash.app.messenger.internal.swipepay.SwipePayUiState
import com.flipcash.app.messenger.internal.swipepay.rememberSwipePaySheetState
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
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.flipcash.app.messenger.internal.screens.boost.CurrencyPickerContent
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
import com.getcode.theme.White10
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
    title: String = stringResource(R.string.title_selectPeople),
    buttonText: String = pluralStringResource(R.plurals.action_sendToPeople, selected.size, selected.size),
    buttonEnabled: Boolean = selected.isNotEmpty(),
    /** Shown as "You" rather than by name, for a list the viewer is on. */
    viewerId: ID? = null,
    /** The sender's own row, first, for a split they can take a part of. Null leaves it out. */
    you: ChatMember? = null,
) {
    val selectedIds = selected.toSet()
    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = title,
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
                text = buttonText,
                enabled = buttonEnabled,
                onClick = onSend,
            )
        },
    ) { innerPadding ->
        val unknownName = stringResource(R.string.title_unnamedUser)
        val youLabel = stringResource(R.string.label_selectPeople_you)
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
            if (you != null) {
                item(key = "you") {
                    MemberCheckRow(
                        member = you,
                        name = youLabel,
                        selected = you.userId in selectedIds,
                        onClick = { onToggle(you) },
                    )
                }
            }
            items(candidates, key = { it.userId }) { member ->
                MemberCheckRow(
                    member = member,
                    name = if (member.userId == viewerId) {
                        youLabel
                    } else {
                        chatterName(member.userProfile.displayName, member.userProfile.username, unknownName)
                    },
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
 * gets, and the swipe. Closing it goes back to Select People; it can't be closed mid-send. Closed
 * by back, as iOS's confirm is, never by a drag or a tap outside it.
 *
 * The token pill swaps this sheet's content for the currency list and back, rather than opening a
 * sheet of its own, which a navigation sheet would draw over this one.
 */
@Composable
internal fun GroupSendConfirmSheet(
    groupSend: GroupSendState,
    recipients: List<ChatMember>,
    swipePay: SwipePayUiState,
    actions: SwipePayActions,
) {
    val total = groupSend.total ?: return
    val pay = groupSend.plan as? GroupSendPlan.Pay ?: return
    val unknownName = stringResource(R.string.title_unnamedUser)

    SwipePaySheet(
        state = swipePay,
        actions = actions,
        sheetState = rememberSwipePaySheetState(locked = swipePay.locked),
        header = {
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
        },
        amount = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = total.formatted(),
                    style = CodeTheme.typography.displayMedium,
                    color = CodeTheme.colors.textMain,
                )
                if (pay.showsEach) {
                    Text(
                        text = eachLabel(pay, total.currencyCode),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                    )
                }
            }
        },
    )
}

/**
 * "$1.00 Each" when every part, the sender's included, is equal, else "~$0.33 Each" for the
 * smallest paid part; ", including You" when the sender is in the split.
 */
@Composable
private fun eachLabel(pay: GroupSendPlan.Pay, currency: CurrencyCode): String {
    val smallest = GroupSendSplit.fiat(pay.smallest, currency).formatted()
    val res = when {
        pay.includesViewer && pay.isEven -> R.string.label_groupSendEachIncludingYou
        pay.includesViewer -> R.string.label_groupSendEachApproxIncludingYou
        pay.isEven -> R.string.label_groupSendEach
        else -> R.string.label_groupSendEachApprox
    }
    return stringResource(res, smallest)
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
