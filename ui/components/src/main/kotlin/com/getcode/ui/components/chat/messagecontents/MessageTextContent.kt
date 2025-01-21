package com.getcode.ui.components.chat.messagecontents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.getcode.extensions.formattedRaw
import com.getcode.libs.opengraph.LocalOpenGraphParser
import com.getcode.libs.opengraph.callback.OpenGraphCallback
import com.getcode.libs.opengraph.model.OpenGraphResult
import com.getcode.model.chat.MessageStatus
import com.getcode.model.sum
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R
import com.getcode.ui.components.chat.MessageNodeDefaults
import com.getcode.ui.components.chat.MessageNodeOptions
import com.getcode.ui.components.chat.MessageNodeScope
import com.getcode.ui.components.chat.UserAvatar
import com.getcode.ui.components.chat.messagecontents.utils.AlignmentRule
import com.getcode.ui.components.chat.messagecontents.utils.rememberAlignmentRule
import com.getcode.ui.components.chat.utils.MessageTip
import com.getcode.ui.components.text.markup.Markup
import com.getcode.ui.components.text.markup.MarkupTextHelper
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.rememberedLongClickable
import kotlinx.datetime.Instant

@Composable
internal fun MessageNodeScope.MessageText(
    modifier: Modifier = Modifier,
    content: String,
    shape: Shape = MessageNodeDefaults.DefaultShape,
    options: MessageNodeOptions,
    isFromSelf: Boolean,
    isFromBlockedMember: Boolean,
    tips: List<MessageTip>,
    date: Instant,
    status: MessageStatus = MessageStatus.Unknown,
    showControls: () -> Unit,
    showTipSelection: () -> Unit,
    showTips: () -> Unit,
) {
    val alignment = if (isFromSelf) Alignment.CenterEnd else Alignment.CenterStart

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        BoxWithConstraints(
            modifier = Modifier
                .sizeableWidth()
                .background(
                    color = color,
                    shape = shape,
                )
                .addIf(options.isInteractive) {
                    Modifier.rememberedLongClickable {
                        showControls()
                    }
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
                    onLongPress = showControls,
                    onDoubleClick = showTipSelection
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
    onLongPress: () -> Unit = { },
    onDoubleClick: () -> Unit = { },
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
        onLongPress = onLongPress,
        onDoubleClick = onDoubleClick
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
    onLongPress: () -> Unit = { },
    onDoubleClick: () -> Unit = { },
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
        hasTips = tips.isNotEmpty(),
        hasLink = linkImageUrl != null && options.linkImagePreviewEnabled
    )

    when (alignmentRule) {
        AlignmentRule.Column -> {
            Column(
                modifier = modifier.width(IntrinsicSize.Max),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
            ) {
                MarkupTextHandler(
                    text = annotatedMessage,
                    options = options,
                    onLongPress = onLongPress,
                    isFromBlockedMember = isFromBlockedMember,
                    onDoubleClick = onDoubleClick,
                )

                if (options.linkImagePreviewEnabled) {
                    AnimatedVisibility(linkImageUrl != null) {
                        AsyncImage(
                            model = linkImageUrl,
                            contentDescription = null,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
                ) {
                    if (tips.isNotEmpty()) {
                        Tips(tips) { openTipModal() }
                    }
                    DateWithStatus(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.Bottom)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = if (!options.isInteractive) null else {
                                        { onLongPress() }
                                    },
                                    onDoubleTap = { onDoubleClick() }
                                )
                            },
                        date = date,
                        status = status,
                        isFromSelf = isFromSelf,
                        showStatus = options.showStatus,
                        showTimestamp = options.showTimestamp,
                    )
                }
            }
        }

        AlignmentRule.ParagraphLastLine -> {
            Column(
                modifier = modifier.padding(CodeTheme.dimens.grid.x1),
            ) {
                MarkupTextHandler(
                    text = annotatedMessage,
                    options = options,
                    onLongPress = onLongPress,
                    isFromBlockedMember = isFromBlockedMember,
                    onDoubleClick = onDoubleClick,
                )
                DateWithStatus(
                    modifier = Modifier
                        .align(Alignment.End)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = if (!options.isInteractive) null else {
                                    { onLongPress() }
                                },
                            )
                        },
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
                        onLongPress = onLongPress,
                        isFromBlockedMember = isFromBlockedMember,
                        onDoubleClick = onDoubleClick,
                    )
                    Spacer(Modifier.weight(1f))
                    DateWithStatus(
                        modifier = Modifier
                            .padding(top = CodeTheme.dimens.grid.x1 + 2.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = if (!options.isInteractive) null else {
                                        { onLongPress() }
                                    },
                                )
                            },
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
    onLongPress: () -> Unit = { },
    onDoubleClick: () -> Unit,
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
            val handler = options.onMarkupClicked
            val markupTextHelper = remember { MarkupTextHelper() }
            val markups = options.markupsToResolve.map { Markup.create(it) }

            val annotatedString = markupTextHelper.annotate(text.text, markups)

            val handleTouchedContent = { offset: Int ->
                annotatedString.getStringAnnotations(
                    tag = Markup.RoomNumber.TAG,
                    start = offset,
                    end = offset
                )
                    .firstOrNull()?.let { annotation ->
                        handler.invoke(Markup.RoomNumber(annotation.item.toLong()))
                    }

                annotatedString.getStringAnnotations(
                    tag = Markup.Url.TAG,
                    start = offset,
                    end = offset
                )
                    .firstOrNull()?.let { annotation ->
                        handler.invoke(Markup.Url(annotation.item))
                    }

                annotatedString.getStringAnnotations(
                    tag = Markup.Phone.TAG,
                    start = offset,
                    end = offset
                ).firstOrNull()?.let { annotation ->
                    handler.invoke(Markup.Phone(annotation.item))
                }
            }

            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

            Text(
                text = annotatedString,
                style = options.contentStyle.copy(color = CodeTheme.colors.textMain),
                modifier = modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                layoutResult?.let { layoutResult ->
                                    val position = layoutResult.getOffsetForPosition(offset)
                                    handleTouchedContent(position)
                                }
                            },
                            onDoubleTap = { _ -> onDoubleClick() },
                            onLongPress = if (!options.isInteractive) null else {
                                { onLongPress() }
                            },
                        )
                    },
                onTextLayout = { layoutResult = it }
            )
        }

        else -> {
            Text(modifier = modifier, text = text, style = options.contentStyle)
        }
    }
}

