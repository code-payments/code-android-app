package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.balanceRequirement
import com.flipcash.app.messenger.internal.mention.mentionListHeight
import com.flipcash.app.messenger.internal.mention.mentionRowCap
import com.flipcash.app.messenger.internal.requiresStaff
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.chat.models.ChatActionHandler
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.ChatInput
import com.getcode.ui.components.chat.ChatInputSubmit
import com.getcode.ui.core.drawWithGradient
import com.getcode.ui.core.measured
import com.getcode.ui.utils.rememberKeyboardController
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/** The ramp's shape: clear for most of its height, then a late rise into the background. */
private val ComposerFadeCurve = CubicBezierEasing(0.42f, 0.18f, 0.17f, 1.04f)

/** Short of opaque, so a bubble behind the bar's bottom edge still shows faintly. */
private const val ComposerFadeEndAlpha = 0.9f

private const val ComposerFadeSamples = 32

/** Sampled once: the curve has no gradient-stop equivalent, so it is laid down as stops. */
private val ComposerFadeStops: List<Pair<Float, Float>> = (0..ComposerFadeSamples).map { i ->
    val t = i / ComposerFadeSamples.toFloat()
    t to (ComposerFadeEndAlpha * ComposerFadeCurve.transform(t)).coerceIn(0f, 1f)
}

/** The dissolve from the transcript into [color] behind the composer, from [startY] to [endY]. */
private fun composerFade(color: Color, startY: Float, endY: Float): Brush = Brush.verticalGradient(
    colorStops = ComposerFadeStops.map { (t, alpha) -> t to color.copy(alpha = alpha) }.toTypedArray(),
    startY = startY,
    endY = endY,
)

