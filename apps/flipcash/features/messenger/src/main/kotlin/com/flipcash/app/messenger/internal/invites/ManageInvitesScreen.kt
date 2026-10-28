package com.flipcash.app.messenger.internal.invites

import android.os.Parcelable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.messenger.internal.screens.openInviteLinkShare
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.HostInvite
import com.flipcash.shared.chat.InviteExpiry
import com.flipcash.shared.chat.InviteSettings
import com.flipcash.shared.chat.InviteUses
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.White50
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.ChoiceRow
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.launch

/**
 * Frames 2a to 2e: the host's invite links, Active then Inactive, with New Link and a row menu.
 * A pushed step of the chat flow, reached from the invite sheet and the profile's overflow menu.
 */
@Composable
internal fun ManageInvitesScreen(chatId: ChatId?) {
    val viewModel = hiltViewModel<ManageInvitesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val navigator = LocalCodeNavigator.current

    LaunchedEffect(chatId) { chatId?.let(viewModel::open) }

    var rowMenu by remember { mutableStateOf<HostInvite?>(null) }
    var newLink by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Back to the profile that opened this.
                ManageInvitesViewModel.Effect.LostAccess -> flowNavigator.back()
                ManageInvitesViewModel.Effect.Created -> newLink = false
            }
        }
    }

    ManageInvitesContent(
        state = state,
        onBack = { flowNavigator.back() },
        onRow = { rowMenu = it },
        onNewLink = { newLink = true },
        onRetry = viewModel::refresh,
    )

    rowMenu?.let { invite ->
        InviteLinkActionsSheet(
            invite = invite,
            onShare = {
                rowMenu = null
                navigator.openInviteLinkShare(invite.invite.id.toHex())
            },
            onRevoke = {
                rowMenu = null
                viewModel.confirmRevoke(invite)
            },
            onDismiss = { rowMenu = null },
        )
    }
    if (newLink) {
        NewInviteLinkSheet(
            creating = state.creating,
            onCreate = viewModel::create,
            onDismiss = { newLink = false },
        )
    }
}

/** Frames 2a and 2e, with no navigation or view model, so the previews and tests can draw them. */
@Composable
internal fun ManageInvitesContent(
    state: ManageInvitesViewModel.State,
    onBack: () -> Unit,
    onRow: (HostInvite) -> Unit,
    onNewLink: () -> Unit,
    onRetry: () -> Unit = {},
) {
    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_inviteLinks),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = onBack,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("manage_invites_screen"),
        ) {
            if (state.loading && state.active.isEmpty() && state.inactive.isEmpty()) {
                // First load: not an empty list, which would read as "no links".
                CodeCircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("manage_invites_loading"),
                )
            } else if (state.failed) {
                LoadFailed(modifier = Modifier.align(Alignment.Center), onRetry = onRetry)
            } else if (state.isEmpty) {
                EmptyInvites(modifier = Modifier.align(Alignment.Center), onNewLink = onNewLink)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = CodeTheme.dimens.inset,
                        vertical = CodeTheme.dimens.grid.x3,
                    ),
                ) {
                    if (state.active.isNotEmpty()) {
                        item(key = "active") {
                            InviteSection(
                                header = stringResource(R.string.header_inviteLinksActive),
                                invites = state.active,
                                current = state.current,
                                onRow = onRow,
                            )
                        }
                    }
                    if (state.inactive.isNotEmpty()) {
                        item(key = "inactive") {
                            InviteSection(
                                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x5),
                                header = stringResource(R.string.header_inviteLinksInactive),
                                invites = state.inactive,
                                current = null,
                                onRow = null,
                            )
                        }
                    }
                    // Room for the pinned button.
                    item(key = "clearance") { Box(Modifier.padding(bottom = 80.dp)) }
                }
                CodeButton(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(bottom = CodeTheme.dimens.grid.x3),
                    text = stringResource(R.string.action_newInviteLink),
                    buttonState = ButtonState.Filled,
                    onClick = onNewLink,
                )
            }
        }
    }
}

