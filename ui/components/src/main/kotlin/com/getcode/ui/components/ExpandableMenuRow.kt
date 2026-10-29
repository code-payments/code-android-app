package com.getcode.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme

/**
 * A menu row that opens its [content] in place, below itself, instead of replacing the menu.
 *
 * Material 3 has no nested menu. The rows unfold downward from this one, first row first, while the chevron turns to
 * point down, so the popup, which is as tall as its content, grows with them. Height, fade and
 * chevron share one spring, so a tap mid-animation reverses from wherever it is. Indent the child
 * rows with [ExpandableMenuRowDefaults.ChildContentPadding].
 */
@Composable
fun ExpandableMenuRow(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chevron",
    )

    Column(modifier) {
        DropdownMenuItem(
            modifier = Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
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
                    modifier = Modifier.rotate(chevronRotation),
                )
            },
            onClick = { expanded = !expanded },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold),
                expandFrom = Alignment.Top,
            ) +
                fadeIn(spring(stiffness = Spring.StiffnessLow)),
            exit = shrinkVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold),
                shrinkTowards = Alignment.Top,
            ) +
                fadeOut(spring(stiffness = Spring.StiffnessMedium)),
            label = "expandableMenuRow",
        ) {
            Column(content = content)
        }
    }
}

object ExpandableMenuRowDefaults {
    /** Material's 12dp row padding, plus an indent so child rows read as part of the row above. */
    val ChildContentPadding = PaddingValues(start = 28.dp, end = 12.dp)
}
