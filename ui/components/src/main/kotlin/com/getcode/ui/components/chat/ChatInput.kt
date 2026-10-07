package com.getcode.ui.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import com.getcode.theme.extraSmall
import com.getcode.theme.inputColors
import com.getcode.ui.components.R
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.getcode.ui.utils.rememberKeyboardController
import kotlin.math.roundToInt

/**
 * What the composer will do with the text it holds, and the action that does it.
 *
 * Editing an existing message reuses this composer rather than opening one of its own, so the submit
 * control has to say which of the two it is about to do. Pairing the glyph with the action here
 * means a caller cannot put up a checkmark that sends a new message, and leaves it reading its own
 * edit state once rather than once per parameter.
 */
sealed interface ChatInputSubmit {
    val perform: () -> Unit

    /** Sends the field's text as a new message. */
    data class Send(override val perform: () -> Unit) : ChatInputSubmit

    /** Confirms an edit of a message already in the transcript. */
    data class ConfirmEdit(override val perform: () -> Unit) : ChatInputSubmit

    /**
     * A named action that does not need the field's text, such as sending a group invite with an
     * optional message. Shown as [label] whether or not anything is typed, or as a spinner in the
     * label's place while [busy], so the button keeps its width as the action runs.
     */
    data class Action(
        val label: String,
        val busy: Boolean = false,
        override val perform: () -> Unit,
    ) : ChatInputSubmit
}

object ChatInputDefaults {
    /** The field's own fill: a faint lift over the bar. A caller drawing its own glass passes transparent. */
    val ContainerColor = Color.White.copy(alpha = 0.03f)

    /** The field's padding around its controls; the attach menu's leading edge sits this far out from "+"'s. */
    val FieldPadding: Dp
        @Composable get() = CodeTheme.dimens.staticGrid.x2

    /** The leading and send controls' diameter, and the text row's minimum height. */
    val AccessorySize: Dp
        @Composable get() = CodeTheme.dimens.staticGrid.x6

    /**
     * The `$` / cancel slot's diameter, and the height of the one-line field beside it: the
     * accessory plus the padding above and below it. The reply strip, the cancel button and the
     * attach menu's anchor all read this one value.
     */
    val OutsideSize: Dp
        @Composable get() = oneLineFieldHeight(FieldPadding, AccessorySize)

    /**
     * The corner of the thumbnails the composer's header holds. The field's own corner is this plus
     * the header inset, and the photo chips draw with it, so the two curves share a centre.
     */
    val HeaderChipCorner: Dp
        @Composable get() = CodeTheme.dimens.staticGrid.x2

    /** The 1dp rim on the field and the circle beside it: bright at the top edge, nearly gone at the bottom. */
    val RimBrush: Brush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))
    )
}

/** The one-line field's height: its controls plus the padding above and below them. */
internal fun oneLineFieldHeight(fieldPad: Dp, accessorySize: Dp): Dp = fieldPad * 2 + accessorySize

/**
 * Whether the composer stacks its text over a row of controls instead of keeping everything on one
 * row. The text stacks once the [draft] wraps ([draftLineWidth], its width on one line, exceeds
 * [inlineWidth], the room beside the controls) or holds a newline, stays stacked while anything is
 * left ([wasStacked]), and unstacks only when the draft is cleared. [inlineWidth] of 0 means the
 * field has not been measured, when only a newline stacks.
 */
internal fun composerStacks(
    draft: CharSequence,
    wasStacked: Boolean,
    draftLineWidth: Int,
    inlineWidth: Int,
): Boolean {
    if (draft.isEmpty()) return false
    if (wasStacked) return true
    if (draft.contains('\n')) return true
    return inlineWidth > 0 && draftLineWidth > inlineWidth
}

/**
 * The composer's measurements, all from the theme's grid so they follow the screen's size class.
 * Resolved once by [composerMetrics] and handed to the shape, the layout and the stacking
 * check, so none of them reads a constant of its own.
 */
