package com.getcode.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.getcode.model.ID
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.MessageStatus
import com.getcode.model.chat.Sender
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R
import com.getcode.ui.components.chat.messagecontents.AnnouncementMessage
import com.getcode.ui.components.chat.messagecontents.DeletedMessage
import com.getcode.ui.components.chat.messagecontents.EncryptedContent
import com.getcode.ui.components.chat.messagecontents.MessagePayment
import com.getcode.ui.components.chat.messagecontents.MessageReplyContent
import com.getcode.ui.components.chat.messagecontents.MessageText
import com.getcode.ui.components.chat.utils.ReplyMessageAnchor
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.ui.components.text.markup.Markup
import com.getcode.util.vibration.LocalVibrator
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.reflect.KClass

object MessageNodeDefaults {

    val DefaultShape: CornerBasedShape
        @Composable get() = CodeTheme.shapes.small

    private val PreviousSameShapeIncoming: CornerBasedShape
        @Composable get() = DefaultShape.copy(topStart = CornerSize(3.dp))

    private val NextSameShapeIncoming: CornerBasedShape
        @Composable get() = DefaultShape.copy(bottomStart = CornerSize(3.dp), topStart = CornerSize(3.dp))

    private val MiddleSameShapeIncoming: CornerBasedShape
        @Composable get() = DefaultShape.copy(
            topStart = CornerSize(3.dp),
            bottomStart = CornerSize(3.dp)
        )

    private val PreviousSameShapeOutgoing: CornerBasedShape
        @Composable get() = DefaultShape.copy(topEnd = CornerSize(3.dp))

    private val NextSameShapeOutgoing: CornerBasedShape
        @Composable get() = DefaultShape.copy(bottomEnd = CornerSize(3.dp), topEnd = CornerSize(3.dp))

    private val MiddleSameShapeOutgoing: CornerBasedShape
        @Composable get() = DefaultShape.copy(
            bottomEnd = CornerSize(3.dp),
            topEnd = CornerSize(3.dp)
        )

    @Composable
    fun messageShape(
        isIncoming: Boolean,
        isPreviousInGroup: Boolean,
        isNextInGroup: Boolean,
    ): Shape {
        return if (isIncoming) {
            when {
                isPreviousInGroup && isNextInGroup -> MiddleSameShapeIncoming
                isPreviousInGroup -> PreviousSameShapeIncoming
                isNextInGroup -> NextSameShapeIncoming
                else -> DefaultShape.copy(topStart = CornerSize(3.dp))
            }
        } else {
            when {
                isPreviousInGroup && isNextInGroup -> MiddleSameShapeOutgoing
                isPreviousInGroup -> PreviousSameShapeOutgoing
                isNextInGroup -> NextSameShapeOutgoing
                else -> DefaultShape.copy(topEnd = CornerSize(3.dp))
            }
        }
    }

    val ContentStyle: TextStyle
        @Composable get() = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.W500)

    val SwipeThreshold
        @Composable get() = 25.dp
}

class MessageNodeScope(
    private val contents: MessageContent,
    private val boxScope: BoxWithConstraintsScope
) {
    fun Modifier.sizeableWidth() =
        this.widthIn(max = boxScope.maxWidth * 0.85f)

    val isAnnouncement: Boolean
        @Composable get() = remember {
            when (contents) {
                is MessageContent.Announcement -> true
                else -> false
            }
        }

    val color: Color
        @Composable get() = when {
            isAnnouncement -> CodeTheme.colors.secondary
            contents.isFromSelf -> CodeTheme.colors.secondary
            else -> CodeTheme.colors.brandDark
        }
}

@Composable
private fun rememberMessageNodeScope(
    contents: MessageContent,
    boxScope: BoxWithConstraintsScope
): MessageNodeScope {
    return remember(contents, boxScope) {
        MessageNodeScope(contents, boxScope)
    }
}

data class MessageNodeOptions(
    val showStatus: Boolean = true,
    val showTimestamp: Boolean = true,
    val isPreviousGrouped: Boolean = false,
    val isNextGrouped: Boolean = false,
    val isInteractive: Boolean = false,
    val canReplyTo: Boolean = false,
    val markupsToResolve: List<KClass<out Markup>> = listOf(
        Markup.RoomNumber::class,
        Markup.Url::class,
        Markup.Phone::class
    ),
    val onMarkupClicked: ((Markup) -> Unit)? = null,
    val contentStyle: TextStyle,
)

