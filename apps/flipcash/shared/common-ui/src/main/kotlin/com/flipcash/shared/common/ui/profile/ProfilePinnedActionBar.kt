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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.toast.ToastBottomClearance
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

/**
 * The one action a profile pins to the bottom of its screen: a filled button, with an optional
 * [above] line over it and an optional text action ([secondaryText]) under it.
 *
 * Given the [hazeState] the screen's scroll content draws into, the content runs on under the bar
 * and fades off the bottom of the screen: it blurs in and dims to the background from the bar's top
 * edge down over [FadeHeight], so by the first row it is gone and [above] reads clear of it. Without a [hazeState] the
 * bar sits on the screen's background.
 *
 * The bar clears the navigation bar and measures itself,
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
    hazeState: HazeState? = null,
    onHeightChanged: (Dp) -> Unit = {},
) {
    val background = CodeTheme.colors.background
    val material = HazeMaterials.ultraThin(containerColor = background)

    val density = LocalDensity.current
    val fadePx = with(density) { FadeHeight.toPx() }
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
            .then(
                if (hazeState != null) {
                    Modifier
                        .hazeBlur(
                            HazeInput.Sources(hazeState),
                            material.then {
                                progressive(HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f))
                            },
                        )
                        // The blur alone leaves shapes showing through; the dim takes them off.
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(background.copy(alpha = 0f), background),
                                startY = 0f,
                                endY = fadePx,
                            ),
                        )
                } else {
                    Modifier.background(background)
                },
            )
            // The fade, then iOS's 12 above, 8 below and 8 between, on the 5dp grid.
            .then(if (hazeState != null) Modifier.padding(top = FadeHeight) else Modifier)
            .padding(top = CodeTheme.dimens.staticGrid.x2)
            .navigationBarsPadding()
            .padding(bottom = CodeTheme.dimens.staticGrid.x2),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x2),
        // iOS stacks these in a VStack, which centres a line narrower than the buttons.
        horizontalAlignment = Alignment.CenterHorizontally,
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

/** The band over the bar's first row in which the content fades off; iOS fades over 56pt. */
private val FadeHeight = 40.dp
