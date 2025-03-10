package com.getcode.ui.components.chat.messagecontents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.getcode.libs.opengraph.LocalOpenGraphParser
import com.getcode.libs.opengraph.callback.OpenGraphCallback
import com.getcode.libs.opengraph.model.OpenGraphResult
import com.getcode.model.ID
import com.getcode.model.chat.MessageStatus
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R
import com.getcode.ui.components.chat.MessageNodeDefaults
import com.getcode.ui.components.chat.MessageNodeOptions
import com.getcode.ui.components.chat.MessageNodeScope
import com.getcode.ui.components.chat.messagecontents.utils.AlignmentRule
import com.getcode.ui.components.chat.messagecontents.utils.rememberAlignmentRule
import com.getcode.ui.components.chat.utils.MessageReaction
import com.getcode.ui.components.chat.utils.MessageTip
import com.getcode.ui.components.text.markup.Markup
import com.getcode.ui.components.text.markup.MarkupTextHelper
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.dashedBorder
import com.getcode.ui.utils.measured
import com.getcode.ui.utils.measuredIntSize
import kotlinx.datetime.Instant
import kotlin.math.min

@Composable
internal fun MessageNodeScope.MessageText(
    modifier: Modifier = Modifier,
    content: String,
    shape: Shape = MessageNodeDefaults.DefaultShape,
    options: MessageNodeOptions,
    isFromSelf: Boolean,
    isFromBlockedMember: Boolean,
    tips: List<MessageTip>,
    reactions: List<MessageReaction>,
    date: Instant,
    status: MessageStatus = MessageStatus.Unknown,
    wasSentAsFullMember: Boolean,
    onTap: (contentPadding: PaddingValues, touchOffset: Offset) -> Unit,
    onLongPress: () -> Unit,
    onDoubleClick: () -> Unit,
    showTips: () -> Unit,
    onAddReaction: (String) -> Unit,
    onRemoveReaction: (ID) -> Unit,
) {
    val alignment = if (isFromSelf) Alignment.CenterEnd else Alignment.CenterStart

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        BoxWithConstraints(
            modifier = Modifier
                .sizeableWidth()
                .addIf(wasSentAsFullMember) {
                    Modifier.background(color = color, shape = shape)
                }
                .addIf(!wasSentAsFullMember) {
                    Modifier.dashedBorder(
                        strokeWidth = CodeTheme.dimens.border,
                        dashWidth = 2.dp,
                        gapWidth = 2.dp,
                        dashColor = CodeTheme.colors.tertiary,
                        shape = shape
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        // entire bubble of a normal message is the contents of the text
                        onTap = { offset -> onTap(PaddingValues(), offset) },
                        onLongPress = if (!options.isInteractive) null else {
                            { onLongPress() }
                        },
                        onDoubleTap = { if (options.canTip) onDoubleClick() }
                    )
                }
                .padding(CodeTheme.dimens.grid.x2)
        ) contents@{
            val maxWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            Column {
                MessageContent(
                    maxWidth = maxWidthPx,
                    message = content,
                    date = date,
                    status = status,
                    isFromSelf = isFromSelf,
                    isFromBlockedMember = isFromBlockedMember,
                    options = options,
                    tips = tips,
                    openTipModal = showTips,
                    reactions = reactions,
                    onAddReaction = onAddReaction,
                    onRemoveReaction = onRemoveReaction
                )
            }
        }
    }
}

@Composable
internal fun MessageContent(
    modifier: Modifier = Modifier,
    minWidth: Int = 0,
    maxWidth: Int,
    message: String,
    date: Instant,
    status: MessageStatus,
    isFromSelf: Boolean,
    isFromBlockedMember: Boolean,
    options: MessageNodeOptions,
    tips: List<MessageTip>,
    openTipModal: () -> Unit,
    reactions: List<MessageReaction>,
    onAddReaction: (String) -> Unit,
    onRemoveReaction: (ID) -> Unit,
) {
    MessageContent(
        modifier = modifier,
        minWidth = minWidth,
        maxWidth = maxWidth,
        annotatedMessage = AnnotatedString(message),
        date = date,
        status = status,
        isFromSelf = isFromSelf,
        isFromBlockedMember = isFromBlockedMember,
        tips = tips,
        options = options,
        openTipModal = openTipModal,
        reactions = reactions,
        onAddReaction = onAddReaction,
        onRemoveReaction = onRemoveReaction
    )
}

