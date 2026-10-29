package com.getcode.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.PopupProperties
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraLarge
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A [DropdownMenu] whose submenu morphs out of the menu itself, as iOS draws a nested `Menu`.
 *
 * Material 3 has no nested menu. Opening a [SubmenuDropdownMenuScope.Submenu] folds every
 * [SubmenuDropdownMenuScope.Item] away, so the submenu's row slides to the top and becomes a bold
 * header with its chevron turned down; a divider and the submenu's rows unfold under it. The header,
 * or system back, reverses it. Height, width, fades and chevron run on springs, so a tap mid-animation
 * reverses from wherever it is. Dismissing the menu resets it.
 */
@Composable
fun SubmenuDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable SubmenuDropdownMenuScope.() -> Unit,
) {
    var openSubmenu by rememberSaveable(expanded) { mutableStateOf<String?>(null) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = CodeTheme.colors.brandLight,
        shape = CodeTheme.shapes.extraLarge,
        offset = offset,
        // Back folds the submenu before it closes the menu, so the popup must not take it first.
        properties = PopupProperties(focusable = true, dismissOnBackPress = openSubmenu == null),
    ) {
        BackHandler(enabled = openSubmenu != null) { openSubmenu = null }
        // Folding rows stop counting toward the width at once, so width and height move together.
        Column(Modifier.then(AnimatedWidthElement)) {
            val scope = SubmenuDropdownMenuScope(
                columnScope = this,
                openSubmenu = openSubmenu,
                onToggle = { key -> openSubmenu = if (openSubmenu == key) null else key },
            )
            scope.content()
        }
    }
}

class SubmenuDropdownMenuScope internal constructor(
    columnScope: ColumnScope,
    private val openSubmenu: String?,
    private val onToggle: (String) -> Unit,
) : ColumnScope by columnScope {

    /** A first-level row, or a divider between them: folds away while a submenu is open. */
    @Composable
    fun Item(content: @Composable () -> Unit) {
        Folding(visible = openSubmenu == null, content = content)
    }

    /**
     * A row that opens [content] in place: [label] with a trailing chevron that turns down and a
     * label that turns bold when open. [key] tells submenus apart and defaults to [label].
     */
    @Composable
    fun Submenu(
        label: String,
        icon: ImageVector,
        key: String = label,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        val open = openSubmenu == key
        val toggle = onToggle
        val chevronRotation by animateFloatAsState(
            targetValue = if (open) 90f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "chevron",
        )
        Folding(visible = openSubmenu == null || open) {
            Column {
                DropdownMenuItem(
                    modifier = Modifier.semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
                    leadingIcon = { Icon(imageVector = icon, contentDescription = null, tint = CodeTheme.colors.textMain) },
                    text = {
                        Text(
                            text = label,
                            style = CodeTheme.typography.textSmall,
                            fontWeight = if (open) FontWeight.Bold else null,
                            color = CodeTheme.colors.textMain,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = CodeTheme.colors.textMain,
                            modifier = Modifier.rotate(chevronRotation),
                        )
                    },
                    onClick = { toggle(key) },
                )
                Folding(visible = open) {
                    Column {
                        HorizontalDivider(color = CodeTheme.colors.divider)
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun Folding(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.then(FoldWidthElement(folding = !visible)),
        enter = Unfold,
        exit = Fold,
    ) { content() }
}

private val WidthSpring = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.5f)

/**
 * Eases the menu's width toward its rows' natural width, on the spring the rows' height uses.
 * [DropdownMenu] sizes its popup from intrinsic width, so the intrinsics report the eased width too.
 */
private data object AnimatedWidthElement : ModifierNodeElement<AnimatedWidthNode>() {
    override fun create() = AnimatedWidthNode()
    override fun update(node: AnimatedWidthNode) = Unit
}

private class AnimatedWidthNode : Modifier.Node(), LayoutModifierNode {
    private var width: Animatable<Float, AnimationVector1D>? = null

    private fun eased(target: Int): Int {
        val anim = width ?: Animatable(target.toFloat()).also { width = it }
        if (anim.targetValue != target.toFloat() && isAttached) {
            coroutineScope.launch { anim.animateTo(target.toFloat(), WidthSpring) }
        }
        return anim.value.roundToInt()
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val w = eased(measurable.maxIntrinsicWidth(constraints.maxHeight)).coerceIn(constraints.minWidth, constraints.maxWidth)
        val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
        return layout(w, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) =
        eased(measurable.maxIntrinsicWidth(height))

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) =
        eased(measurable.maxIntrinsicWidth(height))
}

/**
 * Keeps a row at least as wide as its own text while the menu narrows or widens around it, so the
 * text never rewraps mid-animation; the menu's shape clips the overhang. A [folding] row reports
 * no width, so the menu starts easing to its new width as the row starts to fold.
 */
private data class FoldWidthElement(val folding: Boolean) : ModifierNodeElement<FoldWidthNode>() {
    override fun create() = FoldWidthNode(folding)
    override fun update(node: FoldWidthNode) {
        node.folding = folding
        node.invalidateMeasurement()
    }
}

private class FoldWidthNode(var folding: Boolean) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val natural = measurable.maxIntrinsicWidth(constraints.maxHeight)
        val slot = if (constraints.hasBoundedWidth) constraints.maxWidth else natural
        val placeable = measurable.measure(
            constraints.copy(minWidth = slot, maxWidth = maxOf(slot, natural)),
        )
        return layout(minOf(placeable.width, slot), placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) =
        if (folding) 0 else measurable.maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) =
        if (folding) 0 else measurable.minIntrinsicWidth(height)
}

private val SizeSpring = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold)

private val Unfold: EnterTransition =
    expandVertically(SizeSpring, expandFrom = Alignment.Top) + fadeIn(spring(stiffness = Spring.StiffnessLow))

private val Fold: ExitTransition =
    shrinkVertically(SizeSpring, shrinkTowards = Alignment.Top) + fadeOut(spring(stiffness = Spring.StiffnessMedium))