private enum class MessageNodeDragAnchors {
    DEFAULT, REPLY
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageNode(
    contents: MessageContent,
    isDeleted: Boolean,
    date: Instant,
    sender: Sender,
    status: MessageStatus,
    originalMessage: ReplyMessageAnchor?,
    modifier: Modifier = Modifier,
    options: MessageNodeOptions = MessageNodeOptions(contentStyle = MessageNodeDefaults.ContentStyle),
    openMessageControls: () -> Unit,
    onReply: () -> Unit,
    onViewOriginalMessage: (ID) -> Unit,
) {
    val vibrator = LocalVibrator.current

    val enableReply = remember(contents, options) {
        if (!options.canReplyTo) return@remember false
        when (contents) {
            is MessageContent.RawText -> true
            is MessageContent.Reply -> true
            else -> false
        }
    }

    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidth = maxWidth
        val swipeThreshold = with(density) { maxWidth.toPx() } * 0.30f
        var hasTriggeredTick by remember { mutableStateOf(false) }
        var hasFiredReply by remember { mutableStateOf(false) }

        val anchors = remember(maxWidth) {
            DraggableAnchors {
                MessageNodeDragAnchors.DEFAULT at 0f
                MessageNodeDragAnchors.REPLY at swipeThreshold
            }
        }

        val replyDragState = remember(anchors) {
            AnchoredDraggableState(
                initialValue = MessageNodeDragAnchors.DEFAULT,
                anchors = anchors,
                positionalThreshold = { swipeThreshold },
                velocityThreshold = { with(density) { 50.dp.toPx() } },
                confirmValueChange = { targetValue ->
                    if (targetValue == MessageNodeDragAnchors.REPLY && !hasFiredReply) {
                        hasFiredReply = true
                        onReply()
                    }
                    true
                },
                snapAnimationSpec = tween(durationMillis = 400),
                decayAnimationSpec = splineBasedDecay(density)
            )
        }

        LaunchedEffect(replyDragState.currentValue) {
            if (replyDragState.currentValue == MessageNodeDragAnchors.REPLY) {
                // Reset drag state to allow future replies
                delay(200)
                try {
                    replyDragState.snapTo(MessageNodeDragAnchors.DEFAULT)
                    println("Animation completed")
                } catch (e: CancellationException) {
                    println("Animation canceled: ${e.message}")
                }

                hasFiredReply = false
                hasTriggeredTick = false
            }
        }

        LaunchedEffect(replyDragState.offset) {
            if (replyDragState.offset >= swipeThreshold && !hasTriggeredTick) {
                hasTriggeredTick = true
                vibrator.tick()
            }
        }

        Box(
            modifier = modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .anchoredDraggable(
                    state = replyDragState,
                    enabled = enableReply,
                    orientation = Orientation.Horizontal,
                    reverseDirection = false
                )
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .offset { IntOffset(x = replyDragState.offset.coerceAtMost(swipeThreshold).roundToInt(), y = 0) }
            ) {
                val scope = rememberMessageNodeScope(
                    contents = contents,
                    boxScope = this@BoxWithConstraints
                )

                with(scope) {
                    if (isDeleted) {
                        DeletedMessage(
                            modifier = Modifier.fillMaxWidth(),
                            isFromSelf = sender.isSelf,
                            date = date,
                        )
                    } else {
                        val shape = when {
                            isAnnouncement -> MessageNodeDefaults.DefaultShape
                            else -> MessageNodeDefaults.messageShape(
                                !sender.isSelf, options.isPreviousGrouped, options.isNextGrouped
                            )
                        }

                        when (contents) {
                            is MessageContent.Exchange -> {
                                val alignment = if (sender.isSelf) Alignment.CenterEnd else Alignment.CenterStart
                                Box(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
                                    MessagePayment(
                                        modifier = Modifier
                                            .sizeableWidth()
                                            .background(color = color, shape = shape),
                                        contents = contents,
                                        status = status,
                                        date = date,
                                    )
                                }
                            }

                            is MessageContent.Localized -> {
                                ContentFromSender(
                                    modifier = Modifier.fillMaxWidth(),
                                    sender = sender,
                                    isFirstInSeries = !options.isPreviousGrouped
                                ) {
                                    MessageText(
                                        content = contents.localizedText,
                                        shape = shape,
                                        date = date,
                                        status = status,
                                        isFromSelf = sender.isSelf,
                                        options = options,
                                        showControls = openMessageControls
                                    )
                                }
                            }

                            is MessageContent.SodiumBox -> {
                                val alignment = if (sender.isSelf) Alignment.CenterEnd else Alignment.CenterStart
                                Box(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
                                    EncryptedContent(
                                        modifier = Modifier
                                            .sizeableWidth()
                                            .background(
                                                color = color,
                                                shape = shape,
                                            )
                                            .padding(CodeTheme.dimens.grid.x2),
                                        date = date
                                    )
                                }
                            }

                            is MessageContent.Decrypted -> {
                                ContentFromSender(
                                    modifier = Modifier.fillMaxWidth(),
                                    sender = sender,
                                    isFirstInSeries = !options.isPreviousGrouped
                                ) {
                                    MessageText(
                                        content = contents.data,
                                        shape = shape,
                                        date = date,
                                        status = status,
                                        isFromSelf = sender.isSelf,
                                        options = options,
                                        showControls = openMessageControls
                                    )
                                }
                            }

                            is MessageContent.RawText -> {
                                ContentFromSender(
                                    modifier = Modifier.fillMaxWidth(),
                                    sender = sender,
                                    isFirstInSeries = !options.isPreviousGrouped
                                ) {
                                    MessageText(
                                        content = contents.value,
                                        shape = shape,
                                        date = date,
                                        status = status,
                                        isFromSelf = sender.isSelf,
                                        options = options,
                                        showControls = openMessageControls
                                    )
                                }
                            }

                            is MessageContent.Announcement -> {
                                AnnouncementMessage(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = contents.localizedText
                                )
                            }

                            is MessageContent.Reaction -> Unit
                            is MessageContent.Reply -> {
                                ContentFromSender(
                                    modifier = Modifier.fillMaxWidth(),
                                    sender = sender,
                                    isFirstInSeries = !options.isPreviousGrouped
                                ) {
                                    if (originalMessage != null) {
                                        MessageReplyContent(
                                            content = contents.text,
                                            shape = shape,
                                            date = date,
                                            status = status,
                                            isFromSelf = sender.isSelf,
                                            options = options,
                                            showControls = openMessageControls,
                                            originalMessage = originalMessage,
                                            onOriginalMessageClicked = {
                                                onViewOriginalMessage(originalMessage.id)
                                            }
                                        )
                                    } else {
                                        MessageText(
                                            content = contents.text,
                                            shape = shape,
                                            date = date,
                                            status = status,
                                            isFromSelf = sender.isSelf,
                                            options = options,
                                            showControls = openMessageControls,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = replyDragState.offset > 0f,
                enter = fadeIn() + scaleIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
                    .offset { IntOffset(x = replyDragState.offset.coerceAtMost(swipeThreshold).roundToInt() - 20.dp.roundToPx(), y = 0) }
            ) {
                Icon(
                    modifier = Modifier,
                    imageVector = Icons.AutoMirrored.Default.Reply,
                    contentDescription = "Swipe to Reply",
                )
            }
        }
    }
}

@Composable
private fun ContentFromSender(
    modifier: Modifier = Modifier,
    sender: Sender,
    isFirstInSeries: Boolean,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
    ) {
        if (!sender.isSelf) {
            if (isFirstInSeries) {
                Column {
                    Spacer(Modifier.size(CodeTheme.dimens.grid.x4))
                    Box(
                        modifier = Modifier
                            .padding(top = CodeTheme.dimens.grid.x1)
                    ) {
                        UserAvatar(
                            modifier = Modifier
                                .size(CodeTheme.dimens.staticGrid.x8)
                                .clip(CircleShape),
                            data = sender.profileImage ?: sender.id,
                            overlay = {
                                Image(
                                    modifier = Modifier.padding(5.dp),
                                    imageVector = Icons.Default.Person,
                                    colorFilter = ColorFilter.tint(Color.White),
                                    contentDescription = null,
                                )
                            }
                        )

                        if (sender.isHost) {
                            Image(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(
                                        x = -(CodeTheme.dimens.grid.x1),
                                        y = -(CodeTheme.dimens.grid.x1)
                                    )
                                    .size(CodeTheme.dimens.staticGrid.x4)
                                    .background(color = Color(0xFFE9C432), shape = CircleShape)
                                    .padding(4.dp),
                                painter = painterResource(R.drawable.ic_crown),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(CodeTheme.colors.brand)
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.size(CodeTheme.dimens.staticGrid.x8))
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
            ) {
                if (!sender.isSelf && isFirstInSeries) {
                    Text(
                        text = sender.displayName.orEmpty(),
                        style = CodeTheme.typography.caption,
                        color = CodeTheme.colors.tertiary
                    )
                }
                content()
            }
        }
    }
}