@Composable
internal fun MessageContent(
    modifier: Modifier = Modifier,
    minWidth: Int = 0,
    maxWidth: Int,
    annotatedMessage: AnnotatedString,
    date: Instant,
    status: MessageStatus,
    isFromSelf: Boolean,
    isFromBlockedMember: Boolean,
    options: MessageNodeOptions,
    tips: List<MessageTip> = emptyList(),
    openTipModal: () -> Unit = { },
    reactions: List<MessageReaction> = emptyList(),
    onAddReaction: (String) -> Unit = { },
    onRemoveReaction: (ID) -> Unit = { },
) {
    val openGraphParser = LocalOpenGraphParser.current
    var linkImageUrl: String? by rememberSaveable(annotatedMessage) { mutableStateOf(null) }

    LaunchedEffect(annotatedMessage) {
        if (linkImageUrl == null && options.linkImagePreviewEnabled) {
            val link = Markup.Url().resolve(annotatedMessage.text).firstOrNull()
            if (link != null) {
                openGraphParser?.parse(link, object : OpenGraphCallback {
                    override fun onResponse(result: OpenGraphResult) {
                        linkImageUrl = result.image
                    }

                    override fun onError(error: String) = Unit
                })
            }
        }
    }

    val alignmentRule by rememberAlignmentRule(
        contentTextStyle = options.contentStyle,
        minWidth = minWidth,
        maxWidth = maxWidth,
        message = annotatedMessage,
        date = date,
        hasFeedback = tips.isNotEmpty() || reactions.isNotEmpty(),
        hasLink = linkImageUrl != null && options.linkImagePreviewEnabled
    )

    when (alignmentRule) {
        AlignmentRule.Column -> {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
            ) {
                MarkupTextHandler(
                    text = annotatedMessage,
                    options = options,
                    isFromBlockedMember = isFromBlockedMember,
                )

                if (options.linkImagePreviewEnabled) {
                    AnimatedVisibility(linkImageUrl != null) {
                        AsyncImage(
                            model = linkImageUrl,
                            contentDescription = null,
                        )
                    }
                }

                ColumnBasedFooter(
                    tips = tips,
                    reactions = reactions,
                    isFromSelf = isFromSelf,
                    date = date,
                    status = status,
                    options = options,
                    openTipModal = openTipModal,
                    onAddReaction = onAddReaction,
                    onRemoveReaction = onRemoveReaction
                )
            }
        }

        AlignmentRule.ParagraphLastLine -> {
            Column(
                modifier = modifier.padding(CodeTheme.dimens.grid.x1),
            ) {
                MarkupTextHandler(
                    text = annotatedMessage,
                    options = options,
                    isFromBlockedMember = isFromBlockedMember,
                )
                DateWithStatus(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .align(Alignment.End),
                    date = date,
                    status = status,
                    isFromSelf = isFromSelf,
                    showStatus = options.showStatus,
                    showTimestamp = options.showTimestamp,
                )

                AnimatedVisibility(linkImageUrl != null) {
                    AsyncImage(
                        model = linkImageUrl,
                        contentDescription = null,
                    )
                }
            }
        }

        AlignmentRule.SingleLineEnd -> {
            Column(modifier = modifier) {
                Row(
                    modifier = Modifier.width(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
                ) {
                    MarkupTextHandler(
                        text = annotatedMessage,
                        options = options,
                        isFromBlockedMember = isFromBlockedMember,
                    )
                    Spacer(Modifier.weight(1f))
                    DateWithStatus(
                        modifier = Modifier
                            .padding(top = CodeTheme.dimens.grid.x1 + 2.dp),
                        date = date,
                        status = status,
                        isFromSelf = isFromSelf,
                        showStatus = options.showStatus,
                        showTimestamp = options.showTimestamp,
                    )
                }

                AnimatedVisibility(linkImageUrl != null) {
                    AsyncImage(
                        model = linkImageUrl,
                        contentDescription = null,
                    )
                }
            }
        }

        else -> Unit
    }
}

