package com.flipcash.app.menu

import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import com.flipcash.app.menu.internal.ProfileBarButton
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.bills.ScannableRenderer
import com.flipcash.app.bills.components.cards.LocalTipCardBaseAlpha
import com.flipcash.app.bills.components.cards.LocalTipCardColor
import com.flipcash.app.bills.components.cards.TipCardFlattened
import com.flipcash.app.menu.internal.ProfileCardViewModel
import com.flipcash.core.R as CoreR
import com.flipcash.features.menu.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

/**
 * The viewer's own profile card, full screen, behind `AppRoute.Menu.ProfileCard`. It presents like
 * a scanned bill: the card pops in on a spring while the Close button fades in. The backdrop's
 * fade in and the whole screen's fade out belong to the navigation transition, which fades this
 * route in place of the default slide. Download, where the You tab keeps its settings gear, hands
 * the card to the system share sheet as an image.
 */
@Composable
fun ProfileCardScreen() {
    val viewModel = hiltViewModel<ProfileCardViewModel>()
    val navigator = LocalCodeNavigator.current
    val haptics = LocalHapticFeedback.current
    val card = viewModel.card

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        appeared = true
    }

    val fade by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = RevealFadeMillis, easing = EaseOut),
        label = "profile card fade",
    )
    val cardScale by animateFloatAsState(
        targetValue = if (appeared) 1f else CardInitialScale,
        animationSpec = spring(dampingRatio = CardSpringDamping, stiffness = CardSpringStiffness),
        label = "profile card scale",
    )

    // Everything under the Download button is its frosting source, as the cover is for the You tab's gear.
    val hazeState = rememberHazeState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CodeTheme.colors.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            if (card != null) {
                // Static backdrop, so draw the card opaque at its flattened tone, as the You tab did.
                CompositionLocalProvider(
                    LocalTipCardColor provides TipCardFlattened,
                    LocalTipCardBaseAlpha provides 1f,
                ) {
                    ScannableRenderer(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer {
                                alpha = fade
                                scaleX = cardScale
                                scaleY = cardScale
                            },
                        scannable = card,
                        tipCardWidth = CodeTheme.dimens.screenWidth * CardWidthFraction,
                    )
                }
            }
        }

        // Download stands where the You tab keeps its gear, so the gear reads as turning into it.
        // Nothing to export until the card resolves (or if it never does).
        if (card != null) {
            ProfileBarButton(
                modifier = Modifier.align(Alignment.TopEnd),
                icon = R.drawable.ic_file_download,
                contentDescription = stringResource(R.string.action_download),
                onClick = viewModel::download,
                hazeState = hazeState,
                testTag = "you-download-button",
            )
        }

        CodeButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(bottom = CodeTheme.dimens.grid.x4)
                .graphicsLayer { alpha = fade },
            buttonState = ButtonState.Subtle,
            onClick = { navigator.pop() },
            text = stringResource(CoreR.string.description_chatMediaClose),
        )
    }
}

/** How much of the display the card spans, the proportion the earlier full-screen card used. */
private const val CardWidthFraction = 0.75f

private const val RevealFadeMillis = 250

/** The card's size as it starts to pop in. */
private const val CardInitialScale = 0.55f

// SwiftUI `.spring(duration: 0.4, bounce: 0.6)`: damping ratio is 1 - bounce, and stiffness is
// (2 * pi / duration)^2. The damping ratio matches the scanned bill's pop,
// `AnimationUtils.animationBillEnterGrabbed`.
private const val CardSpringDamping = 0.4f
private const val CardSpringStiffness = 247f

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
