package com.flipcash.app.menu.internal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.flipcash.core.R as CoreR
import com.flipcash.features.menu.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.utils.KeepScreenOn
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

/**
 * The full-screen presentation shared by the profile card and the group card: the backdrop fades
 * up over the sheet beneath, [content] pops in on a spring, brightness is raised for scanning, and
 * Download and Close sit in their usual places. Closing, by button or back, fades the screen out
 * before the route pops.
 *
 * @param onDownload null hides the Download button, for a card with nothing to export yet.
 * @param content the card, centred. [cardModifier] carries the reveal fade and scale and must be
 * applied to the card.
 */
@Composable
internal fun CardOverlay(
    onDownload: (() -> Unit)?,
    downloadTestTag: String,
    closeTestTag: String?,
    content: @Composable BoxScope.(cardModifier: Modifier) -> Unit,
) {
    val navigator = LocalCodeNavigator.current
    val haptics = LocalHapticFeedback.current

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        appeared = true
    }

    // Fade out, then pop, so the sheet beneath is uncovered rather than cut back to.
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        appeared = false
        delay(RevealFadeMillis.toLong())
        navigator.pop()
    }
    BackHandler(enabled = !closing) { closing = true }

    val fade by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = RevealFadeMillis, easing = EaseOut),
        label = "card fade",
    )
    // The backdrop leads the card in, and on the way out takes the card and Close with it.
    val backdropFade by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = RevealFadeMillis, easing = EaseOut),
        label = "card backdrop fade",
    )
    val cardScale by animateFloatAsState(
        targetValue = if (appeared) 1f else CardInitialScale,
        animationSpec = spring(dampingRatio = CardSpringDamping, stiffness = CardSpringStiffness),
        label = "card scale",
    )

    // As on iOS: the card is a code someone is about to scan, so a dim screen is raised to 0.6 for
    // as long as it is up (the boost is capped there, so anything under 0.4 lands on exactly 0.6).
    KeepScreenOn(
        isEnabled = true,
        useBrightness = true,
        minBrightness = ScanMinimumBrightness,
        brightnessBoost = ScanBoostedBrightness,
        maxBrightness = ScanBoostedBrightness,
    )

    // Everything under the Download button is its frosting source, as the cover is for the You tab's gear.
    val hazeState = rememberHazeState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = backdropFade }
            .background(CodeTheme.colors.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            content(
                Modifier.graphicsLayer {
                    alpha = fade
                    scaleX = cardScale
                    scaleY = cardScale
                },
            )
        }

        // Download stands where the You tab keeps its gear, so the gear reads as turning into it.
        // Nothing to export until the card resolves (or if it never does).
        if (onDownload != null) {
            ProfileBarButton(
                modifier = Modifier.align(Alignment.TopEnd),
                icon = R.drawable.ic_file_download,
                contentDescription = stringResource(R.string.action_download),
                onClick = onDownload,
                hazeState = hazeState,
                testTag = downloadTestTag,
            )
        }

        CodeButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(bottom = CodeTheme.dimens.grid.x4)
                .graphicsLayer { alpha = fade }
                .then(if (closeTestTag != null) Modifier.testTag(closeTestTag) else Modifier),
            buttonState = ButtonState.Subtle,
            onClick = { closing = true },
            text = stringResource(CoreR.string.description_chatMediaClose),
        )
    }
}

private const val ScanMinimumBrightness = 0.4f
private const val ScanBoostedBrightness = 0.6f

private const val RevealFadeMillis = 250

/** The card's size as it starts to pop in. */
private const val CardInitialScale = 0.55f

// SwiftUI `.spring(duration: 0.4, bounce: 0.6)`: damping ratio is 1 - bounce, and stiffness is
// (2 * pi / duration)^2. The damping ratio matches the scanned bill's pop,
// `AnimationUtils.animationBillEnterGrabbed`.
private const val CardSpringDamping = 0.4f
private const val CardSpringStiffness = 247f

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
