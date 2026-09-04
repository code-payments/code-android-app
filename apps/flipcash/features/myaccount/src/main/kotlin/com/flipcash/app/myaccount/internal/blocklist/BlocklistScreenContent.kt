package com.flipcash.app.myaccount.internal.blocklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.delay
import com.flipcash.app.core.blocklist.BlockedUserProfile
import com.flipcash.features.myaccount.R
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.utils.hexEncodedString
import com.getcode.view.LoadingSuccessState
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun BlocklistScreenContent(
    blocked: LazyPagingItems<BlockedUserProfile>,
    unblocking: Map<String, LoadingSuccessState>,
    onUnblock: (BlockedUserProfile) -> Unit,
) {
    // Empty-state detection with a RemoteMediator is race-prone: after the mediator's refresh
    // finishes there's a frame where it reports NotLoading + 0 items *before* the Room PagingSource
    // re-queries and surfaces the freshly-inserted rows (the replaceAll invalidation → source
    // reload is async). The same transient shows up on the very first frame (source settled empty,
    // mediator not yet fetching). No instantaneous snapshot can tell that gap apart from a truly
    // empty list — so debounce it: only a "settled empty" that survives a short window paints
    // "No One Blocked". A transient gap resolves to data first and never flashes.
    val loadState = blocked.loadState
    val settledEmpty = loadState.refresh is LoadState.NotLoading &&
        loadState.mediator?.refresh is LoadState.NotLoading &&
        loadState.append.endOfPaginationReached &&
        blocked.itemCount == 0
    var isEmpty by remember { mutableStateOf(false) }
    LaunchedEffect(settledEmpty) {
        isEmpty = if (settledEmpty) {
            delay(250.milliseconds)
            true
        } else {
            false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isEmpty) {
            EmptyBlocklist(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = CodeTheme.dimens.inset),
            ) {
                items(
                    count = blocked.itemCount,
                    key = blocked.itemKey { it.userId.hexEncodedString() },
                ) { index ->
                    val user = blocked[index] ?: return@items
                    BlockedUserRow(
                        modifier = Modifier.animateItem(),
                        user = user,
                        loadingState = unblocking[user.userId.hexEncodedString()]
                            ?: LoadingSuccessState(),
                        onClick = { onUnblock(user) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedUserRow(
    user: BlockedUserProfile,
    loadingState: LoadingSuccessState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Only actionable while idle — no re-taps mid-unblock.
            .clickable(enabled = loadingState.isIdle, onClick = onClick)
            .padding(vertical = CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        ContactAvatar(
            image = user.profilePicture,
            displayName = user.name.orEmpty(),
            access = BlobAccessContext.profile(user.userId),
            // Blocked users are shown obscured, per the design.
            blurred = true,
            modifier = Modifier
                .size(CodeTheme.dimens.staticGrid.x6)
                .clip(CircleShape),
        )
        Text(
            modifier = Modifier.weight(1f),
            // One line, so the handle stands in for a missing name rather than sitting under it.
            text = user.name.orEmpty(),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Fixed footprint so the row doesn't reflow when the chevron becomes a spinner.
        Box(
            modifier = Modifier.size(CodeTheme.dimens.staticGrid.x5),
            contentAlignment = Alignment.Center,
        ) {
            if (loadingState.state == LoadingSuccessState.State.Loading) {
                CodeCircularProgressIndicator(
                    strokeWidth = CodeTheme.dimens.thickBorder,
                    color = CodeTheme.colors.textSecondary,
                    modifier = Modifier.size(CodeTheme.dimens.grid.x3),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = CodeTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun EmptyBlocklist(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = CodeTheme.dimens.inset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        Text(
            text = stringResource(R.string.title_blocklistEmpty),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )
        Text(
            modifier = Modifier.fillMaxWidth(0.8f),
            text = stringResource(R.string.description_blocklistEmpty),
            style = CodeTheme.typography.caption,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
