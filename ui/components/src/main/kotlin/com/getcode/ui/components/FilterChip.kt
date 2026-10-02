package com.getcode.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.getcode.theme.CodeTheme

/**
 * A chip that can be selected, for choosing one filter among a few. [CodeChip] is a label with no
 * click or selected state, so this wraps it rather than changing what every other chip means.
 *
 * Selection is exposed to accessibility (`Role.RadioButton`, selected), because the colour change
 * is the only visual cue. A [count] of null or 0 draws nothing: the chips show a number only when
 * there is something in the filter.
 */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val contentColor = if (selected) CodeTheme.colors.background else CodeTheme.colors.textMain
    CodeChip(
        modifier = modifier
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        backgroundColor = if (selected) CodeTheme.colors.textMain else CodeTheme.colors.surfaceVariant,
        contentPadding = PaddingValues(
            horizontal = CodeTheme.dimens.grid.x3,
            vertical = CodeTheme.dimens.grid.x1,
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
                color = contentColor.copy(alpha = 0.7f),
            )
        }
    }
}