@Composable
private fun InviteSection(
    header: String,
    invites: List<HostInvite>,
    current: HostInvite?,
    onRow: ((HostInvite) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.padding(start = CodeTheme.dimens.grid.x2, bottom = CodeTheme.dimens.grid.x1),
            text = header,
            style = CodeTheme.typography.textSmall,
            color = White50,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CodeTheme.shapes.small)
                .background(White05),
        ) {
            invites.forEachIndexed { index, invite ->
                if (index > 0) HorizontalDivider(color = CodeTheme.colors.divider)
                InviteRow(
                    invite = invite,
                    isCurrent = current != null && current.invite.id == invite.invite.id,
                    onClick = onRow?.let { { it(invite) } },
                )
            }
        }
    }
}

/**
 * One link. An inactive row is greyed and has no click. A link this device can't rebuild says so
 * and still opens its menu, which then offers only Revoke.
 */
@Composable
private fun InviteRow(invite: HostInvite, isCurrent: Boolean, onClick: (() -> Unit)?) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickable)
            .alpha(if (invite.isActive) 1f else InactiveAlpha)
            .padding(horizontal = CodeTheme.dimens.grid.x3, vertical = CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.label_inviteCreated, shortDate(invite.invite.createdAt)),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
            Text(
                text = invite.detailLine(),
                style = CodeTheme.typography.textSmall,
                color = White50,
            )
        }
        if (isCurrent) CurrentChip()
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = White50,
            )
        }
    }
}

@Composable
private fun CurrentChip() {
    Text(
        modifier = Modifier
            .clip(CircleShape)
            .background(White05)
            .padding(horizontal = CodeTheme.dimens.grid.x2, vertical = 2.dp),
        text = stringResource(R.string.label_inviteCurrent),
        style = CodeTheme.typography.textSmall,
        color = CodeTheme.colors.textMain,
    )
}

/** The first load failed: say so, and offer to try again rather than showing a blank list. */
@Composable
private fun LoadFailed(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = CodeTheme.dimens.inset)
            .testTag("manage_invites_failed"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        Text(
            text = stringResource(R.string.title_inviteLinksNotLoaded),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.widthIn(max = 280.dp),
            text = stringResource(R.string.description_inviteLinksNotLoaded),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        CodeButton(
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x3),
            text = stringResource(R.string.action_retry),
            buttonState = ButtonState.Filled10,
            shape = CircleShape,
            style = CodeTheme.typography.textSmall,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
            overrideContentPadding = true,
            onClick = onRetry,
        )
    }
}

/** Frame 2e. */
@Composable
private fun EmptyInvites(onNewLink: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = CodeTheme.dimens.inset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        Text(
            text = stringResource(R.string.title_noInviteLinks),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.widthIn(max = 280.dp),
            text = stringResource(R.string.subtitle_noInviteLinks),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        CodeButton(
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x3),
            text = stringResource(R.string.action_newInviteLink),
            buttonState = ButtonState.Filled10,
            shape = CircleShape,
            style = CodeTheme.typography.textSmall,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
            overrideContentPadding = true,
            onClick = onNewLink,
        )
    }
}

