package com.flipcash.app.messenger.internal.request

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import com.flipcash.shared.chat.ui.request.RequestMotion
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.flipcash.shared.chat.ui.ActionCapsuleButton
import com.flipcash.shared.chat.ui.ActionCapsule
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.app.messenger.internal.screens.boost.CurrencyPickerContent
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.statusLine
import com.flipcash.shared.chat.ui.request.RequestProgressBar
import com.flipcash.shared.chat.ui.request.progressText
import com.flipcash.shared.chat.ui.request.requestText
import com.flipcash.shared.chat.ui.request.text
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.profile.chatterName
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Token
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.SlideToConfirm
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.flipcash.app.messenger.internal.swipepay.SwipePayActions
import com.flipcash.app.messenger.internal.swipepay.SwipePaySheet
import com.flipcash.app.messenger.internal.swipepay.SwipePayUiState
import com.flipcash.app.messenger.internal.swipepay.rememberSwipePaySheetState
import kotlin.time.Instant

/**
 * Request details (frames 09, 11, 24 and 35): the request, one row per share, the viewer's
 * "Send <share>" button, and the creator's close and mark-paid sheets. Editing is not built.
 */
@Composable
internal fun RequestDetailsScreen(
    chatId: ChatId,
    requestId: String,
    onBack: () -> Unit,
    onOpenScreen: (AppRoute) -> Unit,
    pushed: Boolean = false,
) {
    val viewModel = hiltViewModel<RequestDetailsViewModel>()
    LaunchedEffect(chatId, requestId) {
        viewModel.open(chatId, requestId)
    }

    // Fetch on open and every 45 s while the screen is visible with the app in the foreground.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, requestId) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.poll() }
    }
    LaunchedEffect(viewModel) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is RequestDetailsViewModel.Effect.OpenScreen -> onOpenScreen(effect.route)
            }
        }
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val request by viewModel.request.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val token by viewModel.token.collectAsStateWithLifecycle()
    val pay by viewModel.pay.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val unavailable by viewModel.unavailable.collectAsStateWithLifecycle()
    val viewerId = viewModel.viewerId

    val current = request
    val isCreatorOnOpen = current != null && viewerId != null &&
        current.creator == viewerId && current.isOpen(now)

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_requestDetails),
                titleAlignment = Alignment.CenterHorizontally,
                // Pushed from Request Money's history it goes back; opened from the chat it is a
                // sheet of its own, closed the way the share sheet is.
                onBackIconClicked = onBack.takeIf { pushed },
                endContent = {
                    // One capsule for the options and the close, as iOS combines them.
                    val showsClose = !pushed
                    if (isCreatorOnOpen || showsClose) {
                        ActionCapsule {
                            if (isCreatorOnOpen) {
                                ActionCapsuleButton(
                                    icon = Icons.Filled.MoreHoriz,
                                    description = stringResource(R.string.cd_requestOptions),
                                    onClick = viewModel::showOptions,
                                    tag = "request_details_options",
                                )
                            }
                            if (showsClose) {
                                ActionCapsuleButton(
                                    icon = Icons.Filled.Close,
                                    description = stringResource(R.string.cd_close),
                                    onClick = onBack,
                                    tag = "action_close",
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            val share = current?.let { sendableShareOf(it, viewerId, now, state.hasPaymentIntent) }
            if (share != null) {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(bottom = CodeTheme.dimens.grid.x2)
                        .testTag("request_details_send"),
                    text = stringResource(R.string.action_sendShare, share.amount.requestText()),
                    buttonState = ButtonState.Filled,
                    isLoading = pay.preparing,
                    onClick = viewModel::payTapped,
                )
            }
        },
    ) { padding ->
        if (current == null) {
            if (unavailable) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(CodeTheme.dimens.inset),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.description_requestUnavailable),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("request_details_unavailable"),
                    )
                }
            }
            return@CodeScaffold
        }
        RequestDetailsContent(
            request = current,
            members = members,
            viewerId = viewerId,
            now = now,
            contentPadding = padding,
            onRowTapped = viewModel::rowTapped,
        )
    }

    val unnamed = stringResource(R.string.title_unnamedUser)
    val name = { userId: ID -> memberName(members[userId], unnamed) }
    RequestPaySheet(
        pay = pay,
        request = current,
        members = members,
        viewerId = viewerId,
        now = now,
        token = token,
        actions = viewModel.swipePayActions,
    )
    when (val sheet = state.sheet) {
        RequestDetailsViewModel.Sheet.None -> Unit
        RequestDetailsViewModel.Sheet.Options -> ActionSheet(
            title = null,
            body = null,
            action = stringResource(R.string.action_closeRequest),
            working = false,
            onAction = viewModel::askClose,
            onDismiss = viewModel::dismissSheet,
        )
        RequestDetailsViewModel.Sheet.ConfirmClose -> ActionSheet(
            title = stringResource(R.string.title_closeRequestConfirm),
            body = stringResource(R.string.description_closeRequestConfirm),
            action = stringResource(R.string.action_closeRequest),
            working = state.working,
            onAction = viewModel::close,
            onDismiss = viewModel::dismissSheet,
        )
        is RequestDetailsViewModel.Sheet.Member -> {
            val amount = current?.split?.shareOf(sheet.userId)?.amount?.requestText().orEmpty()
            ActionSheet(
                title = name(sheet.userId),
                body = stringResource(R.string.label_shareUnpaidAmount, amount),
                action = stringResource(R.string.action_markAsPaid),
                working = false,
                onAction = { viewModel.askMarkPaid(sheet.userId) },
                onDismiss = viewModel::dismissSheet,
            )
        }
        is RequestDetailsViewModel.Sheet.ConfirmMarkPaid -> {
            val first = firstName(name(sheet.userId))
            ActionSheet(
                title = stringResource(R.string.title_markPaidConfirm, first),
                body = stringResource(R.string.description_markPaidConfirm, first),
                action = stringResource(R.string.action_markAsPaid),
                working = state.working,
                onAction = { viewModel.markPaid(sheet.userId) },
                onDismiss = viewModel::dismissSheet,
            )
        }
    }
}

