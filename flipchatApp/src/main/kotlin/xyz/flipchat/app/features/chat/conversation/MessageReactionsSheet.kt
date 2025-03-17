package xyz.flipchat.app.features.chat.conversation

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Tab
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.extensions.formattedRaw
import com.getcode.model.KinAmount
import com.getcode.model.chat.Sender
import com.getcode.model.sum
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.FullWidthScrollableTabRow
import com.getcode.ui.components.R
import com.getcode.ui.components.chat.UserAvatar
import com.getcode.ui.components.chat.utils.MessageReaction
import com.getcode.ui.components.chat.utils.MessageTip
import com.getcode.ui.components.tabIndicatorOffset
import com.getcode.ui.components.user.social.SenderNameDisplay
import com.getcode.ui.emojis.processEmoji
import kotlinx.coroutines.launch
import xyz.flipchat.services.internal.data.mapper.nullIfEmpty

private sealed interface Feedback {
    val data: FeedbackData
    data class Tips(override val data: FeedbackData.Tips) : Feedback
    data class Emoji(val emoji: String, override val data: FeedbackData.Senders) : Feedback
    data class All(override val data: FeedbackData.All) : Feedback
}

private typealias GroupedTips = List<Pair<Sender, KinAmount>>
private sealed interface FeedbackData {
    data class Tips(val tips: GroupedTips) : FeedbackData
    data class Senders(val senders: List<Sender>) : FeedbackData
    data class All(val tips: List<Pair<Sender, KinAmount>>, val reactions: List<MessageReaction>) : FeedbackData {
        val totalCount: Int
            get() = reactions.count() + tips.count()
    }
}

internal data class MessageReactionsSheet(
    val tips: List<MessageTip>,
    val reactions: List<MessageReaction>,
) : Screen {

    @Composable
    override fun Content() {
        val feedback = remember(tips, reactions) {
            val items = mutableListOf<Feedback>()
            var groupedTips: GroupedTips = emptyList()
            if (tips.isNotEmpty()) {
                groupedTips = tips.groupBy { it.tipper }
                    .mapValues { it.value.map { it.amount }.sum() }
                    .toList().sortedByDescending { it.second.fiat }
                items.add(Feedback.Tips(FeedbackData.Tips(groupedTips)))
            }

            reactions.groupBy { it.emoji }
                .mapValues { it.value.map { e -> e.sender } }
                .onEach {
                    items.add(Feedback.Emoji(it.key, FeedbackData.Senders(it.value)))
                }

            if ((groupedTips.isNotEmpty() || reactions.isNotEmpty()) && items.count() > 1) {
                items.add(0, Feedback.All(FeedbackData.All(groupedTips, reactions)))
            }

            return@remember items
        }


        val pagerState = rememberPagerState { feedback.count() }
        val composeScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
        ) {
            if (pagerState.pageCount > 1) {
                TabIndicator(
                    pagerState = pagerState,
                    feedback = feedback,
                ) {
                    composeScope.launch {
                        pagerState.animateScrollToPage(it)
                    }
                }
            }
            HorizontalPager(
                modifier = Modifier.weight(1f),
                state = pagerState
            ) { page ->
                val item = feedback[page]
                FeedbackContent(item)
            }
        }
    }
}

@Composable
private fun TabIndicator(
    pagerState: PagerState,
    feedback: List<Feedback>,
    onClick: (Int) -> Unit,
) {
    FullWidthScrollableTabRow(
        modifier = Modifier.fillMaxWidth(),
        selectedTabIndex = pagerState.currentPage,
        backgroundColor = Color.Transparent,
        edgePadding = CodeTheme.dimens.grid.x2,
        indicator = { tabPositions ->
            TabRowDefaults.Indicator(
                color = CodeTheme.colors.onSurface,
                modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage])
            )
        },
        divider = {
            TabRowDefaults.Divider(color = CodeTheme.colors.divider)
        }
    ) {
        feedback.onEachIndexed { index, feedback ->
            Tab(
                modifier = Modifier.padding(
//                        horizontal = CodeTheme.dimens.grid.x5,
                    vertical = CodeTheme.dimens.grid.x3
                ),
                selected = index == pagerState.currentPage,
                selectedContentColor = Color.Transparent,
                onClick = {
                    onClick(index)
                }
            ) {
                val text = when (feedback) {
                    is Feedback.Emoji -> {
                        if (LocalInspectionMode.current) {
                            feedback.emoji
                        } else {
                            processEmoji(feedback.emoji).toString()
                        }
                    }
                    is Feedback.Tips -> "⬢"
                    is Feedback.All -> "All"
                }

                Text(
                    text = text,
                    color = CodeTheme.colors.onSurface,
                    style = CodeTheme.typography.textSmall.copy(fontWeight = FontWeight.W700),
                )
            }
        }
    }
}

