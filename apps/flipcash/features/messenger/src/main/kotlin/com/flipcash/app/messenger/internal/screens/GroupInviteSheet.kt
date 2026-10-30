package com.flipcash.app.messenger.internal.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.getcode.theme.extraSmall
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.GroupInviteResult
import com.flipcash.app.core.chat.InviteHostAction
import com.flipcash.app.core.media.rememberMediaUrl
import com.flipcash.app.core.share.SharePreviewImage
import com.flipcash.app.core.share.TipCodePreview
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.GroupInviteViewModel
import com.flipcash.app.messenger.internal.invites.InviteLinkState
import com.flipcash.app.messenger.internal.invites.InviteLinkViewModel
import com.flipcash.app.messenger.internal.invites.elideInviteUrl
import com.flipcash.app.messenger.internal.invites.plainLinkText
import com.flipcash.app.messenger.internal.invites.inviteMetaLine
import kotlinx.coroutines.flow.Flow
import com.flipcash.app.shareable.LocalShareController
import com.flipcash.app.shareable.Shareable
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.opencode.model.core.ID
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.navigateForResult
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.White50
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.chat.ChatInput
import com.getcode.ui.components.chat.ChatInputSubmit
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.utils.sheetResignmentBehavior
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

/**
 * Opens the invite sheet over the current step and, once its invites are out, the chat picked
 * first.
 *
 * The sheet returns that chat rather than navigating itself. It is opened from the transcript and
 * from the group's profile, and only the screen that opened it is sure to be composed when the
 * sends finish, so that screen does the navigating.
 */
internal fun CodeNavigator.openGroupInvite() {
    navigateForResult<GroupInviteResult>(ChatStep.InviteToGroup) { result ->
        if (result is NavResultOrCanceled.ReturnValue) {
            navigate(AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(result.value.chatId)))
        }
    }
}

/**
 * Opens the invite variant of the sheet: the host's current link, or the invite [inviteIdHex] picked
 * in Manage Invites. Returns the chat picked first, as [openGroupInvite] does.
 */
internal fun CodeNavigator.openInviteLinkShare(inviteIdHex: String? = null) {
    navigateForResult<GroupInviteResult>(ChatStep.ShareInviteLink(inviteIdHex)) { result ->
        if (result is NavResultOrCanceled.ReturnValue) {
            navigate(AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(result.value.chatId)))
        }
    }
}

/**
 * Opens the host's Invite sheet (frame 1m). It returns what to open next and closes, and the next
 * sheet opens from here, once it is gone; [onManageInvites] opens Manage Invites in the chat flow.
 */
internal fun CodeNavigator.openInviteHost(onManageInvites: () -> Unit) {
    navigateForResult<InviteHostAction>(ChatStep.InviteHost) { result ->
        if (result !is NavResultOrCanceled.ReturnValue) return@navigateForResult
        when (result.value) {
            InviteHostAction.ShareInvite -> openInviteLinkShare()
            InviteHostAction.ShareJoin -> openGroupInvite()
            InviteHostAction.ManageInvites -> onManageInvites()
        }
    }
}

/** Opens the sheet on a message's link; the chat picked first opens once the sends are out. */
internal fun CodeNavigator.openMessageShare(messageId: Long) {
    navigateForResult<GroupInviteResult>(ChatStep.ShareMessage(messageId)) { result ->
        if (result is NavResultOrCanceled.ReturnValue) {
            navigate(AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(result.value.chatId)))
        }
    }
}

/** Test id of the Show QR Code tile. */
internal const val GroupCardTileTag = "group-card-tile"

/** A group's tile, for either link, carries [GroupCardTileTag]; the profile tile has none. */
internal fun cardTileTag(subject: ShareToChatsSubject): String? = when (subject) {
    is ShareToChatsSubject.Group, is ShareToChatsSubject.GroupInviteLink -> GroupCardTileTag
    else -> null
}

/**
 * Whether the card tile is drawn: whenever the host supplies a card to show, so a group's tile is
 * there for every group and every link, plain or invite, as Share and Copy are. Only a message
 * link has no card.
 */
internal fun showsCardTile(subject: ShareToChatsSubject, onShowCard: (() -> Unit)?): Boolean =
    onShowCard != null &&
        subject !is ShareToChatsSubject.Message &&
        subject !is ShareToChatsSubject.Currency

