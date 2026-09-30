package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.core.ui.shimmer
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LinkCardResolution
import com.flipcash.shared.chat.models.LocalLinkCardResolution
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.services.models.chat.BlobAccessContext
import com.getcode.opencode.model.core.ID
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.getcode.theme.White05
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

/**
 * The share-profile widget as it sits in a transcript. The widget carries only [username], which
 * decides whose profile the card shows and shares: the signed-in user's own comes from the session,
 * anyone else's from the same by-handle lookup a person link card uses.
 *
 * Until that lookup answers, and if it fails, the name is [username] and the avatar its initials.
 * Share needs the profile's id, so it stays off until the profile has resolved.
 */
@Composable
internal fun ShareProfileWidgetBubble(
    username: String,
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
    onShare: ((ChatAction.ShareProfile) -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    attention: () -> Float = { 0f },
) {
    val profile = rememberWidgetProfile(username)
    ShareProfileWidgetBubble(
        displayName = (profile as? WidgetProfile.Resolved)?.displayName ?: username,
        username = username,
        profilePicture = (profile as? WidgetProfile.Resolved)?.picture,
        userId = (profile as? WidgetProfile.Resolved)?.userId,
        loading = profile is WidgetProfile.Loading,
        isFromSelf = isFromSelf,
        position = position,
        maxWidth = maxWidth,
        onShare = (profile as? WidgetProfile.Resolved)?.let { resolved ->
            onShare?.let { share ->
                {
                    share(
                        ChatAction.ShareProfile(
                            userId = resolved.userId,
                            username = username,
                            displayName = resolved.displayName,
                        )
                    )
                }
            }
        },
        modifier = modifier,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        attention = attention,
    )
}

/** Whose card it is, as far as it is known. */
internal sealed interface WidgetProfile {
    data object Loading : WidgetProfile
    data object Failed : WidgetProfile
    data class Resolved(
        val userId: ID,
        val displayName: String?,
        val picture: MediaItem?,
    ) : WidgetProfile
}

/** [displayName] and [picture] of whoever [username] names, from the session or the lookup. */
@Composable
private fun rememberWidgetProfile(username: String): WidgetProfile {
    val userManager = LocalUserManager.current
    val own: UserProfile? = userManager?.state?.collectAsState()?.value?.userProfile
    val ownId = userManager?.accountId
    if (own != null && ownId != null && own.username.equals(username.removePrefix("@"), ignoreCase = true)) {
        return WidgetProfile.Resolved(
            userId = own.userId ?: ownId,
            displayName = own.displayName.takeIf { it.isNotBlank() },
            picture = own.profilePicture,
        )
    }

    val resolution = LocalLinkCardResolution.current
    val revision by resolution.revision.collectAsState()
    val identity = remember(username) { LinkCard.User.Identity.ByUsername(username) }
    val seed = remember(identity) { userCard(identity, LinkCard.User.State.Loading) }
    var card by remember(identity) { mutableStateOf(resolution.peek(seed) ?: seed) }
    LaunchedEffect(identity, revision) { card = resolution.resolve(seed) }

    return when (val state = (card as? LinkCard.User)?.state) {
        is LinkCard.User.State.Resolved -> WidgetProfile.Resolved(
            userId = state.userId,
            displayName = state.profile.displayName.takeIf { it.isNotBlank() },
            picture = state.profile.profilePicture,
        )
        LinkCard.User.State.NotFound -> WidgetProfile.Failed
        else -> WidgetProfile.Loading
    }
}

private fun userCard(identity: LinkCard.User.Identity, state: LinkCard.User.State) =
    LinkCard.User(url = "", start = 0, end = 0, identity = identity, state = state)

/** Node 10588:1979. */
@Composable
internal fun ShareProfileWidgetBubble(
    displayName: String,
    username: String,
    profilePicture: MediaItem?,
    userId: ID?,
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
    onShare: (() -> Unit)?,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    attention: () -> Float = { 0f },
) {
    val shape = bubbleShape(position, isFromSelf)
    val width = minOf(maxWidth, ShareProfileWidgetDefaults.WIDTH)
    Bubble(
        isFromSelf = isFromSelf,
        position = position,
        minWidth = width,
        maxWidth = width,
        bare = true,
        shape = shape,
        horizontalPadding = CodeTheme.dimens.staticGrid.x3,
        verticalPadding = CodeTheme.dimens.staticGrid.x3,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        modifier = modifier
            .background(White05, shape)
            .border(CodeTheme.dimens.border, ShareProfileWidgetDefaults.STROKE, shape),
        attention = attention,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ShareProfileWidgetDefaults.SECTION_GAP),
        ) {
            Column(
                modifier = Modifier.widthIn(max = ShareProfileWidgetDefaults.IDENTITY_WIDTH),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x3),
            ) {
                ContactAvatar(
                    image = profilePicture,
                    displayName = displayName,
                    access = BlobAccessContext.profile(userId),
                    modifier = Modifier
                        .size(ShareProfileWidgetDefaults.AVATAR)
                        .border(CodeTheme.dimens.border, GroupInviteCardDefaults.STROKE, CircleShape)
                        .clip(CircleShape),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ShareProfileWidgetDefaults.LINE_GAP),
                ) {
                    Text(
                        text = displayName,
                        style = CodeTheme.typography.textLarge,
                        color = CodeTheme.colors.textMain,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (loading) Modifier.shimmer(RoundedCornerShape(4.dp)) else Modifier,
                    )
                    Text(
                        text = "@$username",
                        style = CodeTheme.typography.caption,
                        color = CodeTheme.colors.textMain.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CodeTheme.dimens.staticGrid.x11),
                onClick = { onShare?.invoke() },
                enabled = onShare != null,
                buttonState = ButtonState.Filled,
                shape = RoundedCornerShape(ShareProfileWidgetDefaults.BUTTON_RADIUS),
                overrideContentPadding = true,
                contentPadding = PaddingValues(0.dp),
            ) {
                // CodeButton stacks its content, so the pair needs a row of its own.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share_os),
                        contentDescription = null,
                        modifier = Modifier.size(CodeTheme.dimens.staticGrid.x5),
                    )
                    Text(
                        modifier = Modifier.padding(start = CodeTheme.dimens.staticGrid.x1),
                        text = stringResource(R.string.action_shareProfile),
                        style = CodeTheme.typography.textMedium,
                    )
                }
            }
        }
    }
}