@Composable
private fun Tips(
    tips: List<MessageTip>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (tips.isNotEmpty()) {
        val didUserTip = tips.any { it.tipper.isSelf }
        val backgroundColor by animateColorAsState(
            if (didUserTip) CodeTheme.colors.tertiary
            else Color.White
        )

        val contentColor by animateColorAsState(
            if (didUserTip) CodeTheme.colors.onBackground
            else CodeTheme.colors.secondary
        )

        val totalTips = tips.map { it.amount }.sum().formattedRaw()

        Row(
            modifier = modifier
                .clip(CircleShape)
                .clickable { onClick() }
                .background(backgroundColor, CircleShape)
                .padding(
                    horizontal = CodeTheme.dimens.grid.x2,
                    vertical = CodeTheme.dimens.grid.x1,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
        ) {
            Text(
                text = stringResource(R.string.title_kinAmountWithLogo, totalTips),
                color = contentColor,
                style = CodeTheme.typography.caption,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((-8).dp)
            ) {
                val imageModifier = Modifier
                    .size(CodeTheme.dimens.staticGrid.x4)
                    .clip(CircleShape)
                    .border(CodeTheme.dimens.border, contentColor, CircleShape)

                val tippers = remember(tips) { tips.map { it.tipper }.distinct() }

                tippers.take(3).fastForEachIndexed { index, tipper ->
                    UserAvatar(
                        modifier = imageModifier
                            .zIndex((tips.size - index).toFloat()),
                        data = tipper.profileImage.nullIfEmpty() ?: tipper.id
                    )
                }
            }
        }
    }
}

private fun String?.nullIfEmpty() = if (this?.isEmpty() == true) null else this