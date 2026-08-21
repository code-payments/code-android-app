package com.getcode.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcode.theme.CodeTheme
import com.getcode.theme.White20
import com.getcode.ui.theme.CodeToggleSwitch
import androidx.compose.foundation.clickable

private val ListItemIconSize = 24.dp

/** 17sp Demi, per the settings rows in node 9276:4634 — not the 20sp of a section headline. */
private val ListItemHeadlineStyle
    @Composable get() = CodeTheme.typography.textMedium.copy(fontSize = 17.sp, lineHeight = 22.sp)

/**
 * Slot-based list row: icon + headline, with the caller driving the trailing [endSlot] — chevron,
 * loading spinner, beta badge, or any combination. Prefer this overload when the trailing content
 * is stateful (e.g. swaps to a spinner while the row's action is in flight).
 */
@Composable
fun ListItem(
    headline: String,
    icon: Painter?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    endSlot: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { onClick() }
            .padding(CodeTheme.dimens.grid.x5),
        verticalAlignment = CenterVertically
    ) {
        if (icon != null) {
            Image(
                modifier = Modifier
                    .padding(end = CodeTheme.dimens.grid.x4)
                    .size(ListItemIconSize),
                painter = icon,
                colorFilter = ColorFilter.tint(CodeTheme.colors.onBackground),
                contentDescription = ""
            )
        }

        Text(
            modifier = Modifier.align(CenterVertically),
            text = headline,
            style = ListItemHeadlineStyle,
            color = CodeTheme.colors.textMain,
        )

        Spacer(Modifier.weight(1f))

        endSlot()
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
        color = CodeTheme.colors.divider,
        thickness = 0.5.dp
    )
}

/**
 * Convenience row with the standard trailing content: an optional beta badge and a chevron.
 */
@Composable
fun ListItem(
    headline: String,
    icon: Painter?,
    modifier: Modifier = Modifier,
    showBetaIndicator: Boolean = false,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        headline = headline,
        icon = icon,
        modifier = modifier,
        onClick = onClick,
    ) {
        if (showBetaIndicator) {
            BetaIndicator()
        }

        if (showBetaIndicator && showChevron) {
            Spacer(Modifier.width(CodeTheme.dimens.grid.x2))
        }

        if (showChevron) {
            ListItemDefaults.Chevron()
        }
    }
}

object ListItemDefaults {
    /** The standard trailing disclosure chevron; also for callers driving their own [endSlot]. */
    @Composable
    fun Chevron() {
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = White20,
        )
    }

    /**
     * A trailing switch that keeps the row the same height as every other one. It is
     * presentation-only — the row's own `onClick` performs the toggle — and that is what buys the
     * height: Material only applies its 48dp minimum touch target when `onCheckedChange` is
     * non-null, which otherwise makes a toggle row ~24dp taller than its icon-and-chevron
     * neighbours. Pinning the node to the icon size lets the 32dp track paint centred over it,
     * inside the row's own padding.
     */
    @Composable
    fun Toggle(checked: Boolean, enabled: Boolean = true) {
        CodeToggleSwitch(
            modifier = Modifier.height(ListItemIconSize),
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
        )
    }
}
