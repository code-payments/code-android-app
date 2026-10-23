package com.flipcash.shared.common.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.shared.common.ui.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05

private val StatsCardHeight = 84.dp
private val LeadingStatWidth = 176.dp
private val StatsDividerHeight = 48.dp

/**
 * The two facts under a profile header: what it costs to start a chat, and when the subject
 * joined. Both arrive formatted — the caller owns currency and date formatting — and a null
 * renders as a dash rather than collapsing the stat.
 */
@Composable
fun ProfileStatsCard(
    minimumToChat: String?,
    joined: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(StatsCardHeight)
            .clip(CodeTheme.shapes.medium)
            .background(White05),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stat(
            modifier = Modifier.width(LeadingStatWidth).fillMaxHeight(),
            caption = stringResource(R.string.title_profileMinimumToChat),
            value = minimumToChat,
        )
        Box(
            modifier = Modifier
                .width(CodeTheme.dimens.border)
                .height(StatsDividerHeight)
                .background(CodeTheme.colors.divider)
        )
        Stat(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            caption = stringResource(R.string.title_profileDateJoined),
            value = joined,
        )
    }
}

@Composable
private fun Stat(
    caption: String,
    value: String?,
    modifier: Modifier = Modifier,
) {
    // Top-aligned in the full card height, so both captions sit at the same inset from the top
    // edge rather than floating to the middle.
    Column(
        modifier = modifier.padding(start = CodeTheme.dimens.staticGrid.x3, top = CodeTheme.dimens.staticGrid.x3),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x1),
    ) {
        Text(
            text = caption,
            style = CodeTheme.typography.caption,
            color = CodeTheme.colors.textSecondary,
        )
        Text(
            text = value ?: stringResource(R.string.text_profileStatEmpty),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_ProfileStatsCard() {
    Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x3)) {
        ProfileStatsCard(minimumToChat = "$1.00", joined = "October 2026")
        ProfileStatsCard(minimumToChat = null, joined = null)
    }
}