@Composable
private fun MarkupTextHandler(
    text: AnnotatedString,
    options: MessageNodeOptions,
    isFromBlockedMember: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        isFromBlockedMember -> {
            Text(
                modifier = modifier,
                text = AnnotatedString.Builder().apply {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(stringResource(R.string.title_blockedMessage))
                    pop()
                }.toAnnotatedString(),
                style = options.contentStyle
            )

        }

        options.onMarkupClicked != null -> {
            val markupTextHelper = remember { MarkupTextHelper() }
            val markups = options.markupsToResolve.map { Markup.create(it) }

            val annotatedString = markupTextHelper.annotate(text.text, markups)
            val markupHoist = LocalTextLayoutResult.current

            Text(
                text = annotatedString,
                style = options.contentStyle.copy(color = CodeTheme.colors.textMain),
                modifier = modifier,
                onTextLayout = { markupHoist(it) }
            )
        }

        else -> {
            Text(modifier = modifier, text = text, style = options.contentStyle)
        }
    }
}

@Composable
private fun ColumnBasedFooter(
    tips: List<MessageTip>,
    reactions: List<MessageReaction>,
    isFromSelf: Boolean,
    date: Instant,
    status: MessageStatus,
    options: MessageNodeOptions,
    openTipModal: () -> Unit,
    onAddReaction: (String) -> Unit,
    onRemoveReaction: (ID) -> Unit,
) {
    val x1 = CodeTheme.dimens.grid.x1
    val x2 = CodeTheme.dimens.grid.x2
    SubcomposeLayout(modifier = Modifier.wrapContentWidth()) { constraints ->
        // Measure Feedback first if exists
        val feedbackPlaceable = subcompose("Feedback") {
            if (tips.isNotEmpty() || reactions.isNotEmpty()) {
                Feedback(
                    tips = tips,
                    reactions = reactions,
                    isMessageFromSelf = isFromSelf,
                    onViewTips = openTipModal,
                    onAddReaction = onAddReaction,
                    onRemoveReaction = onRemoveReaction,
                )
            }
        }.firstOrNull()?.measure(constraints)

        val feedbackWidth = feedbackPlaceable?.width

        // Measure DateWithStatus separately
        val datePlaceable = subcompose("DateWithStatus") {
            DateWithStatus(
                date = date,
                status = status,
                isFromSelf = isFromSelf,
                showStatus = options.showStatus,
                showTimestamp = options.showTimestamp,
            )
        }.first().measure(constraints)

        val dateWidth = datePlaceable.width
        val dateHeight = datePlaceable.height

        val maxContentWidth = (feedbackWidth?.let { it + x2.roundToPx() } ?: 0) + dateWidth
        val fitsInSameRow = maxContentWidth <= constraints.maxWidth

        val layoutWidth = if (fitsInSameRow) {
            minOf(constraints.maxWidth, maxContentWidth)
        } else {
            maxOf(feedbackWidth ?: 0, dateWidth)
        }
        val layoutHeight = if (fitsInSameRow) {
            feedbackPlaceable?.height ?: dateHeight
        } else {
            (feedbackPlaceable?.height ?: 0) + dateHeight
        }

        layout(layoutWidth, layoutHeight) {
            var yOffset = 0
            var xOffset = 0

            feedbackPlaceable?.placeRelative(xOffset, yOffset)

            if (fitsInSameRow) {
                if (feedbackPlaceable != null) {
                    xOffset = layoutWidth - dateWidth
                    yOffset += feedbackPlaceable.height - dateHeight
                } else {
                    xOffset = layoutWidth - dateWidth
                    yOffset += x1.roundToPx()
                }
                datePlaceable.placeRelative(xOffset, yOffset)
            } else {
                yOffset += (feedbackPlaceable?.height ?: 0) + x1.roundToPx()
                datePlaceable.placeRelative(layoutWidth - dateWidth, yOffset)
            }
        }
    }
}