@Composable
private fun RequestDetailsContent(
    request: PaymentRequest,
    members: Map<ID, ChatMember>,
    viewerId: ID?,
    now: Instant,
    contentPadding: PaddingValues,
    onRowTapped: (ID) -> Unit,
) {
    val split = request.split ?: return
    val open = request.isOpen(now)
    val creator = members[request.creator]
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(bottom = CodeTheme.dimens.grid.x4),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(top = CodeTheme.dimens.grid.x4, bottom = CodeTheme.dimens.grid.x3),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            ) {
                Text(
                    text = request.deckTitle.orEmpty(),
                    style = CodeTheme.typography.displayCompact,
                    color = CodeTheme.colors.textMain,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                ) {
                    if (creator != null) {
                        ContactAvatar(
                            userProfile = creator.userProfile,
                            modifier = Modifier
                                .requiredSize(CodeTheme.dimens.staticGrid.x4)
                                .clip(CircleShape),
                        )
                    }
                    Text(
                        text = if (request.creator == viewerId) stringResource(R.string.label_requestedByYou)
                        else stringResource(R.string.label_requestedBy, memberName(creator)),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                    )
                }
                Text(
                    text = request.progressText(),
                    style = CodeTheme.typography.displaySmall,
                    color = CodeTheme.colors.textMain,
                )
                RequestProgressBar(fraction = split.paidFraction, ended = !open)
                Text(
                    text = request.statusLine(now).text(),
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                )
            }
        }
        items(split.shares, key = { it.userId }) { share ->
            val tappable = canMarkPaid(request, share.userId, viewerId, now)
            ShareRow(
                member = members[share.userId],
                isViewer = share.userId == viewerId,
                share = share,
                requestOpen = open,
                onClick = if (tappable) ({ onRowTapped(share.userId) }) else null,
            )
        }
    }
}

@Composable
private fun ShareRow(
    member: ChatMember?,
    isViewer: Boolean,
    share: Share,
    requestOpen: Boolean,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(bottom = CodeTheme.dimens.grid.x2)
            .clip(CodeTheme.shapes.medium)
            .background(CodeTheme.colors.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        MemberAvatar(member, Modifier.requiredSize(CodeTheme.dimens.staticGrid.x6))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isViewer) stringResource(R.string.title_you) else memberName(member),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Fades to the paid state when the share turns paid while the screen is up.
            Crossfade(
                targetState = if (share.isPaid) 2 else if (requestOpen) 1 else 0,
                animationSpec = RequestMotion.change(),
                label = "share state",
            ) { state ->
                Text(
                    text = when (state) {
                        2 -> stringResource(R.string.label_sharePaid)
                        1 -> stringResource(R.string.label_shareUnpaid)
                        else -> stringResource(R.string.label_shareUnpaidEnded)
                    },
                    style = CodeTheme.typography.textSmall,
                    color = if (state == 2) CodeTheme.colors.success else CodeTheme.colors.textSecondary,
                )
            }
        }
        Text(
            text = share.amount.requestText(),
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
        )
    }
}

/** Frames 28, 29, 33 and 34: an optional title and body, one action, and Cancel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionSheet(
    title: String?,
    body: String?,
    action: String,
    working: Boolean,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !working },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!working) onDismiss() },
        sheetState = sheetState,
        containerColor = CodeTheme.colors.background,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(bottom = CodeTheme.dimens.grid.x4),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            title?.let {
                Text(text = it, style = CodeTheme.typography.screenTitle, color = CodeTheme.colors.textMain)
            }
            body?.let {
                Text(text = it, style = CodeTheme.typography.textMedium, color = CodeTheme.colors.textSecondary)
            }
            CodeButton(
                modifier = Modifier.fillMaxWidth().testTag("request_action_primary"),
                text = action,
                buttonState = ButtonState.Filled,
                isLoading = working,
                onClick = onAction,
            )
            CodeButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.action_cancel),
                buttonState = ButtonState.Subtle,
                enabled = !working,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
internal fun memberName(member: ChatMember?): String =
    memberName(member, stringResource(R.string.title_unnamedUser))

private fun memberName(member: ChatMember?, fallback: String): String =
    member?.let { chatterName(it.userProfile.displayName, it.userProfile.username, fallback) } ?: fallback

/** A member's avatar, or an empty circle of the same size while the roster loads. */
@Composable
internal fun MemberAvatar(member: ChatMember?, modifier: Modifier) {
    if (member != null) {
        ContactAvatar(userProfile = member.userProfile, modifier = modifier.clip(CircleShape))
    } else {
        Box(modifier.clip(CircleShape).background(CodeTheme.colors.divider))
    }
}

private fun firstName(name: String): String = name.trim().substringBefore(' ').ifEmpty { name }
