package com.getcode.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.PopupProperties
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraLarge

/**
 * A [DropdownMenu] whose submenu morphs out of the menu itself, as iOS draws a nested `Menu`.
 *
 * Material 3 has no nested menu. Opening a [SubmenuDropdownMenuScope.Submenu] folds every
 * [SubmenuDropdownMenuScope.Item] away, so the submenu's row slides to the top and becomes a bold
 * header with its chevron turned down; a divider and the submenu's rows unfold under it. The header,
 * or system back, reverses it. Size, fades and chevron run on springs, so a tap mid-animation
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
        // Rows keep their width while they fold, then leave; this eases the width change that follows.
        Column(Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))) {
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
    AnimatedVisibility(visible = visible, enter = Unfold, exit = Fold) { content() }
}

private val SizeSpring = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold)

private val Unfold: EnterTransition =
    expandVertically(SizeSpring, expandFrom = Alignment.Top) + fadeIn(spring(stiffness = Spring.StiffnessLow))

private val Fold: ExitTransition =
    shrinkVertically(SizeSpring, shrinkTowards = Alignment.Top) + fadeOut(spring(stiffness = Spring.StiffnessMedium))