/**
 * What [GroupInviteSheet] hands out: a group's invite link, a person's profile link, or a link to
 * one message. They differ in their copy, in what the Share tile gives the system share sheet, and in whether the
 * group's analytics fire; the rest of the sheet is the same.
 */
internal sealed interface ShareToChatsSubject {
    /**
     * @param group what is being invited to, for the group's name and picture. The sheet only opens
     * on a group, so null is the frame before the subject resolves.
     */
    data class Group(val group: ChatSubject.Group?) : ShareToChatsSubject

    /**
     * A private group's invite link (frames 1b and 1f): the join variant's sheet with a link pill,
     * Share Link and Copy Link in place of the group card, and Recent Chats cut to the chats an
     * invite link may be sent into.
     *
     * [link] carries the link; its `toString` leaves the link out, so this prints without it.
     */
    data class GroupInviteLink(val group: ChatSubject.Group?, val link: InviteLinkState) : ShareToChatsSubject

    /**
     * @param preview the tip-code image for the system share sheet's card, when one is rendered.
     */
    data class User(
        val userId: ID,
        val username: String?,
        val displayName: String?,
        val preview: TipCodePreview?,
    ) : ShareToChatsSubject

    /**
     * A message, handed out as its link. The sheet's URL is the link itself, so nothing else is
     * carried: the recipient's app draws the message card from it.
     */
    data object Message : ShareToChatsSubject

    /** A currency's page link, from Currency Info. Like a message link it has no card to show. */
    data class Currency(val name: String) : ShareToChatsSubject
}

/**
 * Node 10329:12104 — handing out a link to a group or a person, and sending it straight into the
 * viewer's other chats.
 *
 * The Share and Copy tiles carry the same URL, built once by the caller: for a group by
 * [com.flipcash.app.core.util.Linkify.groupChatInvite] from the chat's id (there is no invite RPC,
 * the id *is* the invite), for a person by [com.flipcash.app.core.util.Linkify.tipcard]. One
 * builder is what keeps the shared link, the copied link and the sent link identical.
 *
 * @param inviteUrl null for anyone who has nobody to invite — a DM, or a group this viewer has not
 * joined — in which case the sheet is the title bar alone. Both entry points are gated on the same
 * value, so this is a race with a leave rather than a state to design for.
 * @param subject what is being handed out.
 * @param state the Recent Chats selection and the message to send with the link.
 * @param onShowCard opens the profile card of a person or the scan card of a group, as a third
 * tile beside Share and Copy. Null hides the tile (a message link has no card).
 */
