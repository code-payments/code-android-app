package com.flipcash.app.login.internal.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.flipcash.features.login.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator

@Composable
internal fun AccountSelectionContent(
    state: AccountSelectionViewModel.State,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The in-app switcher has no onboarding flow to fall through to, so it hides the footer and
     * leaves [onEnterAccessKey] alone.
     */
    showEnterAccessKey: Boolean = true,
    onEnterAccessKey: () -> Unit = {},
) {
    // The title lives in each caller's AppBarWithTitle, as on every other list screen here
    // (Blocklist, Advanced). A second heading drawn inside the content sat below the bar and made
    // this the one screen with two title rows.
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when {
                // Read the list before saying it is empty, or every cold start flashes the
                // empty state.
                state.loading -> CodeCircularProgressIndicator(
                    modifier = Modifier.size(CodeTheme.dimens.grid.x6),
                )

                // A Block Store read that comes back with nothing is the case that strands the
                // in-app switcher, which has no "Enter a Different Access Key" footer to leave by.
                state.accounts.isEmpty() -> Text(
                    modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
                    text = stringResource(R.string.subtitle_noStoredAccounts),
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = CodeTheme.dimens.inset),
                ) {
                    items(state.accounts, key = { it.id }) { account ->
                        AccountRow(
                            account = account,
                            isCurrent = account.entropy == state.currentEntropy,
                            relativeCreationDate = remember(account.creationDate) {
                                formatRelative(account.creationDate)
                            },
                            onClick = { onSelect(account.entropy) },
                            onLongClick = { onRemove(account.entropy) },
                        )
                    }
                }
            }
        }

        if (showEnterAccessKey) {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x4),
                onClick = onEnterAccessKey,
                text = stringResource(R.string.action_enterDifferentAccessKey),
                buttonState = ButtonState.Subtle,
            )
        }
    }
}

private fun formatRelative(epochMillis: Long): String =
    android.text.format.DateUtils.getRelativeTimeSpanString(epochMillis).toString()
