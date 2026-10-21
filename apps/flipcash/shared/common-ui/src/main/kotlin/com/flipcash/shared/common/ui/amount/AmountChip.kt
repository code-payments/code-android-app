package com.flipcash.shared.common.ui.amount

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import com.getcode.theme.White
import com.getcode.theme.White10

/**
 * A preset amount slot. Selected inverts to a solid white face — node 10127:118237's `$100`, and the
 * paid-reaction sheet's `$10` (node 10330:18196).
 *
 * @param content the chip's face, drawn in the text colour that reads against the current fill.
 */
@Composable
fun AmountChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (textColor: Color) -> Unit,
) {
    Box(
        modifier = modifier
            .height(AmountChipDefaults.Height)
            .clip(CodeTheme.shapes.extraSmall)
            .background(if (selected) White else White10)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(if (selected) CodeTheme.colors.background else CodeTheme.colors.textMain)
    }
}

object AmountChipDefaults {
    val Height = 60.dp

    /** The face's text: 22sp semibold. */
    val TextStyle: TextStyle
        @Composable get() = CodeTheme.typography.textLarge.copy(
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )
}
