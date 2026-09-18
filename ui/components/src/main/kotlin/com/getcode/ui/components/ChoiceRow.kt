package com.getcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.extraSmall

private val IconSize = 24.dp

/**
 * A rounded card that reads as one tappable choice: icon, label, nothing else.
 *
 * Not [ListItem] — that draws a settings list, with a divider baked in and no card behind it. The
 * group-chat screens (nodes 10127:117987, 10127:118315) stack separated cards instead, so the row
 * carries its own background and the column spaces them apart.
 */
@Composable
fun ChoiceRow(
    label: String,
    icon: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CodeTheme.shapes.extraSmall)
            .background(White05)
            .clickable(onClick = onClick)
            .padding(CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        Icon(
            modifier = Modifier.size(IconSize),
            painter = painterResource(icon),
            contentDescription = null,
            tint = CodeTheme.colors.textMain,
        )
        Text(
            text = label,
            // 17sp Demi, matching the settings rows rather than the 16sp of `textMedium`.
            style = CodeTheme.typography.textMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
            color = CodeTheme.colors.textMain,
        )
    }
}
