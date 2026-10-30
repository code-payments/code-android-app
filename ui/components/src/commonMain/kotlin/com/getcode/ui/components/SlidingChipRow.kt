package com.getcode.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import com.getcode.theme.White
import com.getcode.theme.White10
import kotlinx.coroutines.launch

/**
 * A row of preset chips with one white highlight that slides to the chosen chip, and fades out
 * when [selected] is null (a choice none of the chips stands for). The chips share the row's width
 * equally, as iOS's SlidingChipPicker does. A label too wide for its chip shrinks by up to 20%,
 * like iOS's minimumScaleFactor(0.8), and past that wraps at the space, so none is cut. Every chip
 * takes the height of the tallest, so the capsules stay uniform.
 *
 * With [reducedMotion] on, the highlight moves and fades straight to its new place.
 */
@Composable
fun <T> SlidingChipRow(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
) {
    val bounds = remember { mutableStateMapOf<T, Rect>() }
    val highlight = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val shown = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = selected?.let { bounds[it] }
    val labels = options.map { label(it) }
    val style = CodeTheme.typography.caption
    val measurer = rememberTextMeasurer()
    LaunchedEffect(selected, target) {
        val spec = chipMotion<Float>(reducedMotion)
        when {
            selected == null -> shown.animateTo(0f, spec)
            target == null -> Unit
            !placed -> {
                highlight.snapTo(target)
                shown.snapTo(1f)
                placed = true
            }
            else -> {
                launch { highlight.animateTo(target, chipMotion(reducedMotion)) }
                shown.animateTo(1f, spec)
            }
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = 6.dp
        val textWidth = with(LocalDensity.current) {
            ((maxWidth - gap * (options.size - 1)) / options.size - ChipPadding * 2).toPx().toInt()
        }
        val styles = remember(labels, style, textWidth, measurer) {
            labels.map { fitStyle(it, style, textWidth, measurer) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .selectableGroup()
                .drawBehind {
                    bounds.values.forEach { chip ->
                        drawRoundRect(White10, chip.topLeft, chip.size, CornerRadius(chip.height / 2))
                    }
                    val chip = highlight.value
                    drawRoundRect(White, chip.topLeft, chip.size, CornerRadius(chip.height / 2), alpha = shown.value)
                },
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = option == selected
                val color by animateColorAsState(
                    targetValue = if (isSelected) Color.Black else CodeTheme.colors.textMain,
                    animationSpec = chipMotion(reducedMotion),
                    label = "chip text",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onPlaced { bounds[option] = Rect(it.positionInParent(), it.size.toSize()) }
                        .clip(CircleShape)
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) }
                        .padding(horizontal = ChipPadding, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = labels[index],
                        style = styles[index],
                        color = color,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private val ChipPadding = 6.dp

/**
 * The largest of [style]'s size down to 80% at which [text] fits [width] on one line; failing
 * that, 80%, at which the label wraps.
 */
private fun fitStyle(text: String, style: TextStyle, width: Int, measurer: TextMeasurer): TextStyle {
    val sizes = (100 downTo 80 step 2).map { style.copy(fontSize = style.fontSize * (it / 100f)) }
    return sizes.firstOrNull { candidate ->
        val layout = measurer.measure(
            text = text,
            style = candidate,
            maxLines = 1,
            softWrap = false,
            constraints = Constraints(maxWidth = width.coerceAtLeast(0)),
        )
        !layout.didOverflowWidth
    } ?: sizes.last()
}

private fun <T> chipMotion(reducedMotion: Boolean): AnimationSpec<T> =
    if (reducedMotion) snap() else spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)

@Preview
@Composable
private fun SlidingChipRowPreview() {
    DesignSystem {
        var selected by remember { mutableStateOf<String?>("7 days") }
        SlidingChipRow(
            options = listOf("1 day", "7 days", "30 days", "Never"),
            selected = selected,
            onSelect = { selected = it },
            label = { it },
        )
    }
}