@Immutable
internal data class ComposerMetrics(
    /** The field's corner around stacked text, from iOS `BarMetrics.fieldCornerRadius`. */
    val fieldCorner: Dp,
    /** Where the header's top edge sits below the field's: iOS `fieldPadding` + half `fieldVerticalPadding`. */
    val headerTopInset: Dp,
    /** The corner of the thumbnails the header holds, so the field can be concentric with them. */
    val headerInnerCorner: Dp,
    /** The field's padding around its controls. */
    val fieldPad: Dp,
    /** The leading and send controls' diameter, and the text row's minimum height. */
    val accessorySize: Dp,
    /** The text's inset from the field's sides while stacked or with no leading control. */
    val stackedTextInset: Dp,
    /** The gap between the controls and the text. */
    val controlSpacing: Dp,
    /** The gap between the outside control and the field. */
    val outsideSpacing: Dp,
)

@Composable
internal fun composerMetrics(): ComposerMetrics {
    val grid = CodeTheme.dimens.staticGrid
    return ComposerMetrics(
        fieldCorner = grid.x6,
        headerTopInset = grid.x2,
        headerInnerCorner = ChatInputDefaults.HeaderChipCorner,
        fieldPad = ChatInputDefaults.FieldPadding,
        accessorySize = ChatInputDefaults.AccessorySize,
        stackedTextInset = grid.x2,
        controlSpacing = grid.x2,
        outsideSpacing = grid.x2,
    )
}

/**
 * The radius that keeps an outer corner concentric with an inner one [inset] away from it: the
 * inner radius plus the gap, so both curves share a centre.
 */
internal fun concentricRadius(inner: Float, inset: Float): Float = inner + inset

/**
 * The field's corner radius for a field [height] tall, in the same unit as the other arguments.
 * Around a header it is concentric with the header's rounded thumbnails; otherwise it is the stacked
 * text's corner. Either is capped at half the height, so a one-line field is a capsule, and because
 * [height] is the animated one the corner follows the layout between the arrangements.
 */
internal fun composerCornerRadius(
    height: Float,
    hasHeader: Boolean,
    headerInnerCorner: Float,
    headerInset: Float,
    stackedCorner: Float,
): Float {
    val base = if (hasHeader) concentricRadius(headerInnerCorner, headerInset) else stackedCorner
    return base.coerceAtMost(height / 2f).coerceAtLeast(0f)
}

/** A rounded rectangle whose radius is [composerCornerRadius] of the size it is drawn at. */
private class ComposerFieldShape(
    private val hasHeader: Boolean,
    private val metrics: ComposerMetrics,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = composerCornerRadius(
            height = size.height,
            hasHeader = hasHeader,
            headerInnerCorner = with(density) { metrics.headerInnerCorner.toPx() },
            headerInset = with(density) { metrics.headerTopInset.toPx() },
            stackedCorner = with(density) { metrics.fieldCorner.toPx() },
        )
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
    }
}

private const val MaxLines = 5

/** iOS `ChatMotion.replySurface`: duration 0.28, bounce 0, so damping ratio 1 and stiffness (2pi/0.28)^2. */
private val StackSpring = spring<Float>(dampingRatio = 1f, stiffness = 503f)