@Composable
private fun FeedbackContent(
    item: Feedback
) {
    val imageModifier = Modifier
        .size(CodeTheme.dimens.staticGrid.x8)
        .clip(CircleShape)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(CodeTheme.dimens.inset),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
    ) {
        val count = when (val data = item.data) {
            is FeedbackData.Senders -> data.senders.count()
            is FeedbackData.Tips -> data.tips.count()
            is FeedbackData.All -> data.totalCount
        }
        items(count) { index ->
            when (val data = item.data) {
                is FeedbackData.Senders -> {
                    val sender = data.senders[index]
                    EmojiReactionRow(
                        sender = sender,
                        imageModifier = imageModifier,
                        modifier = Modifier.fillParentMaxWidth(),
                    )
                }
                is FeedbackData.Tips -> {
                    val tip = data.tips[index]
                    val tipper = tip.first
                    val tipAmount = tip.second

                    TipReactionRow(
                        tipper = tipper,
                        tip = tipAmount,
                        imageModifier = imageModifier,
                        modifier = Modifier.fillParentMaxWidth(),
                    )
                }

                is FeedbackData.All -> {
                    val items = data.tips + data.reactions
                    val i = items[index]
                    when (i) {
                        is MessageReaction -> {
                            EmojiReactionRow(
                                sender = i.sender,
                                imageModifier = imageModifier,
                                modifier = Modifier.fillParentMaxWidth(),
                                emoji = i.emoji
                            )
                        }
                        is Pair<*, *> -> {
                            val row = i as Pair<Sender, KinAmount>
                            TipReactionRow(
                                tipper = row.first,
                                tip = i.second,
                                imageModifier = imageModifier,
                                modifier = Modifier.fillParentMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiReactionRow(
    sender: Sender,
    emoji: String? = null,
    @SuppressLint("ModifierParameter")
    imageModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            modifier = imageModifier,
            data = sender.profileImage ?: sender.id
        ) {
            Image(
                modifier = Modifier.padding(5.dp),
                imageVector = Icons.Default.Person,
                colorFilter = ColorFilter.tint(Color.White),
                contentDescription = null,
            )
        }
        SenderNameDisplay(
            sender = sender,
            textStyle = CodeTheme.typography.textMedium,
            textColor = CodeTheme.colors.onSurface
        )

        Spacer(Modifier.weight(1f))

        if (emoji != null) {
            Text(
                text = if (LocalInspectionMode.current) {
                    emoji
                } else {
                    processEmoji(emoji).toString()
                },
                color = CodeTheme.colors.onSurface,
                style = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.W700),
            )
        }
    }
}

@Composable
private fun TipReactionRow(
    tipper: Sender,
    tip: KinAmount,
    @SuppressLint("ModifierParameter")
    imageModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            modifier = imageModifier,
            data = tipper.profileImage.nullIfEmpty() ?: tipper.id
        ) {
            Image(
                modifier = Modifier.padding(5.dp),
                imageVector = Icons.Default.Person,
                colorFilter = ColorFilter.tint(Color.White),
                contentDescription = null,
            )
        }
        SenderNameDisplay(
            sender = tipper,
            textStyle = CodeTheme.typography.textMedium,
            textColor = CodeTheme.colors.onSurface
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(
                R.string.title_kinAmountWithLogo,
                tip.formattedRaw()
            ),
            color = CodeTheme.colors.onSurface,
            style = CodeTheme.typography.textMedium,
        )
    }
}