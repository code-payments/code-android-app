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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.flipcash.app.core.media.rememberMediaUrl
import com.flipcash.app.core.share.SharePreviewImage
import com.flipcash.app.core.share.TipCodePreview
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.GroupInviteViewModel
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
 * What [GroupInviteSheet] hands out: a group's invite link, or a person's profile link. The two
 * differ in their copy, in what the Share tile gives the system share sheet, and in whether the
 * group's analytics fire; the rest of the sheet is the same.
 */
internal sealed interface ShareToChatsSubject {
    /**
     * @param group what is being invited to, for the group's name and picture. The sheet only opens
     * on a group, so null is the frame before the subject resolves.
     */
    data class Group(val group: ChatSubject.Group?) : ShareToChatsSubject

    /**
     * @param preview the tip-code image for the system share sheet's card, when one is rendered.
     */
    data class User(
        val userId: ID,
        val username: String?,
        val displayName: String?,
        val preview: TipCodePreview?,
    ) : ShareToChatsSubject
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
 * @param onShowCard opens the person's profile card, as a third tile beside Share and Copy. Null
 * for a group, which has no card.
 */
@Composable
internal fun GroupInviteSheet(
    inviteUrl: String?,
    subject: ShareToChatsSubject,
    state: GroupInviteViewModel.State,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onToggle: (ChatId) -> Unit,
    onMessageChanged: (String) -> Unit,
    onInvite: () -> Unit,
    onDismiss: () -> Unit,
    onShowCard: (() -> Unit)? = null,
) {
    val shareController = LocalShareController.current
    val scope = rememberCoroutineScope()

    // Resolved here rather than in the share controller: the stored download URL expires, and
    // re-minting it needs the chat's profile to authorize it — neither of which the shareable
    // module can see. Same size and access as the avatar the conversation bar draws.
    val group = (subject as? ShareToChatsSubject.Group)?.group
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
            if (inviteUrl != null) {
                item(key = "links") {
                    // Intrinsic height, so a tile whose label wraps stretches the others to
                    // match rather than standing taller than them.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(horizontal = ListInset)
                            .padding(top = TileRowTop, bottom = TileRowBottom),
                        horizontalArrangement = Arrangement.spacedBy(TileGap),
                    ) {
                        InviteLinkTile(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            icon = R.drawable.ic_share_os,
                            label = stringResource(R.string.action_shareInviteLink),
                            onClick = {
                                onShare()
                                scope.launch {
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
                                        }
                                    )
                                }
                            },
                        )
                        InviteLinkTile(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            icon = if (copied) R.drawable.ic_check else R.drawable.ic_copy,
                            label = stringResource(
                                when {
                                    copied -> R.string.action_inviteLinkCopied
                                    subject is ShareToChatsSubject.User -> R.string.action_copyLink
                                    else -> R.string.action_copyInviteLink
                                }
                            ),
                            onClick = {
                                onCopy()
                                copied = true
                            },
                        )
                        if (onShowCard != null) {
                            InviteLinkTile(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                // The Scan tab's outline glyph, as the iOS tile draws it.
                                icon = R.drawable.ic_nav_tipcard,
                                label = stringResource(R.string.action_showProfileCard),
                                onClick = onShowCard,
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
                        enabled = !state.sending,
                        onClick = { onToggle(chat.chatId) },
                    )
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
            title = stringResource(
                if (subject is ShareToChatsSubject.User) R.string.title_shareProfileToChats else R.string.title_invitePeople
            ),
            titleAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(HeaderPadding),
            endContent = { AppBarDefaults.Close(hazeState = hazeState, onClick = onDismiss) },
        )

        // Slides rather than fades: a fading blur shows the unblurred list through it for the
        // length of the fade.
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = inviteUrl != null && state.showsComposer,
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
                        if (subject is ShareToChatsSubject.User) R.string.action_send else R.string.action_invite
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
) {
    Column(
        modifier = modifier
            .heightIn(min = TileMinHeight)
            .clip(TileShape)
            .background(White05)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TilePaddingHorizontal, vertical = TilePaddingVertical),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TileIconGap, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = CodeTheme.colors.textMain,
            modifier = Modifier.size(TileIconSize),
        )
        Text(
            text = label,
            style = CodeTheme.typography.textSmall,
            color = White50,
            textAlign = TextAlign.Center,
        )
    }
}

/** Node 10330:19568 — the list's label, an ordinary row that scrolls with the chats. */
@Composable
private fun RecentChatsHeader() {
    Column(modifier = Modifier.padding(horizontal = ListInset)) {
        Text(
            modifier = Modifier.padding(vertical = HeaderRowVertical),
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
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .padding(horizontal = ListInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        ContactAvatar(
            image = chat.image,
            displayName = chat.name.orEmpty(),
            access = chat.avatarAccess,
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
                    text = chat.name.orEmpty(),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The same mark the token and currency pickers use, so this reads as the same kind
                // of list.
                Image(
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
private val HeaderRowVertical = 16.dp
// More above than below. AppBarWithTitle holds its height fixed and ignores HeaderPadding's bottom,
// while its top pushes the close button down past the bar's measured edge, which is where the list
// starts. iOS pads its header 16 on every side over the same 12 row inset; the extra 16 here stands
// in for that bottom padding.
private val TileRowTop = 28.dp
private val TileRowBottom = 12.dp
private val TileGap = 10.dp
private val TileMinHeight = 95.dp
private val TileShape = RoundedCornerShape(6.dp)
private val TilePaddingHorizontal = 8.dp
private val TilePaddingVertical = 16.dp
private val TileIconSize = 28.dp
private val TileIconGap = 6.dp

// Node 10330:19572: the gap between avatar and name, which is also where the row's divider starts.
private val RowGap = 16.dp

private val ComposerMinBottom = 8.dp

/** How far each edge scrim runs past its bar, as on the Chats list. */
private val ScrimTail = 48.dp
// Far enough that the field starts fully below the sheet's bottom edge.
private val ComposerTravel = 120.dp
// iOS's spring(response: 0.35): stiffness (2π / 0.35)² and its default damping.
private val ComposerSpring = spring<IntOffset>(dampingRatio = 0.825f, stiffness = 322f)
