package com.flipcash.shared.common.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.toast.ToastBottomClearance
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

/**
 * The one action a profile pins to the bottom of its screen: a filled button, with an optional
 * [above] line over it (a footer, a shortfall) and an optional text action ([secondaryText])
 * under it.
 *
 * The bar sits on the screen's background, clears the navigation bar, and measures itself,
 * system bar included. While it is composed it asks the root toast host to rest toasts above that
 * height, and withdraws the request when it leaves, so show it only when something is pinned. The
 * same height goes to [onHeightChanged] for the caller's scroll content to leave room for; it is
 * the caller's to ignore once the bar is gone.
 *
 * Align it to the bottom of its parent through [modifier].
 */
@Composable
fun ProfilePinnedActionBar(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    above: (@Composable () -> Unit)? = null,
    secondaryText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    isSecondaryLoading: Boolean = false,
    onHeightChanged: (Dp) -> Unit = {},
) {
    val density = LocalDensity.current
    var height by remember { mutableStateOf(0.dp) }
    ToastBottomClearance(height)

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Measured before the inset is applied, so the height includes the system bar.
            .onSizeChanged {
                val measured = with(density) { it.height.toDp() }
                height = measured
                onHeightChanged(measured)
            }
            .background(CodeTheme.colors.background)
            .navigationBarsPadding()
            // iOS: 12 above, 8 below and 8 between, on the 5dp grid.
            .padding(vertical = CodeTheme.dimens.staticGrid.x2),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x2),
    ) {
        above?.invoke()
        CodeButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset),
            buttonState = ButtonState.Filled,
            text = text,
            enabled = enabled,
            isLoading = isLoading,
            onClick = onClick,
        )
        if (secondaryText != null && onSecondaryClick != null) {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset),
                buttonState = ButtonState.Subtle,
                text = secondaryText,
                isLoading = isSecondaryLoading,
                enabled = !isSecondaryLoading,
                onClick = onSecondaryClick,
            )
        }
    }
}
