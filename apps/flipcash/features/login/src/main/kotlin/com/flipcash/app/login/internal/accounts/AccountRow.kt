package com.flipcash.app.login.internal.accounts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.shimmer
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
            // iOS pads each row 20pt on every side; the list's contentPadding supplies the sides.
            .padding(vertical = CodeTheme.dimens.grid.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        // The same mark as the region, token and group-invite pickers.
        Image(
            painter = painterResource(
                if (isCurrent) R.drawable.ic_checked else R.drawable.ic_unchecked
            ),
            contentDescription = if (isCurrent) {
                stringResource(R.string.subtitle_currentAccount)
            } else {
                null
            },
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        ) {
            // iOS sets the name and the balance on one 16pt line (appTextMedium), bottom-aligned
            // 10pt apart, with 5pt under the line on top of the column's spacing.
            Row(
                modifier = Modifier.padding(bottom = CodeTheme.dimens.grid.x1),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            ) {
                Text(
                    // Without a balance to push to the end, the Not Found badge follows the name
                    // directly, as on iOS.
                    modifier = Modifier.weight(1f, fill = !account.notFound),
                    text = account.name,
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Order matters: a resolved balance wins, then the backend's own "no such
                // account", then our inability to ask, and only then is the fetch still in
                // flight. An account whose balance we could not fetch must not be reported as not
                // found — the two say very different things to someone checking their own wallet.
                when {
                    account.balance != null -> Text(
                        text = account.balance.formatted(),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textMain,
                    )

                    account.notFound -> NotFoundBadge()

                    account.balanceUnavailable -> Text(
                        text = stringResource(R.string.subtitle_balanceUnavailable),
                        style = CodeTheme.typography.caption,
                        color = CodeTheme.colors.textSecondary,
                    )

                    // The skeleton the discovery list uses, sized to a short balance on the
                    // textMedium line. It carries no semantics: TalkBack announces the balance
                    // when it lands.
                    else -> Box(
                        Modifier
                            .size(width = 60.dp, height = 14.dp)
                            .shimmer()
                    )
                }
            }
            Text(
                text = stringResource(R.string.subtitle_accountCreated, relativeCreationDate),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
            Text(
                text = account.ownerAddress,
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}

/** iOS's `Badge(decoration: .circle(.textError))`: a 6pt dot in textError beside appTextSmall. */
@Composable
private fun NotFoundBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(CodeTheme.colors.errorText, CircleShape)
        )
        Text(
            text = stringResource(R.string.subtitle_accountNotFound),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )
    }
}