@Composable
internal fun GroupInviteSheet(
    inviteUrl: String?,
    subject: ShareToChatsSubject,
    state: GroupInviteViewModel.State,
    onShare: () -> Unit = {},
    onCopy: () -> Unit,
    onToggle: (ChatId) -> Unit,
    onMessageChanged: (String) -> Unit,
    onInvite: () -> Unit,
    onDismiss: () -> Unit,
    onShowCard: (() -> Unit)? = null,
    /** The invite variant's Share, Copy and Send answers; see [InviteLinkViewModel.Effect]. */
    inviteEffects: Flow<InviteLinkViewModel.Effect>? = null,
    onInviteEffect: (InviteLinkViewModel.Effect) -> Unit = {},
) {
    val shareController = LocalShareController.current
    val scope = rememberCoroutineScope()

    // Resolved here rather than in the share controller: the stored download URL expires, and
    // re-minting it needs the chat's profile to authorize it — neither of which the shareable
    // module can see. Same size and access as the avatar the conversation bar draws.
    val inviteLink = subject as? ShareToChatsSubject.GroupInviteLink
    val group = (subject as? ShareToChatsSubject.Group)?.group ?: inviteLink?.group
    // Share, Copy and Send wait for the link: the tiles are disabled rather than dropping the tap.
    val creating = inviteLink != null && inviteLink.link !is InviteLinkState.Ready
    val picture = group?.picture
    val pictureUrl = rememberMediaUrl(
        media = picture,
        targetLongestSidePx = SharePreviewImage.TARGET_PX,
        access = group?.chatId
            ?.let { BlobAccessContext.ChatProfile(it) }
            ?: BlobAccessContext.Owned,
    )

    val hazeState = rememberHazeState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val material = HazeMaterials.ultraThin(containerColor = CodeTheme.colors.background)
    val scrimColor = CodeTheme.colors.surface
    var headerHeight by remember { mutableStateOf(0.dp) }
    var fieldHeight by remember { mutableStateOf(0.dp) }
    var copied by remember { mutableStateOf(false) }

    if (inviteEffects != null) {
        val groupTitle = group?.groupTitle
        val cacheKey = picture?.cacheKeyForSize(SharePreviewImage.TARGET_PX)
        // The collector outlives recompositions, so it reads the latest callback rather than the first.
        val currentOnInviteEffect by rememberUpdatedState(onInviteEffect)
        LaunchedEffect(inviteEffects) {
            inviteEffects.collect { effect ->
                when (effect) {
                    is InviteLinkViewModel.Effect.Share -> shareController.present(
                        Shareable.GroupInvite(
                            url = effect.url,
                            title = groupTitle,
                            imageUrl = pictureUrl.url,
                            imageCacheKey = cacheKey,
                        )
                    )
                    InviteLinkViewModel.Effect.Copied -> copied = true
                    else -> currentOnInviteEffect(effect)
                }
            }
        }
    }

    // Held above the gesture bar, and dropped once the keyboard is up: the keyboard already lifts
    // the whole sheet clear of it, so counting both would leave a gap over the keys.
    val navigationBar = WindowInsets.navigationBars
        .exclude(WindowInsets.ime)
        .asPaddingValues()
        .calculateBottomPadding()
    // The field sits inside the bottom scrim, which is as tall as the header, so the two edges fade
    // the same depth. iOS reaches the screen edge; here the gesture bar keeps it higher.
    val composerBottom = maxOf(headerHeight - fieldHeight, ComposerMinBottom, navigationBar)
    val recentChats = state.invitable.orEmpty()

    // No pinned header: the title bar and, once a chat is picked, the message field float over the
    // one list, which scrolls under both and fades out at each edge (node 10330:19387).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // Without this, a fling that reaches the top of the list carries on into the
                // sheet and dismisses it.
                .sheetResignmentBehavior(listState)
                .hazeSource(hazeState),
            state = listState,
            contentPadding = PaddingValues(
                top = headerHeight,
                bottom = if (state.showsComposer) {
                    maxOf(headerHeight, composerBottom + fieldHeight)
                } else {
                    headerHeight
                },
            ),
        ) {
            if (inviteUrl != null || inviteLink != null) {
                // Both of a group's links lead with the link itself; only the invite link has an
                // expiry and a joined count to put under it.
                val pillModifier = Modifier
                    .padding(horizontal = ListInset)
                    .padding(top = TileRowTop)
                val showsPill = inviteLink != null || subject is ShareToChatsSubject.Group
                if (inviteLink != null) {
                    item(key = "pill") { InviteLinkPill(link = inviteLink.link, modifier = pillModifier) }
                } else if (subject is ShareToChatsSubject.Group && inviteUrl != null) {
                    item(key = "pill") { PlainLinkPill(url = inviteUrl, modifier = pillModifier) }
                }
                item(key = "links") {
                    // Intrinsic height, so a tile whose label wraps stretches the others to
                    // match rather than standing taller than them.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(horizontal = ListInset)
                            .padding(
                                top = if (showsPill) TileRowBottom else TileRowTop,
                                bottom = TileRowBottom,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                    ) {
                        InviteLinkTile(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            enabled = !creating,
                            icon = R.drawable.ic_share_os,
                            label = stringResource(
                                if (inviteLink != null) R.string.action_shareLink else R.string.action_shareInviteLink
                            ),
                            onClick = {
                                onShare()
                                // The invite variant shares what the view model answers with, once
                                // there is a link to share.
                                if (inviteUrl != null) scope.launch {
                                    shareController.present(
                                        when (subject) {
                                            is ShareToChatsSubject.Group -> Shareable.GroupInvite(
                                                url = inviteUrl,
                                                title = group?.groupTitle,
                                                imageUrl = pictureUrl.url,
                                                imageCacheKey = picture
                                                    ?.cacheKeyForSize(SharePreviewImage.TARGET_PX),
                                            )
                                            is ShareToChatsSubject.User -> Shareable.Profile(
                                                userId = subject.userId,
                                                displayName = subject.displayName,
                                                username = subject.username,
                                                preview = subject.preview,
                                            )
                                            ShareToChatsSubject.Message -> Shareable.MessageLink(inviteUrl)
                                            is ShareToChatsSubject.Currency -> Shareable.MessageLink(inviteUrl)
                                            is ShareToChatsSubject.GroupInviteLink -> error("shared by effect")
                                        }
                                    )
                                }
                            },
                        )
                        InviteLinkTile(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            enabled = !creating,
                            icon = if (copied) R.drawable.ic_check else R.drawable.ic_copy,
                            label = stringResource(
                                when {
                                    copied -> R.string.action_inviteLinkCopied
                                    subject !is ShareToChatsSubject.Group -> R.string.action_copyLink
                                    else -> R.string.action_copyInviteLink
                                }
                            ),
                            onClick = {
                                onCopy()
                                // The invite variant has nothing to copy until the link exists.
                                if (inviteLink == null) copied = true
                            },
                        )
                        if (showsCardTile(subject, onShowCard)) {
                            InviteLinkTile(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                // The card encodes the link, so it waits for it as Share and Copy do.
                                enabled = !creating,
                                // The Scan tab's outline glyph, as the iOS tile draws it.
                                icon = R.drawable.ic_nav_tipcard,
                                label = stringResource(
                                    if (group != null) {
                                        R.string.action_showGroupCard
                                    } else {
                                        R.string.action_showProfileCard
                                    }
                                ),
                                onClick = onShowCard!!,
                                testTag = cardTileTag(subject),
                            )
                        }
                    }
                }
                if (recentChats.isNotEmpty()) {
                    item(key = "header") { RecentChatsHeader() }
                }
                items(recentChats, key = { it.chatId.toString() }) { chat ->
                    RecentChatRow(
                        chat = chat,
                        selected = chat.chatId in state.selection,
                        enabled = !state.sending && !creating,
                        onClick = { onToggle(chat.chatId) },
                    )
                }
                if (inviteLink != null) {
                    item(key = "footnote") {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = ListInset)
                                .padding(top = TileRowBottom),
                            text = stringResource(R.string.footnote_inviteLink),
                            style = CodeTheme.typography.textSmall,
                            color = White50,
                        )
                    }
                }
            }
        }

        // Always drawn, whether or not the field is up, so the edge stays still while the field
        // slides through it. A colour scrim like the top one, not a blur.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(headerHeight)
                .drawBehind {
                    val scrimHeight = size.height + ScrimTail.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, scrimColor),
                            startY = size.height - scrimHeight,
                            endY = size.height,
                        ),
                        topLeft = Offset(0f, size.height - scrimHeight),
                        size = size.copy(height = scrimHeight),
                    )
                },
        )

        AppBarWithTitle(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                // The Chats list's scrim: the surface colour fading out a little past the bar,
                // drawn behind it so the tail stays out of the bar's measured height.
                .drawBehind {
                    val scrimHeight = size.height + ScrimTail.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(scrimColor, Color.Transparent),
                            startY = 0f,
                            endY = scrimHeight,
                        ),
                        size = size.copy(height = scrimHeight),
                    )
                }
                .onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
            title = when (subject) {
                is ShareToChatsSubject.Currency ->
                    stringResource(R.string.title_shareCurrency, subject.name)
                is ShareToChatsSubject.User -> stringResource(R.string.title_shareProfileToChats)
                is ShareToChatsSubject.Message -> stringResource(R.string.title_shareMessageToChats)
                is ShareToChatsSubject.Group,
                is ShareToChatsSubject.GroupInviteLink -> stringResource(R.string.title_invitePeople)
            },
            titleAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(HeaderPadding),
            endContent = { AppBarDefaults.Close(hazeState = hazeState, onClick = onDismiss) },
        )

        // Slides rather than fades: a fading blur shows the unblurred list through it for the
        // length of the fade.
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = (inviteUrl != null || inviteLink != null) && state.showsComposer,
            enter = slideInVertically(ComposerSpring) { with(density) { ComposerTravel.roundToPx() } },
            exit = slideOutVertically(ComposerSpring) { with(density) { ComposerTravel.roundToPx() } },
        ) {
            // Inset in a wrapper: ChatInput clips to its pill before applying its modifier, so
            // padding passed to it would round the padded box while the glass drew square inside.
            Box(
                modifier = Modifier
                    .padding(horizontal = ListInset)
                    .padding(bottom = composerBottom),
            ) {
                InviteComposer(
                    modifier = Modifier
                        .onSizeChanged { fieldHeight = with(density) { it.height.toDp() } },
                    hazeState = hazeState,
                    sending = state.sending,
                    sendLabel = stringResource(
                        if (subject is ShareToChatsSubject.Group || inviteLink != null) R.string.action_invite else R.string.action_send
                    ),
                    onMessageChanged = onMessageChanged,
                    onInvite = onInvite,
                )
            }
        }
    }
}

