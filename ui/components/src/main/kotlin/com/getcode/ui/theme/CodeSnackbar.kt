package com.getcode.ui.theme

import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Snackbar
import androidx.compose.material.SnackbarData
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.theme.BrandMuted
import com.getcode.theme.CodeTheme

/**
 * The action label is bold and, by default, the same colour as the message, matching the iOS
 * archive undo toast.
 */
@Composable
fun CodeSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
    actionOnNewLine: Boolean = false,
    shape: Shape = CodeTheme.shapes.small,
    backgroundColor: Color = BrandMuted,
    contentColor: Color = CodeTheme.colors.onBackground,
    actionColor: Color = contentColor,
    elevation: Dp = 6.dp
) {
    val actionLabel = snackbarData.actionLabel
    Snackbar(
        modifier = modifier,
        action = actionLabel?.let {
            {
                TextButton(
                    onClick = { snackbarData.performAction() },
                    colors = ButtonDefaults.textButtonColors(contentColor = actionColor),
                ) {
                    Text(text = it, fontWeight = FontWeight.Bold)
                }
            }
        },
        actionOnNewLine = actionOnNewLine,
        shape = shape,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        elevation = elevation,
    ) {
        Text(snackbarData.message)
    }
}
