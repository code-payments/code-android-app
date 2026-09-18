package com.flipcash.app.login.internal.accounts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.flipcash.features.login.R
import com.getcode.theme.CodeTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AccountRow(
    account: AccountSelectionViewModel.AccountUiModel,
    isCurrent: Boolean,
    relativeCreationDate: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = !isCurrent,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        // The tick is laid out for every row so the names line up; it is only drawn, and only
        // announced, for the account the user is signed into.
        Icon(
            modifier = Modifier.size(CodeTheme.dimens.grid.x4),
            imageVector = Icons.Default.Check,
            contentDescription = if (isCurrent) {
                stringResource(R.string.subtitle_currentAccount)
            } else {
                null
            },
            tint = if (isCurrent) CodeTheme.colors.textMain else Color.Transparent,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        ) {
            Text(
                text = account.name,
                // textLarge is the primary line on the app's other list rows (Blocklist).
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.subtitle_accountCreated, relativeCreationDate),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
            Text(
                text = account.ownerAddress,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }

        // Order matters: a resolved balance wins, then the backend's own "no such account", then
        // our inability to ask. An account whose balance we could not fetch must not be reported as
        // not found — the two say very different things to someone checking their own wallet.
        when {
            account.balance != null -> Text(
                text = account.balance.formatted(),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )

            account.notFound -> Text(
                text = stringResource(R.string.subtitle_accountNotFound),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )

            account.balanceUnavailable -> Text(
                text = stringResource(R.string.subtitle_balanceUnavailable),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}