/** Frame 2b: Share Link and Revoke Link, in the mute sheet's style. A link that can't be shared gets Revoke only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InviteLinkActionsSheet(
    invite: HostInvite,
    onShare: () -> Unit,
    onRevoke: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CodeTheme.colors.background,
    ) {
        InviteLinkActions(invite = invite, onShare = onShare, onRevoke = onRevoke, onDismiss = onDismiss)
    }
}

@Composable
internal fun InviteLinkActions(
    invite: HostInvite,
    onShare: () -> Unit,
    onRevoke: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        AppBarWithTitle(
            title = stringResource(R.string.title_inviteLink),
            titleAlignment = Alignment.CenterHorizontally,
            endContent = { AppBarDefaults.Close(onClick = onDismiss) },
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset),
            text = "${stringResource(R.string.label_inviteCreated, shortDate(invite.invite.createdAt))} · ${invite.detailLine()}",
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(vertical = CodeTheme.dimens.grid.x4),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            if (invite.canShare) {
                ChoiceRow(
                    label = stringResource(R.string.action_shareLink),
                    icon = R.drawable.ic_send_outlined,
                    onClick = onShare,
                )
            }
            ChoiceRow(
                label = stringResource(R.string.action_revokeLink),
                icon = rememberVectorPainter(Icons.Outlined.Block),
                contentColor = CodeTheme.colors.errorText,
                onClick = onRevoke,
            )
        }
    }
}

/** Frame 2d: expiry and use-limit presets, and Create Link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewInviteLinkSheet(
    creating: Boolean,
    onCreate: (InviteSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CodeTheme.colors.background,
    ) {
        NewInviteLinkContent(creating = creating, onCreate = onCreate)
    }
}

@Composable
internal fun NewInviteLinkContent(
    creating: Boolean,
    onCreate: (InviteSettings) -> Unit,
    initial: InviteSettings = InviteSettings.Default,
) {
    var expiry by remember { mutableStateOf(initial.expiry) }
    var uses by remember { mutableStateOf(initial.uses) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = CodeTheme.dimens.inset),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        Text(
            text = stringResource(R.string.title_newInviteLink),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )
        Text(
            text = stringResource(R.string.label_inviteExpiresHeader),
            style = CodeTheme.typography.textSmall,
            color = White50,
        )
        OptionChips(
            options = InviteExpiry.entries,
            selected = expiry,
            label = { stringResource(it.labelRes()) },
            onSelect = { expiry = it },
        )
        Text(
            text = stringResource(R.string.label_inviteWhoCanJoin),
            style = CodeTheme.typography.textSmall,
            color = White50,
        )
        OptionChips(
            options = InviteUses.entries,
            selected = uses,
            label = { stringResource(it.labelRes()) },
            onSelect = { uses = it },
        )
        CodeButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = CodeTheme.dimens.grid.x2, bottom = CodeTheme.dimens.grid.x3),
            text = stringResource(R.string.action_createInviteLink),
            isLoading = creating,
            enabled = !creating,
            buttonState = ButtonState.Filled,
            onClick = { onCreate(InviteSettings(expiry, uses)) },
        )
    }
}

@Composable
private fun <T> OptionChips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        for (option in options) {
            val isSelected = option == selected
            // The chip is a radio button in a group, and at least 48dp tall to touch.
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) CodeTheme.colors.textMain else White05)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = CodeTheme.dimens.grid.x3, vertical = CodeTheme.dimens.grid.x2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = CodeTheme.typography.textSmall,
                    color = if (isSelected) CodeTheme.colors.background else CodeTheme.colors.textMain,
                )
            }
        }
    }
}

private fun InviteExpiry.labelRes(): Int = when (this) {
    InviteExpiry.OneDay -> R.string.label_inviteExpiry_oneDay
    InviteExpiry.SevenDays -> R.string.label_inviteExpiry_sevenDays
    InviteExpiry.ThirtyDays -> R.string.label_inviteExpiry_thirtyDays
    InviteExpiry.Never -> R.string.label_inviteExpiry_never
}

private fun InviteUses.labelRes(): Int = when (this) {
    InviteUses.One -> R.string.label_inviteUses_one
    InviteUses.Five -> R.string.label_inviteUses_five
    InviteUses.Ten -> R.string.label_inviteUses_ten
    InviteUses.TwentyFive -> R.string.label_inviteUses_twentyFive
    InviteUses.NoLimit -> R.string.label_inviteUses_noLimit
}

/** Inactive rows are greyed (frame 2a). */
private const val InactiveAlpha = 0.5f