private enum class Slot { Header, Text, Leading, Send }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatInput(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hint: String = "",
    state: TextFieldState = rememberTextFieldState(),
    focusRequester: FocusRequester = remember { FocusRequester() },
    containerColor: Color = ChatInputDefaults.ContainerColor,
    submit: ChatInputSubmit,
    // Whether something other than text is waiting to be sent (staged photos), which shows the send
    // control with an empty field.
    hasAttachments: Boolean = false,
    // Whether a tap on the send control does anything. Dimmed when not, so a chip that failed to
    // upload reads as the reason nothing sends.
    sendEnabled: Boolean = true,
    // The control at the field's leading edge, inside it. Null for none.
    leading: (@Composable () -> Unit)? = null,
    // Content in the field above the text, such as staged photos. Null for none.
    header: (@Composable () -> Unit)? = null,
    // A control before the field, outside it (cancel edit). Stays while the field holds text. Null
    // for none.
    outside: (@Composable () -> Unit)? = null,
    // A control after the field, outside it (send cash). Makes way while the field holds text.
    // Null for none.
    trailingOutside: (@Composable () -> Unit)? = null,
) {
    val textStyle = CodeTheme.typography.textMedium.copy(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = CodeTheme.colors.textMain,
    )
    val hasText = state.text.isNotEmpty()
    val sendVisible = hasText || hasAttachments || submit is ChatInputSubmit.Action
    val sendSpec = spring<Float>(dampingRatio = 0.66f, stiffness = 4000f)
    val sendAlpha by animateFloatAsState(if (sendVisible) 1f else 0f, sendSpec, label = "send alpha")
    val sendScale by animateFloatAsState(if (sendVisible) 1f else 0.6f, sendSpec, label = "send scale")

    // spring(duration 0.4, bounce 0.3)
    val outsideSpec = spring<Float>(dampingRatio = 0.7f, stiffness = 247f)
    val outsideFraction by animateFloatAsState(
        targetValue = if (outside != null) 1f else 0f,
        animationSpec = outsideSpec,
        label = "outside fraction",
    )
    val trailingOutsideFraction by animateFloatAsState(
        targetValue = if (trailingOutside != null && !hasText) 1f else 0f,
        animationSpec = outsideSpec,
        label = "trailing outside fraction",
    )

    var fieldWidth by remember { mutableIntStateOf(0) }
    var leadingWidth by remember { mutableIntStateOf(0) }
    var sendWidth by remember { mutableIntStateOf(0) }
    var stacked by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val metrics = composerMetrics()
    val outsideSize = ChatInputDefaults.OutsideSize
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state, textMeasurer, textStyle, density, metrics) {
        snapshotFlow {
            val draft = state.text
            val pad = with(density) { metrics.fieldPad.roundToPx() }
            val gap = with(density) { metrics.controlSpacing.roundToPx() }
            val inlineX = if (leadingWidth > 0) pad + leadingWidth + gap else with(density) { metrics.stackedTextInset.roundToPx() }
            val inline = fieldWidth - inlineX - (pad + sendWidth + gap)
            val lineWidth = if (draft.isEmpty()) 0 else textMeasurer.measure(
                text = draft.toString(),
                style = textStyle,
                overflow = TextOverflow.Clip,
                softWrap = false,
            ).size.width
            composerStacks(draft, stacked, lineWidth, if (fieldWidth == 0) 0 else inline)
        }.collect { stacked = it }
    }
    val stackFraction by animateFloatAsState(
        targetValue = if (stacked) 1f else 0f,
        animationSpec = StackSpring,
        label = "stack fraction",
    )

    val keyboard = rememberKeyboardController()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(keyboard.visible) {
        if (!keyboard.visible) focusManager.clearFocus(true)
    }

    val shape = remember(header != null, metrics) { ComposerFieldShape(hasHeader = header != null, metrics = metrics) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (outside != null) {
            OutsideSlot(outsideFraction, outsideSize, metrics.outsideSpacing, outside)
        }
        Layout(
            modifier = Modifier
                .weight(1f)
                // Above the trailing control, which the field slides over as it widens.
                .zIndex(1f)
                .onSizeChanged { fieldWidth = it.width }
                .clip(shape)
                .then(modifier)
                .background(containerColor, shape)
                .border(CodeTheme.dimens.border, ChatInputDefaults.RimBrush, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                ) {
                    focusRequester.requestFocus()
                    keyboard.show()
                },
            content = {
                if (header != null) Box(Modifier.layoutId(Slot.Header)) { header() }
                Box(Modifier.layoutId(Slot.Text)) {
                    BasicTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        state = state,
                        enabled = enabled,
                        textStyle = textStyle,
                        cursorBrush = SolidColor(CodeTheme.colors.textMain),
                        keyboardOptions = KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = MaxLines),
                        decorator = { inner ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = metrics.accessorySize),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (!hasText && hint.isNotEmpty()) {
                                    Text(
                                        text = hint,
                                        style = textStyle,
                                        color = CodeTheme.colors.textSecondary,
                                        maxLines = 1,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
                if (leading != null) Box(
                    Modifier
                        .layoutId(Slot.Leading)
                        .onSizeChanged { leadingWidth = it.width }
                ) { leading() }
                Box(
                    modifier = Modifier
                        .layoutId(Slot.Send)
                        .onSizeChanged { sendWidth = it.width }
                        .graphicsLayer {
                            alpha = if (sendEnabled) sendAlpha else sendAlpha * 0.4f
                            scaleX = sendScale
                            scaleY = sendScale
                        }
                        .defaultMinSize(minWidth = metrics.accessorySize, minHeight = metrics.accessorySize)
                        .clip(CircleShape)
                        .background(Color.White, CircleShape)
                        // Reads the current submit rather than the one the crossfade happens to
                        // be showing, so a tap mid-transition does what the composer is now for.
                        .clickable(enabled = sendVisible && enabled && sendEnabled) { submit.perform() },
                    contentAlignment = Alignment.Center,
                ) {
                    SendGlyph(submit)
                }
            },
            measurePolicy = remember(density, scope, metrics) {
                ComposerMeasurePolicy(
                    metrics = metrics,
                    stackFraction = { stackFraction },
                    stacked = { stacked },
                    scope = scope,
                )
            },
        )
        if (trailingOutside != null) {
            TrailingOutsideSlot(trailingOutsideFraction, outsideSize, metrics.outsideSpacing, trailingOutside)
        }
    }
}

/**
 * The control before the field. It scales and fades in with [fraction] and takes its width, and
 * the [spacing] between it and the field, from the row as it does.
 */
@Composable
private fun OutsideSlot(
    fraction: Float,
    size: Dp,
    spacing: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.layout { measurable, _ ->
            val sizePx = size.roundToPx()
            val f = fraction.coerceAtLeast(0f)
            val p = measurable.measure(Constraints.fixed(sizePx, sizePx))
            val gap = (spacing.roundToPx() * f).roundToInt()
            layout(((sizePx * f).roundToInt() + gap).coerceAtLeast(0), sizePx) {
                p.placeWithLayer(0, 0) {
                    val k = 0.4f + 0.6f * f.coerceAtMost(1f)
                    scaleX = k
                    scaleY = k
                    alpha = f.coerceIn(0f, 1f)
                }
            }
        },
    ) { content() }
}

