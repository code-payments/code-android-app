package com.getcode.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White10

/**
 * A chip that can be selected, for choosing one filter among a few. [CodeChip] is a label with no
 * click or selected state, so this wraps it rather than changing what every other chip means.
 *
 * Selection is exposed to accessibility (`Role.RadioButton`, selected), because the colour change
 * is the only visual cue. Selected is a white tint at 16% over whatever is behind the chip, with the
 * label in the main text colour; unselected has no fill and uses the secondary text colour. Both
 * carry a 1dp white outline at 10%, as iOS draws them. A
 * solid white chip was the brightest thing on a dark screen. A [count] of null or 0 draws nothing:
 * the chips show a number only when there is something in the filter.
 */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) SelectedChipTint else Color.Transparent,
        label = "filterChipBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) CodeTheme.colors.textMain else CodeTheme.colors.textSecondary,
        label = "filterChipContent",
    )
    CodeChip(
        modifier = modifier
            .clip(CircleShape)
            // Drawn rather than passed as CodeChip's background so the animated fill only redraws.
            .drawBehind { drawRect(backgroundColor) }
            .border(1.dp, White10, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        backgroundColor = Color.Transparent,
        contentPadding = PaddingValues(
            horizontal = CodeTheme.dimens.grid.x3,
            // 7dp to match iOS's selectable chip; no grid step lands on it.
            vertical = 7.dp,
        ),
    ) {
        Text(
            text = label,
            style = CodeTheme.typography.textSmall,
            color = contentColor,
        )
        if (count != null && count > 0) {
            Text(
                text = count.toString(),
                style = CodeTheme.typography.textSmall,
                color = contentColor,
            )
        }
    }
}

// No theme token sits at 16%; White10 and White20 bracket it.
private val SelectedChipTint = White.copy(alpha = 0.16f)
