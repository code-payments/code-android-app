package com.getcode.ui.components.toast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.R
import com.getcode.theme.White
import com.getcode.ui.components.glass.floatingGlass
import dev.chrisbanes.haze.HazeState

/**
 * The app's toast: a pill on the navigation bar's glass, holding an optional [icon], the [message],
 * and an optional action in its own pill. iOS draws the same design.
 *
 * It fills the width it is given unless [hugContent], when it wraps the message instead. Give it
 * [FloatingChrome.horizontalInset][com.getcode.ui.components.glass.FloatingChrome.horizontalInset]
 * on each side so it is never wider than the bar. [hazeState] is the bar's; without one the glass
 * falls back to the bar's near-opaque fill.
 *
 * With no [actionLabel] nothing here takes pointer input, so taps pass through to what is beneath.
 * Show it through [FloatingToastHost], which owns its motion, lifetime and swipe to dismiss.
 */
@Composable
fun FloatingToast(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    hugContent: Boolean = false,
    hazeState: HazeState? = null,
) {
    Row(
        modifier = modifier
            .then(if (hugContent) Modifier else Modifier.fillMaxWidth())
            .floatingGlass(hazeState)
            .padding(
                PaddingValues(
                    start = 16.dp,
                    // With an action, its 48dp touch area sets the height instead.
                    top = if (actionLabel != null) 0.dp else 8.dp,
                    bottom = if (actionLabel != null) 0.dp else 8.dp,
                    end = if (actionLabel != null) 8.dp else 16.dp,
                )
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CodeTheme.colors.textMain,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = message,
            style = CodeTheme.typography.textSmall.copy(fontFamily = AvenirMedium, fontWeight = FontWeight.Medium),
            color = CodeTheme.colors.textMain,
            modifier = Modifier
                // Weighted children measure after the fixed ones, so a long message wraps rather
                // than squeezing the action; without fill, a hugging toast stays the message's width.
                .weight(1f, fill = !hugContent)
                // The action pill's vertical padding, so a toast is the same height with or without one.
                .padding(vertical = 7.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (actionLabel != null) {
            val interactionSource = remember { MutableInteractionSource() }
            // The touch area is the toast's full height; the ripple stays on the visible pill.
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onAction,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actionLabel,
                    style = CodeTheme.typography.textSmall,
                    fontWeight = FontWeight.Bold,
                    color = CodeTheme.colors.textMain,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(ActionFill)
                        .indication(interactionSource, ripple())
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
        }
    }
}

// No theme token sits at 12%; White10 is the nearest below it.
private val ActionFill = White.copy(alpha = 0.12f)

// The theme maps Medium to the Demi file, so name the Medium cut directly: iOS sets this message in
// Avenir Next Medium.
private val AvenirMedium = FontFamily(Font(R.font.avenir_next_medium, FontWeight.Medium))