/**
 * The control after the field. The row gives its width up to the field as [fraction] falls, but the
 * control stays where it is: it fades out and shrinks in place while the field, drawn above it,
 * widens across it, and plays the same in reverse as [fraction] rises.
 */
@Composable
private fun TrailingOutsideSlot(
    fraction: Float,
    size: Dp,
    spacing: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.layout { measurable, _ ->
            val sizePx = size.roundToPx()
            val f = fraction.coerceAtLeast(0f)
            val p = measurable.measure(Constraints.fixed(sizePx, sizePx))
            val width = ((sizePx + spacing.roundToPx()) * f).roundToInt().coerceAtLeast(0)
            layout(width, sizePx) {
                // Pinned to the row's trailing edge however much width the slot reports.
                p.placeWithLayer(width - sizePx, 0) {
                    val k = 0.5f + 0.5f * f.coerceIn(0f, 1f)
                    scaleX = k
                    scaleY = k
                    alpha = f.coerceIn(0f, 1f)
                }
            }
        },
    ) { content() }
}

/**
 * Lays the composer's four parts out in one of two arrangements without recomposing them, so the
 * text field keeps its place in the tree (and its focus and IME session) when the layout changes.
 * Inline puts `leading | text | send` on one row; stacked gives the text the full width above a row
 * of the controls. [stackFraction] is the one animated progress (iOS `replySurface`, 0.28s, no
 * bounce) that the field's height, the controls' and the text's positions all read, and the corner
 * follows from the height, so nothing else animates the change; the text is measured for the target
 * arrangement.
 */
