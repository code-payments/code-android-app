package com.getcode.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.PopupProperties
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraLarge

private const val MenuSwapMillis = 220

/**
 * A [DropdownMenu] with one level of submenu.
 *
 * Material 3 has no nested menu, so this one swaps its own items in place, as SwiftUI's nested
 * `Menu` draws on iOS: [root] runs until a row calls the `openSubmenu` it is handed, then the menu
 * shows a header row (a back arrow and [submenuTitle]) over [submenu]. The header, or system back,
 * returns to [root]; dismissing the menu resets it to [root].
 */
@Composable
fun SubmenuDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    submenuTitle: String,
    submenuIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    root: @Composable ColumnScope.(openSubmenu: () -> Unit) -> Unit,
    submenu: @Composable ColumnScope.() -> Unit,
) {
    var inSubmenu by rememberSaveable(expanded) { mutableStateOf(false) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = CodeTheme.colors.brandLight,
        shape = CodeTheme.shapes.extraLarge,
        offset = offset,
        // Back steps out of the submenu before it closes the menu, so the popup must not take it first.
        properties = PopupProperties(focusable = true, dismissOnBackPress = !inSubmenu),
    ) {
        BackHandler(enabled = inSubmenu) { inSubmenu = false }
        // The popup is as big as its content, so animating the content's size grows and shrinks the
        // menu between levels while the rows cross-fade; clip stays off so the corners aren't cut mid-resize.
        AnimatedContent(
            targetState = inSubmenu,
            transitionSpec = {
                (fadeIn(tween(MenuSwapMillis, delayMillis = MenuSwapMillis / 3)) togetherWith
                    fadeOut(tween(MenuSwapMillis / 2)))
                    .using(SizeTransform(clip = false) { _, _ -> tween(MenuSwapMillis) })
            },
            label = "submenu",
        ) { showSubmenu ->
            Column {
                if (showSubmenu) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = null,
                                tint = CodeTheme.colors.textMain,
                            )
                        },
                        text = {
                            Text(
                                text = submenuTitle,
                                style = CodeTheme.typography.textSmall,
                                color = CodeTheme.colors.textMain,
                            )
                        },
                        onClick = { inSubmenu = false },
                    )
                    HorizontalDivider(color = CodeTheme.colors.divider)
                    submenu()
                } else {
                    root { inSubmenu = true }
                }
            }
        }
    }
}

/** The row in a menu's first level that opens its submenu: [label] with a trailing chevron. */
@Composable
fun SubmenuRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = { Icon(imageVector = icon, contentDescription = null, tint = CodeTheme.colors.textMain) },
        text = {
            Text(
                text = label,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textMain,
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = CodeTheme.colors.textMain,
            )
        },
        onClick = onClick,
    )
}
