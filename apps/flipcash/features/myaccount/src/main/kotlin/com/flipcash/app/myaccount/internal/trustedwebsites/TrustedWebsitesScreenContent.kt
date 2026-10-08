package com.flipcash.app.myaccount.internal.trustedwebsites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.links.TrustedWebsite
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.util.formatLocalized
import kotlin.time.Instant

@Composable
internal fun TrustedWebsitesScreenContent(
    websites: List<TrustedWebsite>,
    onRemove: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (websites.isEmpty()) {
            EmptyTrustedWebsites(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = CodeTheme.dimens.inset),
            ) {
                items(websites, key = { it.host }) { website ->
                    TrustedWebsiteRow(
                        modifier = Modifier.animateItem(),
                        website = website,
                        onRemove = { onRemove(website.host) },
                    )
                    HorizontalDivider(color = CodeTheme.colors.divider, thickness = 0.5.dp)
                }
                item(key = "footer") {
                    Text(
                        modifier = Modifier
                            .animateItem()
                            .padding(vertical = CodeTheme.dimens.grid.x4),
                        text = stringResource(R.string.description_trustedWebsites),
                        style = CodeTheme.typography.caption,
                        color = CodeTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrustedWebsiteRow(
    website: TrustedWebsite,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = website.host,
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.subtitle_trustedWebsiteAdded,
                    Instant.fromEpochMilliseconds(website.addedAtMillis).formatLocalized("MMM d"),
                ),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
        }
        Text(
            modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(CodeTheme.dimens.grid.x1),
            text = stringResource(R.string.action_remove),
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.errorText,
        )
    }
}

/** Same treatment as the Blocked screen's empty state. */
@Composable
private fun EmptyTrustedWebsites(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = CodeTheme.dimens.inset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        Text(
            text = stringResource(R.string.title_trustedWebsitesEmpty),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )
        Text(
            modifier = Modifier.fillMaxWidth(0.8f),
            text = stringResource(R.string.description_trustedWebsitesEmpty),
            style = CodeTheme.typography.caption,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
