package com.flipcash.app.myaccount.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import com.flipcash.core.R
import com.flipcash.services.models.SocialAccount
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.text.SectionHeader
import androidx.compose.foundation.clickable

@Composable
internal fun UserProfileScreenContent(
    state: UserProfileViewModel.State,
    dispatch: (UserProfileViewModel.Event) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Display Name section
        item(contentType = "section_header") { SectionHeader(stringResource(R.string.title_sectionDisplayName)) }
        item(contentType = "profile_value") {
            val name = state.displayName
            if (!name.isNullOrEmpty()) {
                ProfileValueRow(value = name)
            } else {
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

        // Phone section
        item(contentType = "section_header") { SectionHeader(stringResource(R.string.title_sectionPhone)) }
        item(contentType = "contact_method") {
            if (state.phoneNumber != null) {
                ContactMethodRow(
                    value = state.phoneNumber,
                    subtitle = if (state.phoneLinkedForPayment) {
                        stringResource(R.string.subtitle_linkedForPayments)
                    } else null,
                    onRowClick = { dispatch(UserProfileViewModel.Event.ReplacePhoneClicked) },
                    onDeleteClick = { dispatch(UserProfileViewModel.Event.UnlinkPhoneClicked) },
                )
            } else {
                AddContactMethodRow(
                    label = stringResource(R.string.action_addPhoneNumber),
                    onClick = { dispatch(UserProfileViewModel.Event.ConnectPhoneClicked) },
                )
            }
        }

        // Email section
        item(contentType = "section_header") { SectionHeader(stringResource(R.string.title_sectionEmail)) }
        item(contentType = "contact_method") {
            if (state.emailAddress != null) {
                ContactMethodRow(
                    value = state.emailAddress,
                    subtitle = null,
                    onRowClick = { dispatch(UserProfileViewModel.Event.ReplaceEmailClicked) },
                    onDeleteClick = { dispatch(UserProfileViewModel.Event.UnlinkEmailClicked) },
                )
            } else {
                AddContactMethodRow(
                    label = stringResource(R.string.action_addEmailAddress),
                    onClick = { dispatch(UserProfileViewModel.Event.ConnectEmailClicked) },
                )
            }
        }

        // Social Accounts section
        item(contentType = "section_header") { SectionHeader(stringResource(R.string.title_sectionSocialAccounts)) }
        if (state.socialAccounts.isEmpty()) {
            item(contentType = "empty_state") {
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
        } else {
            items(state.socialAccounts, key = { (it as? SocialAccount.TwitterX)?.id ?: it }, contentType = { "social_account" }) { account ->
                when (account) {
                    is SocialAccount.TwitterX -> SocialAccountRow(
                        account = account,
                        onDeleteClick = {
                            dispatch(UserProfileViewModel.Event.UnlinkSocialAccountClicked(account))
                        },
                    )
                }
            }
        }
    }
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
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable { onRowClick() }
            .fillMaxWidth()
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
        IconButton(onClick = onDeleteClick) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = CodeTheme.colors.textSecondary,
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x5),
            )
        }
    }
}

@Composable
private fun SocialAccountRow(
    account: SocialAccount.TwitterX,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
        IconButton(onClick = onDeleteClick) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = CodeTheme.colors.textSecondary,
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x5),
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