internal object ShareProfileWidgetDefaults {
    /** The card's width in the design. A narrower transcript shrinks it. */
    val WIDTH = 290.dp
    val IDENTITY_WIDTH = 210.dp
    val AVATAR = 64.dp
    val SECTION_GAP = 18.dp
    val BUTTON_RADIUS = 6.dp
    val LINE_GAP = 2.dp

    /** The card's outline: white at 8%, the nearest token to the design's 7%. */
    val STROKE = Color.White.copy(alpha = 0.08f)
}

/** A lookup that answers with [state], for the previews and the screenshot test. */
internal class FixedUserResolution(private val state: LinkCard.User.State) : LinkCardResolution {
    override val revision: StateFlow<Int> = MutableStateFlow(0)
    override fun peek(card: LinkCard): LinkCard? = (card as? LinkCard.User)?.copy(state = state)
    override suspend fun resolve(card: LinkCard): LinkCard = peek(card) ?: card
}

@Composable
internal fun PreviewShareProfileWidget(state: LinkCard.User.State, username: String) {
    CompositionLocalProvider(LocalLinkCardResolution provides FixedUserResolution(state)) {
        ShareProfileWidgetBubble(
            username = username,
            isFromSelf = false,
            position = BubblePosition.Solo,
            maxWidth = 320.dp,
            onShare = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Someone else's profile, resolved by handle. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_ShareProfileWidgetBubble() {
    PreviewShareProfileWidget(
        state = previewUserResolved(name = "Brad Burnham", handle = "@brad_burnham_2", blurHash = null),
        username = "brad_burnham_2",
    )
}

/** The signed-in user's own profile, which needs no lookup. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_ShareProfileWidgetBubble_Own() {
    ShareProfileWidgetBubble(
        displayName = "Brad Burnham",
        username = "brad_burnham_2",
        profilePicture = null,
        userId = null,
        isFromSelf = false,
        position = BubblePosition.Solo,
        maxWidth = 320.dp,
        onShare = {},
        modifier = Modifier.padding(16.dp),
    )
}

/** The lookup found nobody or failed: the handle stands in for the name, and Share is off. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_ShareProfileWidgetBubble_LookupFailed() {
    PreviewShareProfileWidget(state = LinkCard.User.State.NotFound, username = "brad_burnham_2")
}