@Composable
internal fun UserControlBottomBar(
    state: ChatViewModel.State,
    hazeState: HazeState,
    onAction: ChatActionHandler,
    dispatch: (ChatViewModel.Event) -> Unit,
    topBarHeight: Dp = 0.dp,
) {
    if (state.isAnonymous) {
        DeactivatedChatBottomBar()
        return
    }

    // The same shape as the block above it, and for the same reason: there is no composer to
    // configure when the viewer cannot post. `replacesComposer` holds for every viewer outside the
    // group — including an eligible one reading the transcript sharp, and the frames before the gate
    // has decided anything — and for as long as the gate holds its join confirmation.
    if (state.replacesComposer) {
        GroupGateBar(
            access = state.groupAccess,
            // The chat's own rule, read the same way the info card at the head of the transcript
            // reads it, so the two lines about this group cannot state different requirements.
            requirement = (state.subject as? ChatSubject.Group)?.rules.balanceRequirement(),
            staffOnly = (state.subject as? ChatSubject.Group)?.rules.requiresStaff() == true,
            currency = state.ruleCurrency,
            onAction = onAction,
            joinProgress = state.joinProgress,
        )
        return
    }

    // A member the speaker rules keep from speaking: nothing to type into, so the panel stands
    // where the composer would. Checked after the join gate, which owns every viewer outside.
    state.speakerBlock?.takeIf { state.isReadOnlySpeaker }?.let { block ->
        val rules = (state.subject as? ChatSubject.Group)?.rules
        val ruleMint = rules.balanceRequirement()?.mints?.firstOrNull()
        val blockMint = (block as? ChatRuleRequirement.MinimumBalance)?.mints?.firstOrNull()
        SpeakerGateBar(
            requirement = block,
            hazeState = hazeState,
            // The resolved currency describes the chat's stated balance rule; only name it when
            // that is the mint this requirement is about.
            currencyName = state.ruleCurrency?.nameInRequirement
                ?.takeIf { blockMint != null && ruleMint?.bytes == blockMint.bytes },
        )
        return
    }

    val keyboard = rememberKeyboardController()
    val focusRequester = remember { FocusRequester() }
    var buttonHeight by remember { mutableStateOf(0.dp) }
    var mentionListHeight by remember { mutableStateOf(0.dp) }
    val material = HazeMaterials.ultraThin(containerColor = CodeTheme.colors.background)

    LaunchedEffect(keyboard.visible) {
        if (!keyboard.visible) {
            dispatch(ChatViewModel.Event.OnStopMessageInput)
        }
    }

    // The bar is measured against the whole screen (the scaffold overlays it), so this is the
    // height the mention list's row cap weighs the transcript's share against.
    BoxWithConstraints {
        val screenHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            // Compact at rest: with the keyboard down the composer sits narrower and a little lower, into
            // the navigation bar's inset, and opens out to the normal margins as the keyboard comes up.
            // Only once there is a composer; the full-width Send Cash button keeps its width.
            //
            // Tracks how far open the keyboard is rather than springing on a keyboard up/down flag, so
            // the bar changes size in step with the keyboard's own motion: not ahead of it (a flag that
            // flips as the hide starts), and not after it (one that waits for the hide to finish). The
            // composer appearing at all (typingConstraints resolving a frame after the bar is revealed)
            // doesn't move anything, since nothing here animates on its own.
            val keyboardOpen = keyboardOpenFraction()
            val hasComposer = state.typingConstraints.enabled
            val compactInset = CodeTheme.dimens.grid.x6.coerceAtLeast(CodeTheme.dimens.inset)
            val sideInset = if (hasComposer) lerp(compactInset, CodeTheme.dimens.inset, keyboardOpen) else CodeTheme.dimens.inset
            val restingDrop = if (hasComposer) lerp(CodeTheme.dimens.grid.x2, 0.dp, keyboardOpen) else 0.dp
            // The part of the navigation bar's inset the keyboard isn't covering, so the bar only sinks
            // into it once the keyboard has cleared it.
            val restingRoom = WindowInsets.navigationBars.exclude(WindowInsets.ime)
            Box {
                // The transcript runs under the bar and dissolves into the background here, from the
                // bar's top edge to the bottom of the screen (or the keyboard's top edge). The bar has no
                // surface of its own and floats over it.
                val fadeColor = CodeTheme.colors.background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(buttonHeight)
                        .align(Alignment.BottomCenter)
                        .drawWithGradient(
                            brush = { startY, endY -> composerFade(fadeColor, startY, endY) },
                            startY = { 0f },
                        ),
                )
                AnimatedContent(
                    modifier = Modifier
                        .measured { buttonHeight = it.height }
                        // Reports itself shorter by the drop and lets the controls run past its bottom
                        // edge, so the bar sits that far into the navigation bar's inset and the
                        // transcript's bottom padding (measured from this height) follows it down.
                        .layout { measurable, constraints ->
                            val drop = restingDrop.roundToPx().coerceAtMost(restingRoom.getBottom(this))
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, (placeable.height - drop).coerceAtLeast(0)) {
                                placeable.place(0, 0)
                            }
                        }
                        .padding(vertical = CodeTheme.dimens.grid.x3)
                        .navigationBarsPadding()
                        // typingConstraints.enabled starts false and only resolves a frame or two after
                        // open, once Room confirms whether the chat has a cash message. Rendering the
                        // default false layout first showed a full-width "Send $" button that then
                        // scaled down to the pill + input box. Hold the bar invisible (but measured, so
                        // the message list keeps correct padding) until resolved, then reveal the final
                        // layout directly — no visible full-width state, no resize.
                        //
                        // The chat kind starts UNKNOWN and, for tip DMs, SendCashButton would otherwise
                        // read a not-yet-resolved chat as a non-tip chat and show the white expanded pill
                        // before condensing — a visible flash. Wait until the kind is known (chatType is
                        // CONTACT_DM or TIP_DM) so the bar reveals already in its final presentation.
                        // chatType resolves from a local contact lookup, not the network profile, so this
                        // adds no perceptible delay; a tip chat whose identity never resolves flips to the
                        // deactivated bar instead, so this can't hide it forever.
                        .alpha(
                            if (state.typingConstraints.resolved &&
                                state.chatType != ChatType.UNKNOWN
                            ) 1f else 0f
                        ),
                    targetState = state.typingConstraints.enabled,
                    // The layout only ever changes on the initial async resolution, which is hidden by
                    // the alpha gate above, so snap rather than crossfade. The SendCashButton's own
                    // color/label springs still animate the typing interaction.
                    transitionSpec = {
                        ContentTransform(
                            targetContentEnter = EnterTransition.None,
                            initialContentExit = ExitTransition.None,
                            sizeTransform = null,
                        )
                    },
                ) { canType ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Cards above the input row: mention suggestions, then the reply strip. A reply
                        // is a banner, unlike an edit, which swaps the leading control instead. The two
                        // differ in what the user needs to see: an edit's subject is already in front of
                        // them as the composer's text, while a reply's subject is a different message
                        // that is very likely scrolled off screen.
                        val accessories = composerAccessories(state, canType)
                        val divider = CodeTheme.dimens.border
                        val mentionRows = mentionRowCap(
                            replyOpen = accessories.any { it is ComposerAccessory.Reply },
                            // What the transcript and the list share: the screen less the top bar and
                            // the rest of this bar (the reply strip, input row and their padding).
                            roomAboveComposer = screenHeight - topBarHeight - (buttonHeight - mentionListHeight),
                            listHeight = { rows -> mentionListHeight(rows, divider) },
                        )
                        ComposerAccessoryStack(
                            accessories = accessories,
                            sideInset = sideInset,
                            mentionRows = mentionRows,
                            hazeState = hazeState,
                            dispatch = dispatch,
                            mentionListModifier = Modifier.measured { mentionListHeight = it.height },
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = sideInset),
                            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            // Editing swaps the leading control rather than adding a banner above the bar:
                            // send-cash is not reachable mid-edit anyway, and cancel is what the slot is
                            // for while the edit is open.
                            if (state.editing != null) {
                                CancelEditButton(
                                    onClick = { dispatch(ChatViewModel.Event.CancelEdit) },
                                )
                            } else {
                                SendCashButton(
                                    state = state,
                                    hazeState = hazeState,
                                    hazeMaterial = material,
                                    onClick = {
                                        keyboard.hideIfVisible {
                                            dispatch(ChatViewModel.Event.OnSendCash)
                                        }
                                    }
                                )
                            }

                            if (canType) {
                                ChatInput(
                                    modifier = Modifier
                                        .testTag("chat_message_input")
                                        .weight(1f)
                                        .border(
                                            CodeTheme.dimens.border,
                                            CodeTheme.colors.divider,
                                            CodeTheme.shapes.medium,
                                        )
                                        .hazeBlur(HazeInput.Sources(hazeState), material),
                                    focusRequester = focusRequester,
                                    hint = stringResource(
                                        if (state.replyingTo != null) R.string.hint_chatReply
                                        else R.string.hint_chatMessage
                                    ),
                                    state = state.chatInputState,
                                    // One read of the edit state decides both the glyph and what the tap
                                    // does, so the composer cannot show a checkmark and send a new message.
                                    submit = if (state.editing != null) {
                                        ChatInputSubmit.ConfirmEdit {
                                            dispatch(ChatViewModel.Event.SubmitEdit)
                                            keyboard.restartInput()
                                        }
                                    } else {
                                        ChatInputSubmit.Send {
                                            dispatch(ChatViewModel.Event.SendMessage)
                                            keyboard.restartInput()
                                        }
                                    },
                                )

                                // An edit starts from a long-press, which leaves the keyboard down, so the
                                // composer has to claim focus itself or the pre-filled text sits unreachable.
                                LaunchedEffect(state.editing?.messageId) {
                                    if (state.editing != null) {
                                        focusRequester.requestFocus()
                                        keyboard.show()
                                    }
                                }

                                // A reply starts from a long-press or a swipe, neither of which raises the
                                // keyboard, so the composer claims focus for the same reason.
                                LaunchedEffect(state.replyingTo?.messageId) {
                                    if (state.replyingTo != null) {
                                        focusRequester.requestFocus()
                                        keyboard.show()
                                    }
                                }

                                // Restores the pre-#1075 behavior: when OnStartMessageInput raises
                                // state.messageInputRequested (returning from amount entry after a send, or a
                                // post-tip open), focus the input and show the keyboard. Co-located with
                                // ChatInput so focusRequester is guaranteed attached; consumes the request so
                                // it fires once and a later manual dismiss doesn't re-open it.
                                LaunchedEffect(state.messageInputRequested) {
                                    if (state.messageInputRequested) {
                                        focusRequester.requestFocus()
                                        keyboard.show()
                                        dispatch(ChatViewModel.Event.OnMessageInputConsumed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Leaves edit mode. Sized to the send-cash button it stands in for so the bar doesn't reflow. */
@Composable
private fun CancelEditButton(onClick: () -> Unit) {
    val shape = CodeTheme.shapes.medium
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 54.dp, minHeight = 54.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.1f), shape)
            .border(CodeTheme.dimens.border, CodeTheme.colors.divider, shape)
            .clickable(onClick = onClick)
            .testTag("chat_cancel_edit"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.action_cancelEdit),
            tint = Color.White,
            modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x5),
        )
    }
}

@Composable
private fun DeactivatedChatBottomBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(vertical = CodeTheme.dimens.grid.x3),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.subtitle_chatNoLongerAvailable),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * How far open the keyboard is, from 0 (down) to 1 (fully up), following its show and hide
 * animations frame by frame.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun keyboardOpenFraction(): Float {
    val density = LocalDensity.current
    val now = WindowInsets.ime.getBottom(density)
    val full = maxOf(
        WindowInsets.imeAnimationSource.getBottom(density),
        WindowInsets.imeAnimationTarget.getBottom(density),
        now,
    )
    return if (full <= 0) 0f else now.toFloat() / full
}
