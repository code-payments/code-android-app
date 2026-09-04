package com.flipcash.app.myaccount.internal.userprofile

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight.Companion.W600
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.core.R
import com.flipcash.services.models.SocialAccount
import com.flipcash.services.models.handle
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.SwipeAction
import com.getcode.ui.components.SwipeActionRow
import com.getcode.ui.components.text.SectionHeader
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import com.flipcash.app.core.util.abbreviatedLink
import com.flipcash.services.models.chat.BlobAccessContext
import com.getcode.theme.White05
import com.getcode.theme.extraSmall
import com.getcode.ui.core.verticalScrollStateGradient
import kotlinx.coroutines.delay

@Composable
internal fun UserProfileScreenContent(
    state: UserProfileViewModel.State,
    dispatch: (UserProfileViewModel.Event) -> Unit,
) {
    val inset = CodeTheme.dimens.inset
    val listState = rememberLazyListState()
    // The gesture-bar inset rides in contentPadding rather than on the list itself: as a layout
    // inset it shortened the viewport and cut the last card off at the padded edge, and it left the
    // list exactly one screen tall and so unable to scroll at all. As content padding the list still
    // fills the window and the last card scrolls clear of the bar, fading out under the gradient.
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            // Fades at both edges, so content reads as running past the app bar and the gesture bar
            // rather than stopping at them.
            .verticalScrollStateGradient(
                scrollState = listState,
                showAtStart = true,
                showAtEnd = true,
                isLongGradient = true,
            ),
        state = listState,
        contentPadding = PaddingValues(
            start = inset,
            end = inset,
            bottom = bottomInset + CodeTheme.dimens.grid.x3,
        ),
    ) {
        // Profile header — the account's public identity, read only. Editing the photo and the
        // display name lives in My Account, which is the single place those are changed.
        item(contentType = "profile_header") {
            ProfileHeader(
                displayName = state.displayName,
                profilePicture = state.profilePicture,
                username = state.username,
                tipCardLink = state.tipCardLink,
                usernameMinBalance = state.usernameMinBalance,
                onCopyLink = { dispatch(UserProfileViewModel.Event.CopyTipCardLink) },
            )
        }

        // Account Info section
        val hasAccountInfo = !state.publicKey.isNullOrEmpty() ||
                !state.accountId.isNullOrEmpty() ||
                !state.pushToken.isNullOrEmpty()

        if (hasAccountInfo) {
            item { SectionHeader(stringResource(R.string.title_sectionAccountInfo)) }

            val entries = buildList {
                if (!state.publicKey.isNullOrEmpty()) {
                    add(
                        Triple(
                            "Public Key",
                            state.publicKey,
                            UserProfileViewModel.Event.CopyPublicKey
                        )
                    )
                }
                if (!state.accountId.isNullOrEmpty()) {
                    add(
                        Triple(
                            "Account ID",
                            state.accountId,
                            UserProfileViewModel.Event.CopyAccountId
                        )
                    )
                }
                if (!state.pushToken.isNullOrEmpty()) {
                    add(
                        Triple(
                            "Push Token",
                            state.pushToken,
                            UserProfileViewModel.Event.CopyPushToken
                        )
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .background(Color.Transparent, CodeTheme.shapes.medium)
                        .clip(CodeTheme.shapes.medium)
                        .fillMaxWidth()
                ) {
                    val cornerRadius = 12.dp // medium
                    entries.forEachIndexed { index, (label, value, event) ->
                        val shape = when {
                            entries.size == 1 -> CodeTheme.shapes.medium
                            index == 0 -> RoundedCornerShape(
                                topStart = cornerRadius,
                                topEnd = cornerRadius,
                            )
                            index == entries.lastIndex -> RoundedCornerShape(
                                bottomStart = cornerRadius,
                                bottomEnd = cornerRadius,
                            )
                            else -> RectangleShape
                        }
                        CopySwipeRow(
                            onCopy = { dispatch(event) },
                            stateKey = value,
                        ) {
                            CopyableTextEntry(
                                modifier = Modifier
                                    .cardStyle(shape = shape),
                                label = label,
                                value = value
                            )
                        }
                        if (index < entries.lastIndex) {
                            HorizontalDivider(
                                color = CodeTheme.colors.divider,
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }
            }
        }

        // Phone section
        item(contentType = "section_header") { SectionHeader(stringResource(R.string.title_sectionPhone)) }
        item(contentType = "contact_method") {
            val phone = state.phone
            when {
                phone == null -> CardRow {
                    AddContactMethodRow(
                        label = stringResource(R.string.action_addPhoneNumber),
                        onClick = { dispatch(UserProfileViewModel.Event.ConnectPhoneClicked) },
                    )
                }
                // Only verified contacts can be unlinked from the server.
                phone.verified -> SwipeActionRow(
                    onDelete = { dispatch(UserProfileViewModel.Event.UnlinkPhoneClicked) },
                    stateKey = phone.value,
                    resetOnDismiss = true,
                ) {
                    ContactMethodRow(
                        value = phone.value,
                        verified = true,
                        linkedForPayment = state.phoneLinkedForPayment,
                        onRowClick = { dispatch(UserProfileViewModel.Event.ReplacePhoneClicked) },
                    )
                }
                else -> ContactMethodRow(
                    value = phone.value,
                    verified = false,
                    linkedForPayment = state.phoneLinkedForPayment,
                    onRowClick = { dispatch(UserProfileViewModel.Event.ReplacePhoneClicked) },
                )
            }
        }

        // Email section
        item(contentType = "section_header") { SectionHeader(stringResource(R.string.title_sectionEmail)) }
        item(contentType = "contact_method") {
            val email = state.email
            when {
                email == null -> CardRow {
                    AddContactMethodRow(
                        label = stringResource(R.string.action_addEmailAddress),
                        onClick = { dispatch(UserProfileViewModel.Event.ConnectEmailClicked) },
                    )
                }

                email.verified -> SwipeActionRow(
                    onDelete = { dispatch(UserProfileViewModel.Event.UnlinkEmailClicked) },
                    stateKey = email.value,
                    resetOnDismiss = true,
                ) {
                    ContactMethodRow(
                        value = email.value,
                        verified = true,
                        linkedForPayment = false,
                        onRowClick = { dispatch(UserProfileViewModel.Event.ReplaceEmailClicked) },
                    )
                }

                else -> SwipeActionRow(
                    onDelete = { dispatch(UserProfileViewModel.Event.UnlinkEmailClicked) },
                    stateKey = email.value,
                    resetOnDismiss = true,
                ) {
                    ContactMethodRow(
                        value = email.value,
                        verified = false,
                        linkedForPayment = false,
                        onRowClick = { dispatch(UserProfileViewModel.Event.ReplaceEmailClicked) },
                    )
                }
            }
        }

        // Social Accounts section
        item(contentType = "section_header") { SectionHeader(stringResource(R.string.title_sectionSocialAccounts)) }
        if (state.socialAccounts.isEmpty()) {
          item(contentType = "empty_state") {
                CardRow {
                    Text(
                        text = stringResource(R.string.subtitle_noSocialAccounts),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textSecondary,
                        modifier = Modifier.padding(
                            horizontal = CodeTheme.dimens.inset,
                            vertical = CodeTheme.dimens.grid.x3,
                        ),
                    )
                }
            }
        } else {
            items(state.socialAccounts, key = { it.id }, contentType = { "social_account" }) { account ->
                when (account) {
                    is SocialAccount.TwitterX -> {
                        SwipeActionRow(
                            onDelete = {
                                dispatch(
                                    UserProfileViewModel.Event.UnlinkSocialAccountClicked(account)
                                )
                            },
                            stateKey = account.id,
                            resetOnDismiss = true,
                        ) {
                            SocialAccountRow(account = account)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    displayName: String,
    profilePicture: MediaItem?,
    username: String?,
    tipCardLink: String?,
    usernameMinBalance: String,
    onCopyLink: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CodeTheme.dimens.grid.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Avatar, name, handle and link are one identity block, so they sit tight together.
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        ContactAvatar(
            image = profilePicture,
            displayName = displayName,
            // This screen is the account's own public profile, so it owns the picture's blobs.
            access = BlobAccessContext.Owned,
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape),
        )
        Text(
            text = displayName.takeIf { it.isNotEmpty() }
                ?: stringResource(R.string.subtitle_noDisplayName),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )
        if (username != null) {
            Text(
                text = "@$username",
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textSecondary,
            )
        } else {
            // The same flag the entry screen's rejection dialog quotes, so the two never name
            // different thresholds. Stated rather than actionable: claiming happens on the You tab.
            Text(
                text = stringResource(R.string.title_usernameUpsell),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(
                    R.string.subtitle_usernameUpsellLocked,
                    usernameMinBalance,
                ),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
        }
        if (tipCardLink != null) {
            TipCardLinkRow(
                link = tipCardLink,
                onCopy = onCopyLink,
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
            )
        }
    }
}

/**
 * The account's public link, tap to copy — the same row the You tab shows, so the link reads and
 * behaves identically in both places. The clipboard gives no feedback of its own, so the copy glyph
 * holds a checkmark for a moment after the tap.
 */
@Composable
private fun TipCardLinkRow(
    link: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Bumped rather than latched so a second tap restarts the hold instead of being swallowed.
    var copyToken by remember { mutableIntStateOf(0) }
    val copied = copyToken > 0

    LaunchedEffect(copyToken) {
        if (copyToken > 0) {
            delay(CopyConfirmationMillis)
            copyToken = 0
        }
    }

    Row(
        modifier = modifier
            .height(CodeTheme.dimens.grid.x8)
            .clip(CodeTheme.shapes.extraSmall)
            .background(White05)
            .clickable {
                onCopy()
                copyToken++
            }
            .padding(horizontal = CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(R.drawable.ic_chain_link),
            contentDescription = null,
            tint = Color.White,
        )
        Text(
            text = link.abbreviatedLink(),
            style = CodeTheme.typography.textSmall.copy(fontSize = 15.sp),
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Crossfade(
            targetState = copied,
            animationSpec = tween(CopyIconFadeMillis, easing = EaseInOut),
            label = "copyConfirmation",
        ) { showCheck ->
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(
                    if (showCheck) R.drawable.ic_check_circle else R.drawable.ic_copy
                ),
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

/** How long the copy button holds the checkmark before reverting (iOS: 1.5s). */
private const val CopyConfirmationMillis = 1_500L

/** Cross-fade between the copy and confirmation glyphs (iOS: 0.15s ease-in-out). */
private const val CopyIconFadeMillis = 150

/** Non-swipeable card wrapper — just visual styling. */
@Composable
private fun CardRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.cardStyle(),
    ) {
        content()
    }
}

@Composable
private fun Modifier.cardStyle(shape: Shape = CodeTheme.shapes.medium): Modifier {
    return this
        .background(CodeTheme.colors.bannerThemed, shape)
        .clip(shape)
        .fillMaxWidth()
}

@Composable
private fun ContactMethodRow(
    value: String,
    verified: Boolean,
    linkedForPayment: Boolean,
    onRowClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .cardStyle()
            .clickable { onRowClick() }
            .padding(
                horizontal = CodeTheme.dimens.inset,
                vertical = CodeTheme.dimens.grid.x3,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = value,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
        )
        // Match the linked-payment icon's height to the status badge (the badge's
        // natural height drives the row via IntrinsicSize.Min).
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            if (linkedForPayment) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(CodeTheme.colors.success.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_linked_payment_method),
                        contentDescription = stringResource(R.string.subtitle_linkedForPayments),
                        tint = CodeTheme.colors.success,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            StatusBadge(
                label = if (verified) {
                    stringResource(R.string.label_verified)
                } else {
                    stringResource(R.string.label_unverified)
                },
                color = if (verified) CodeTheme.colors.success else CodeTheme.colors.warning,
                icon = if (verified) Icons.Default.Check else null,
            )
        }
    }
}

@Composable
private fun StatusBadge(
    label: String,
    color: Color,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(
                horizontal = CodeTheme.dimens.grid.x2,
                vertical = CodeTheme.dimens.grid.x1,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = CodeTheme.typography.caption,
            color = color,
        )
    }
}

@Composable
private fun SocialAccountRow(
    account: SocialAccount.TwitterX,
) {
    Row(
        modifier = Modifier
            .cardStyle()
            .padding(
                horizontal = CodeTheme.dimens.inset,
                vertical = CodeTheme.dimens.grid.x3,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = account.handle,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
            Text(
                text = account.name,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun AddContactMethodRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .fillMaxWidth()
            .padding(
                horizontal = CodeTheme.dimens.inset,
                vertical = CodeTheme.dimens.grid.x3,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = CodeTheme.colors.textSecondary,
            modifier = Modifier
                .size(CodeTheme.dimens.staticGrid.x5)
                .padding(end = CodeTheme.dimens.grid.x2),
        )
        Text(
            text = label,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun CopySwipeRow(
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    stateKey: Any? = null,
    content: @Composable () -> Unit,
) {
    SwipeActionRow(
        actions = listOf(
            SwipeAction(
                background = CodeTheme.colors.brandLight,
                onTriggered = onCopy,
                resetOnDismiss = true,
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x5),
                )
            }
        ),
        modifier = modifier,
        stateKey = stateKey,
        content = content,
    )
}

@Composable
private fun CopyableTextEntry(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(
                horizontal = CodeTheme.dimens.inset,
                vertical = CodeTheme.dimens.grid.x3,
            ),
    ) {
        Text(
            text = label,
            style = CodeTheme.typography.caption.copy(fontWeight = W600),
            color = CodeTheme.colors.textMain,
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = value,
            style = CodeTheme.typography.caption,
            color = CodeTheme.colors.textSecondary,
            overflow = TextOverflow.MiddleEllipsis,
            maxLines = 1,
        )
    }
}
