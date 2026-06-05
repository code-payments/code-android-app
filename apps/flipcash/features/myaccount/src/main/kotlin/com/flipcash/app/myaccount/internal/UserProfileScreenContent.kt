package com.flipcash.app.myaccount.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight.Companion.W600
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.core.R
import com.flipcash.services.models.SocialAccount
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.SwipeAction
import com.getcode.ui.components.SwipeActionRow
import com.getcode.ui.components.text.SectionHeader

@Composable
internal fun UserProfileScreenContent(
    state: UserProfileViewModel.State,
    dispatch: (UserProfileViewModel.Event) -> Unit,
) {
    val inset = CodeTheme.dimens.inset
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = inset),
    ) {
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

        // Display Name section
        item(contentType = "section_header") { SectionHeader(stringResource(R.string.title_sectionDisplayName)) }
        item(contentType = "profile_value") {
            val name = state.displayName
            if (!name.isNullOrEmpty()) {
                CardRow { ProfileValueRow(value = name) }
            } else {
                CardRow {
                    Text(
                        text = stringResource(R.string.subtitle_noDisplayName),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textSecondary,
                        modifier = Modifier.padding(
                            horizontal = CodeTheme.dimens.inset,
                            vertical = CodeTheme.dimens.grid.x3,
                        ),
                    )
                }
            }
        }

        // Phone section
        item(contentType = "section_header") { SectionHeader(stringResource(R.string.title_sectionPhone)) }
        item(contentType = "contact_method") {
            if (state.phoneNumber != null) {
                SwipeActionRow(
                    onDelete = { dispatch(UserProfileViewModel.Event.UnlinkPhoneClicked) },
                    stateKey = state.phoneNumber,
                    resetOnDismiss = true,
                ) {
                    ContactMethodRow(
                        value = state.phoneNumber,
                        subtitle = if (state.phoneLinkedForPayment) {
                            stringResource(R.string.subtitle_linkedForPayments)
                        } else null,
                        onRowClick = { dispatch(UserProfileViewModel.Event.ReplacePhoneClicked) },
                    )
                }
            } else {
                CardRow {
                    AddContactMethodRow(
                        label = stringResource(R.string.action_addPhoneNumber),
                        onClick = { dispatch(UserProfileViewModel.Event.ConnectPhoneClicked) },
                    )
                }
            }
        }

        // Email section
        item(contentType = "section_header") { SectionHeader(stringResource(R.string.title_sectionEmail)) }
        item(contentType = "contact_method") {
            if (state.emailAddress != null) {
                SwipeActionRow(
                    onDelete = { dispatch(UserProfileViewModel.Event.UnlinkEmailClicked) },
                    stateKey = state.emailAddress,
                    resetOnDismiss = true,
                ) {
                    ContactMethodRow(
                        value = state.emailAddress,
                        subtitle = null,
                        onRowClick = { dispatch(UserProfileViewModel.Event.ReplaceEmailClicked) },
                    )
                }
            } else {
                CardRow {
                    AddContactMethodRow(
                        label = stringResource(R.string.action_addEmailAddress),
                        onClick = { dispatch(UserProfileViewModel.Event.ConnectEmailClicked) },
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
private fun ProfileValueRow(value: String) {
    Text(
        text = value,
        style = CodeTheme.typography.textMedium,
        color = CodeTheme.colors.textMain,
        modifier = Modifier.padding(
            horizontal = CodeTheme.dimens.inset,
            vertical = CodeTheme.dimens.grid.x3,
        ),
    )
}

@Composable
private fun ContactMethodRow(
    value: String,
    subtitle: String?,
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
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = value,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
            }
        }
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
                text = "@${account.username}",
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
