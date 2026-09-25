package com.flipcash.app.messenger.internal.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.features.messenger.R
import com.flipcash.services.models.handle
import com.flipcash.shared.chat.reactions.ReactionPill
import com.flipcash.shared.chat.reactions.ReactorsListModel
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.opencode.model.core.ID
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.utils.AllowSheetExpansionWhenScrollable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Who reacted, and with what (decision 4): one row per person, every emoji they used, no filters.
 * Opens unscrolled with nothing focused — there is no search here, unlike the picker.
 *
 * @param resolveDisplay per-row identity, re-subscribed for the row's lifetime in composition —
 *   see [ChatViewModel.reactorDisplay] for the source precedence and its live re-resolution as a
 *   member roster or a server fetch lands.
 * @param hasMore read live rather than passed as a snapshot: the last row's visibility effect below
 *   asks it fresh each time it fires.
 */
@Composable
internal fun ReactorsSheet(
    pills: List<ReactionPill>,
    rows: List<ReactorsListModel.Row>,
    loading: Boolean,
    hasMore: () -> Boolean,
    resolveDisplay: (ID) -> Flow<ChatViewModel.ReactorDisplay?>,
    onLoadMore: () -> Unit,
    onOpenProfile: (ID) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    AllowSheetExpansionWhenScrollable(listState)

    val totalCount = remember(pills) { pills.sumOf { it.count } }

    Column(modifier = Modifier.fillMaxWidth()) {
        AppBarWithTitle(
            title = if (totalCount > 0) {
                stringResource(R.string.title_reactionsCount, totalCount)
            } else {
                stringResource(R.string.title_reactorsSheet)
            },
            titleAlignment = Alignment.CenterHorizontally,
            endContent = { AppBarDefaults.Close(onClick = onDismiss) },
        )

        if (pills.isNotEmpty()) {
            SummaryPillRow(pills = pills)
        }

        // Placeholders only if nothing has rendered 300ms in — the first page usually lands
        // faster than that, and this keeps a fast fetch from ever flashing skeleton rows.
        val showPlaceholders by produceState(initialValue = false, rows, loading) {
            value = false
            if (rows.isEmpty() && loading) {
                delay(300)
                value = rows.isEmpty()
            }
        }

        LoadMoreOnLastRowVisible(listState, rows, hasMore, onLoadMore)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            // No horizontal padding: rows inset their own leading edge, and overflowing emoji run
            // off the screen edge rather than stopping at a margin.
            contentPadding = PaddingValues(vertical = CodeTheme.dimens.grid.x2),
        ) {
            if (showPlaceholders) {
                // min(max pill count, 5) placeholder rows.
                val count = (pills.maxOfOrNull { it.count } ?: 1L).coerceAtMost(5L).toInt().coerceAtLeast(1)
                items(count) { ReactorRowPlaceholder() }
            } else {
                items(rows, key = { it.userId.joinToString(",") }) { row ->
                    ReactorRow(
                        userId = row.userId,
                        emojis = row.emojis,
                        resolveDisplay = resolveDisplay,
                        onClick = { onOpenProfile(row.userId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadMoreOnLastRowVisible(
    listState: LazyListState,
    rows: List<ReactorsListModel.Row>,
    hasMore: () -> Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(listState, rows) {
        if (rows.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= rows.lastIndex && hasMore()) {
                    onLoadMore()
                }
            }
    }
}

/** The non-interactive summary row: every pill from the pill row, unclickable here. */
@Composable
private fun SummaryPillRow(pills: List<ReactionPill>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = CodeTheme.dimens.inset, vertical = CodeTheme.dimens.grid.x2),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        for (pill in pills) {
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(percent = 50))
                    .padding(horizontal = CodeTheme.dimens.grid.x3),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = pill.emoji, fontSize = 17.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = pill.count.toString(),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

/**
 * One reactor: avatar and name/handle, then every emoji they reacted with. Only the person opens
 * their profile; the emoji are theirs to scroll, not a tap target.
 */
@Composable
private fun ReactorRow(
    userId: ID,
    emojis: List<String>,
    resolveDisplay: (ID) -> Flow<ChatViewModel.ReactorDisplay?>,
    onClick: () -> Unit,
) {
    val displayFlow = remember(userId, resolveDisplay) { resolveDisplay(userId) }
    val display by displayFlow.collectAsStateWithLifecycle(initialValue = null)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // The person keeps priority over the emoji, which always get at least MinEmojiWidth.
        val personMaxWidth = (maxWidth - RowLeadingInset - MinEmojiWidth).coerceAtLeast(0.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = RowLeadingInset)
                .padding(vertical = 8.5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = personMaxWidth)
                    .clickable(enabled = display != null, onClick = onClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                val profile = display?.profile
                if (profile != null) {
                    ContactAvatar(userProfile = profile, modifier = Modifier.size(48.dp).clip(CircleShape))
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(CodeTheme.colors.contactAvatar.colors)),
                    )
                }

                Column {
                    Text(
                        text = display?.name ?: "",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CodeTheme.colors.textMain,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val handle = profile?.handle
                    if (!handle.isNullOrBlank()) {
                        Text(
                            text = handle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = CodeTheme.colors.textMain.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            ReactorEmojis(emojis = emojis, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * A reactor's emoji, trailing-aligned in whatever the name leaves. When there are more than fit
 * they scroll sideways, opening on the first emoji, and fade out across the margins instead of
 * stopping at a hard edge. The leading margin is also the gap to the name.
 */
@Composable
private fun ReactorEmojis(emojis: List<String>, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    val overflows = scrollState.maxValue > 0
    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = emojis.joinToString(" ") }
            .then(if (overflows) Modifier.edgeFade(EmojiLeadingMargin, EmojiTrailingMargin) else Modifier),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(start = EmojiLeadingMargin, end = EmojiTrailingMargin)
                .clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (emoji in emojis) {
                Text(text = emoji, fontSize = 30.sp)
            }
        }
    }
}

/** Fades content out across [leading] and [trailing], eased so the fade has no visible start line. */
private fun Modifier.edgeFade(leading: Dp, trailing: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val leadingPx = leading.toPx()
        val trailingPx = trailing.toPx()
        drawRect(
            brush = Brush.horizontalGradient(*EdgeFadeStops, startX = 0f, endX = leadingPx),
            size = Size(leadingPx, size.height),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.horizontalGradient(*EdgeFadeStops, startX = size.width, endX = size.width - trailingPx),
            topLeft = Offset(size.width - trailingPx, 0f),
            size = Size(trailingPx, size.height),
            blendMode = BlendMode.DstIn,
        )
    }

private val EdgeFadeStops = arrayOf(
    0f to Color.Black.copy(alpha = 0f),
    0.25f to Color.Black.copy(alpha = 0.15f),
    0.5f to Color.Black.copy(alpha = 0.5f),
    0.75f to Color.Black.copy(alpha = 0.85f),
    1f to Color.Black,
)

private val RowLeadingInset = 20.dp
private val MinEmojiWidth = 140.dp
private val EmojiLeadingMargin = 16.dp
private val EmojiTrailingMargin = 20.dp

@Composable
private fun ReactorRowPlaceholder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RowLeadingInset, vertical = 8.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(CodeTheme.colors.divider),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CodeTheme.colors.divider),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.25f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CodeTheme.colors.divider),
            )
        }
    }
}