/**
 * Node 10330:19400 — Share, Copy, or Show Profile Card: a glyph over its label, the tiles splitting
 * the row. The label wraps rather than truncates, so the tile grows past its minimum height
 * instead, and the row stretches its neighbours to the same height.
 */
@Composable
private fun InviteLinkTile(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .alpha(if (enabled) 1f else DimmedAlpha)
            .heightIn(min = CodeTheme.dimens.grid.x19)
            .clip(CodeTheme.shapes.extraSmall)
            .background(White05)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = CodeTheme.dimens.grid.x2, vertical = CodeTheme.dimens.grid.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = CodeTheme.colors.textMain,
            modifier = Modifier.size(CodeTheme.dimens.staticGrid.x6),
        )
        Text(
            text = label,
            style = CodeTheme.typography.textSmall,
            color = White50,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Node 12933:113 — the invite link in a pill, with its expiry and joined count under it; while the
 * tap on Share Invite Link makes the link, a spinner and "Creating link…" (node 12933:210). The link is cut in the
 * middle so the secret is never shown whole.
 */
@Composable
internal fun InviteLinkPill(link: InviteLinkState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)) {
        LinkPillFrame {
            when (link) {
                InviteLinkState.Creating -> {
                    CodeCircularProgressIndicator(modifier = Modifier.size(CodeTheme.dimens.staticGrid.x6 / 2))
                    Text(
                        text = stringResource(R.string.label_creatingInviteLink),
                        style = CodeTheme.typography.textMedium,
                        color = White50,
                    )
                }
                is InviteLinkState.Ready -> LinkPillText(elideInviteUrl(link.url))
                // A picked invite being fetched, or nothing to show as the sheet closes.
                InviteLinkState.None, InviteLinkState.Loading ->
                    CodeCircularProgressIndicator(modifier = Modifier.size(CodeTheme.dimens.staticGrid.x6 / 2))
            }
        }
        if (link is InviteLinkState.Ready) {
            Text(
                text = inviteMetaLine(link.expiresAt, link.useCount, link.maxUses),
                style = CodeTheme.typography.textSmall,
                color = White50,
            )
        }
    }
}