private class ComposerMeasurePolicy(
    private val metrics: ComposerMetrics,
    private val stackFraction: () -> Float,
    private val stacked: () -> Boolean,
    private val scope: kotlinx.coroutines.CoroutineScope,
) : MeasurePolicy {
    // A change in content height that is not a stack toggle (a chip arriving, another wrapped line)
    // is taken as an offset equal to minus the change, so the field starts where it was, then eased
    // to zero on the same spring as the stack progress. Everything is placed relative to the bottom
    // edge plus this offset, so the content rides the field's height rather than jumping.
    private var residual by mutableFloatStateOf(0f)
    private var residualJob: Job? = null
    private var lastTarget = -1
    private var lastStacked = false

    private fun easeResidual() {
        residualJob?.cancel()
        residualJob = scope.launch {
            Animatable(residual).animateTo(0f, StackSpring) { residual = value }
        }
    }

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val width = constraints.maxWidth
        val pad = metrics.fieldPad.roundToPx()
        val gap = metrics.controlSpacing.roundToPx()
        val stackedInset = metrics.stackedTextInset.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val header = measurables.firstOrNull { it.layoutId == Slot.Header }
            ?.measure(loose.copy(maxWidth = width))
        val leading = measurables.firstOrNull { it.layoutId == Slot.Leading }?.measure(loose)
        val send = measurables.first { it.layoutId == Slot.Send }.measure(loose)
        val leadW = leading?.width ?: 0
        val minRow = metrics.accessorySize.roundToPx()

        val inlineX = if (leading != null) pad + leadW + gap else stackedInset
        val inlineW = (width - inlineX - (pad + send.width + gap)).coerceAtLeast(0)
        val stackedW = (width - 2 * stackedInset).coerceAtLeast(0)
        val target = stacked()
        val textW = if (target) stackedW else inlineW
        val text = measurables.first { it.layoutId == Slot.Text }
            .measure(Constraints(minWidth = textW, maxWidth = textW))

        val headerTop = metrics.headerTopInset.roundToPx()
        val headerBlock = if (header != null) headerTop + header.height + gap else pad
        val controlsH = maxOf(minRow, leading?.height ?: 0, send.height)
        val inlineRow = maxOf(minRow, text.height, controlsH)
        val inlineH = headerBlock + inlineRow + pad
        val stackedH = headerBlock + text.height + controlsH + pad
        val t = stackFraction().coerceIn(0f, 1f)
        val naturalH = lerp(inlineH, stackedH, t)
        val heightTarget = if (stacked()) stackedH else inlineH
        if (lastTarget >= 0 && heightTarget != lastTarget && stacked() == lastStacked) {
            residual -= (heightTarget - lastTarget).toFloat()
            easeResidual()
        }
        lastTarget = heightTarget
        lastStacked = stacked()
        val shift = residual.roundToInt()
        val height = (naturalH + shift).coerceAtLeast(0)

        return layout(width, height) {
            header?.place(0, headerTop + shift)
            val rowTop = headerBlock
            // Inline: controls bottom-aligned on the row; stacked: on the row under the text.
            val inlineBottom = rowTop + inlineRow
            val stackedBottom = naturalH - pad
            val bottom = lerp(inlineBottom, stackedBottom, t)
            leading?.place(pad, bottom - leading.height + shift)
            send.place(width - pad - send.width, bottom - send.height + shift)
            val textX = lerp(inlineX, stackedInset, t)
            val textY = lerp(rowTop + (inlineRow - text.height) / 2, rowTop, t)
            text.place(textX, textY + shift)
        }
    }
}

