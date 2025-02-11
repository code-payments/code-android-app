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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.getcode.libs.opengraph.LocalOpenGraphParser
import com.getcode.libs.opengraph.callback.OpenGraphCallback
import com.getcode.libs.opengraph.model.OpenGraphResult
import com.getcode.model.chat.MessageStatus
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R
import com.getcode.ui.components.chat.MessageNodeDefaults
import com.getcode.ui.components.chat.MessageNodeOptions
import com.getcode.ui.components.chat.MessageNodeScope
import com.getcode.ui.components.chat.messagecontents.utils.AlignmentRule
import com.getcode.ui.components.chat.messagecontents.utils.rememberAlignmentRule
import com.getcode.ui.components.chat.utils.MessageTip
import com.getcode.ui.components.text.markup.Markup
import com.getcode.ui.components.text.markup.MarkupTextHelper
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.dashedBorder
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
    wasSentAsFullMember: Boolean,
    onTap: (contentPadding: PaddingValues, touchOffset: Offset) -> Unit,
    onLongPress: () -> Unit,
    onDoubleClick: () -> Unit,
    showTips: () -> Unit,
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
                ) {
                    if (tips.isNotEmpty()) {
                        Tips(tips = tips, isMessageFromSelf = isFromSelf) { openTipModal() }
                    }
                    DateWithStatus(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.Bottom),
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
                    isFromBlockedMember = isFromBlockedMember,
                )
                DateWithStatus(
                    modifier = Modifier
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