/** A group's plain `/chat` link in the invite link's pill, with no line under it: it has no expiry or count. */
@Composable
internal fun PlainLinkPill(url: String, modifier: Modifier = Modifier) {
    LinkPillFrame(modifier = modifier.fillMaxWidth()) {
        LinkPillText(plainLinkText(url))
    }
}

@Composable
internal fun LinkPillFrame(
    modifier: Modifier = Modifier,
    color: Color = White05,
    verticalPadding: Dp = CodeTheme.dimens.grid.x3,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CodeTheme.shapes.extraSmall)
            .background(color)
            .padding(horizontal = CodeTheme.dimens.grid.x3, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        content = content,
    )
}

@Composable
private fun LinkPillText(text: String) {
    Text(
        text = text,
        style = CodeTheme.typography.textMedium,
        color = CodeTheme.colors.textMain,
        maxLines = 1,
        // No wrap: a link breaks at its slashes and hyphens, and maxLines would clip what follows.
        // Still too wide for the pill, it loses its middle rather than its end.
        softWrap = false,
        overflow = TextOverflow.MiddleEllipsis,
    )
}

/** Node 10330:19568 — the list's label, an ordinary row that scrolls with the chats. */
@Composable
private fun RecentChatsHeader() {
    // Node 11633:2259: SemiBold 14, 16 above and below; the divider keeps the rows' insets.
    Column(modifier = Modifier.padding(horizontal = ListInset)) {
        Text(
            modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x3),
            text = stringResource(R.string.label_recentChats),
            style = CodeTheme.typography.textSmall,
            color = White50,
        )
        HorizontalDivider(color = CodeTheme.colors.divider)
    }
}

/**
 * Node 10330:19387 — one chat, 1:1 or group, picked or not. The whole row takes the tap. Its divider starts
 * at the name, not the avatar, as in node 10330:19585.
 */
