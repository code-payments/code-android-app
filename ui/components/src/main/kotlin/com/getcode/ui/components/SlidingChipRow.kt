package com.getcode.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.Role
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
 * when [selected] is null (a choice none of the chips stands for).
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .drawBehind {
                bounds.values.forEach { chip ->
                    drawRoundRect(White10, chip.topLeft, chip.size, CornerRadius(chip.height / 2))
                }
                val chip = highlight.value
                drawRoundRect(White, chip.topLeft, chip.size, CornerRadius(chip.height / 2), alpha = shown.value)
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val color by animateColorAsState(
                targetValue = if (isSelected) Color.Black else CodeTheme.colors.textMain,
                animationSpec = chipMotion(reducedMotion),
                label = "chip text",
            )
            Text(
                modifier = Modifier
                    .onPlaced { bounds[option] = Rect(it.positionInParent(), it.size.toSize()) }
                    .clip(CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                text = label(option),
                style = CodeTheme.typography.caption,
                color = color,
                maxLines = 1,
            )
        }
    }
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