@Composable
private fun SendGlyph(submit: ChatInputSubmit) {
    // The button stays put and only its glyph changes, so entering and leaving edit
    // mode reads as the same control changing meaning rather than two controls
    // swapping places.
    val sendSpec = spring<Float>(dampingRatio = 0.66f, stiffness = 4000f)
    AnimatedContent(
        targetState = submit,
        // Keyed by kind, not by value: each submit carries a fresh lambda, so
        // equality alone would restart the crossfade on every recomposition.
        contentKey = { it::class },
        transitionSpec = {
            (fadeIn(sendSpec) + scaleIn(sendSpec, initialScale = 0.6f)) togetherWith
                    (fadeOut(sendSpec) + scaleOut(sendSpec, targetScale = 0.6f))
        },
        label = "send glyph",
    ) { target ->
        if (target is ChatInputSubmit.Action) {
            // Reads the live submit, not the crossfade's captured target: the busy
            // flag changes without changing the kind, so no crossfade runs for it.
            val busy = (submit as? ChatInputSubmit.Action)?.busy == true
            Box(contentAlignment = Alignment.Center) {
                Text(
                    modifier = Modifier
                        .padding(horizontal = CodeTheme.dimens.staticGrid.x2)
                        .graphicsLayer { alpha = if (busy) 0f else 1f },
                    text = target.label,
                    style = CodeTheme.typography.textMedium,
                    color = Color.Black,
                )
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
                        color = Color.Black,
                        strokeWidth = CodeTheme.dimens.thickBorder,
                    )
                }
            }
        } else if (target is ChatInputSubmit.ConfirmEdit) {
            Icon(
                modifier = Modifier
                    .testTag("chat_confirm_edit_icon")
                    .size(CodeTheme.dimens.staticGrid.x4),
                imageVector = Icons.Rounded.Check,
                tint = Color.Black,
                contentDescription = "Confirm edit",
            )
        } else {
            Icon(
                modifier = Modifier
                    .testTag("chat_send_icon")
                    .size(CodeTheme.dimens.staticGrid.x4),
                painter = painterResource(R.drawable.ic_arrow_up),
                tint = Color.Black,
                contentDescription = "Send message",
            )
        }
    }
}

@Preview
@Composable
private fun Preview_ChatInput_Empty() {
    DesignSystem {
        Box(modifier = Modifier.background(Color(0xFF19191A))) {
            ChatInput(
                modifier = Modifier.padding(15.dp),
                submit = ChatInputSubmit.Send {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_ChatInput_Typing() {
    DesignSystem {
        Box(modifier = Modifier.background(Color(0xFF19191A))) {
            ChatInput(
                modifier = Modifier.padding(15.dp),
                submit = ChatInputSubmit.Send {},
                state = TextFieldState("That’s very kind of you. I ha")
            )
        }
    }
}

@Preview
@Composable
private fun Preview_ChatInput_Action() {
    DesignSystem {
        Box(modifier = Modifier.background(Color(0xFF19191A))) {
            ChatInput(
                modifier = Modifier.padding(15.dp),
                hint = "Add a message",
                submit = ChatInputSubmit.Action(label = "Invite") {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_ChatInput_ActionBusy() {
    DesignSystem {
        Box(modifier = Modifier.background(Color(0xFF19191A))) {
            ChatInput(
                modifier = Modifier.padding(15.dp),
                hint = "Add a message",
                enabled = false,
                submit = ChatInputSubmit.Action(label = "Invite", busy = true) {},
            )
        }
    }
}

@Preview
@Composable
private fun Preview_ChatInput_Editing() {
    DesignSystem {
        Box(modifier = Modifier.background(Color(0xFF19191A))) {
            ChatInput(
                modifier = Modifier.padding(15.dp),
                submit = ChatInputSubmit.ConfirmEdit {},
                state = TextFieldState("That’s very kind of you. I have")
            )
        }
    }
}