@Composable
private fun RecentChatRow(
    chat: ConversationReference,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DimmedAlpha)
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .padding(horizontal = ListInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        ContactAvatar(
            image = chat.image,
            displayName = chat.name.orEmpty(),
            access = chat.avatarAccess,
            modifier = Modifier
                .requiredSize(CodeTheme.dimens.staticGrid.x5)
                .clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                // Node 11633:2262: 52 tall including the 1 divider.
                modifier = Modifier.height(CodeTheme.dimens.grid.x10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = chat.name.orEmpty().foldStyledLetters(),
                    // SemiBold 17.
                    style = CodeTheme.typography.textMedium.copy(fontSize = 17.sp),
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The same mark the token and currency pickers use, so this reads as the same kind
                // of list.
                Image(
                    modifier = Modifier.size(CodeTheme.dimens.staticGrid.x5),
                    painter = painterResource(
                        if (selected) R.drawable.ic_checked else R.drawable.ic_unchecked
                    ),
                    contentDescription = null,
                )
            }
            HorizontalDivider(color = CodeTheme.colors.divider)
        }
    }
}

/**
 * Node 10329:11963 — the message to go with the link, and the send. The field keeps its own text,
 * and the sheet's view model reads it at send time.
 *
 * The chat screen's own composer and glass, so typing here feels like typing in a chat, but
 * without its outline, since the Figma pill has none. A text label replaces the send arrow
 * because the link goes out whether or not a message is typed.
 */
@Composable
private fun InviteComposer(
    hazeState: HazeState,
    sending: Boolean,
    sendLabel: String,
    onMessageChanged: (String) -> Unit,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One fill, node 10329:11963's 46% #1E1E1E, over a blur of the rows beneath. ChatInput's own
    // fill is dropped because stacking it on the tint made the pill opaque and hid its edges.
    // The HazeBlurStyle builder is not a @Composable scope, so the theme read is hoisted above it.
    val backdrop = CodeTheme.colors.surface
    val glass = HazeBlurStyle {
        blurRadius(ComposerBlurRadius)
        backgroundColor(backdrop)
        colorEffects(listOf(HazeColorEffect.tint(ComposerFill)))
    }
    val message = rememberTextFieldState()
    LaunchedEffect(message) {
        snapshotFlow { message.text.toString() }.collect(onMessageChanged)
    }
    ChatInput(
        modifier = modifier.hazeBlur(HazeInput.Sources(hazeState), glass),
        containerColor = Color.Transparent,
        enabled = !sending,
        hint = stringResource(R.string.hint_addAMessage),
        state = message,
        submit = ChatInputSubmit.Action(
            label = sendLabel,
            busy = sending,
            perform = onInvite,
        ),
    )
}

// Node 10330:19387 and its children. Fixed rather than the theme's inset, which narrows on small
// screens, because the design holds these at 20 at every width.
private val ListInset = 20.dp
private val ComposerFill = Color(0x751E1E1E)
private val ComposerBlurRadius = 24.dp
private val HeaderPadding = 16.dp
// More above than below. AppBarWithTitle holds its height fixed and ignores HeaderPadding's bottom,
// while its top pushes the close button down past the bar's measured edge, which is where the list
// starts. iOS pads its header 16 on every side over the same 12 row inset; the extra 16 here stands
// in for that bottom padding.
private val TileRowTop = 28.dp
private val TileRowBottom = 12.dp


private val ComposerMinBottom = 8.dp

/** Share, Copy and Recent Chats while the first tap makes the link (node 12933:210). */
private const val DimmedAlpha = 0.4f

/** How far each edge scrim runs past its bar, as on the Chats list. */
private val ScrimTail = 48.dp
// Far enough that the field starts fully below the sheet's bottom edge.
private val ComposerTravel = 120.dp
// iOS's spring(response: 0.35): stiffness (2π / 0.35)² and its default damping.
private val ComposerSpring = spring<IntOffset>(dampingRatio = 0.825f, stiffness = 322f)

/**
 * Names can arrive in Unicode "styled" letters (e.g. U+1D5D4 mathematical sans-serif bold), which
 * the app font has no glyphs for, so those rows fell back to a system font. NFKC folds them to
 * the plain letters the app font draws; plain names are unchanged.
 */
internal fun String.foldStyledLetters(): String =
    java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFKC)
