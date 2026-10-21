@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.flipcash.app.internal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Transition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.asNavBarTab
import com.flipcash.app.core.ui.transitions.sharedBoundsTransition
import com.getcode.navigation.core.CodeNavigator
import kotlinx.coroutines.flow.first

/**
 * The nav bar's search button growing into Search's field, and back.
 *
 * The button lives in the root nav bar and the field in the Search entry, so they are in different
 * parts of the tree. Both hang off the Search entry's own enter/exit transition: the entry publishes
 * it here, and the bar animates its tab pill and search button as children of it. A predictive back
 * seeks that one transition, so the field shrinks into the button under the finger and the pill
 * fades back in step, and a cancelled gesture puts both back.
 */
@Stable
internal class SearchMorph(private val navigator: CodeNavigator) {
    /** The Search entry's transition while it is composed, or null when Search isn't up. */
    var transition: Transition<EnterExitState>? by mutableStateOf(null)
        private set

    /**
     * Set by the search button. Search can also arrive by popping a chat opened from its results;
     * the bar is hidden behind that chat, so it must not flash its pill on the way back.
     */
    private var openedFromBar by mutableStateOf(false)

    fun open() {
        openedFromBar = true
        navigator.push(AppRoute.Messaging.Search)
    }

    /**
     * Whether the bar's tab pill and search button are showing at [state] of the Search entry. They
     * hand over to Search while it arrives from the bar, and come back while it leaves to a tab. A
     * chat pushed over Search takes the whole bar away instead, so they stay hidden for that.
     */
    fun chromeVisible(state: EnterExitState): Boolean = when (state) {
        EnterExitState.PreEnter -> openedFromBar
        EnterExitState.Visible -> false
        EnterExitState.PostExit -> {
            val top = navigator.currentRouteKey
            top is AppRoute.Messaging.Search || (top as? AppRoute)?.asNavBarTab() != null
        }
    }

    /** Publishes the Search entry's transition. Call from inside the Search nav entry. */
    @Composable
    fun Publish() {
        val own = LocalNavAnimatedContentScope.current.transition
        DisposableEffect(own) {
            transition = own
            onDispose { if (transition === own) transition = null }
        }
        LaunchedEffect(own) {
            snapshotFlow { own.currentState == EnterExitState.Visible }.first { it }
            openedFromBar = false
        }
    }
}

/** Shared-bounds key for the search button and the field it grows into. */
internal const val SearchMorphKey = "search-morph"

/**
 * The button and the field are both pills, so the morph is clipped to a pill of whatever size it has
 * reached.
 */
internal val PillOverlayClip = object : SharedTransitionScope.OverlayClip {
    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path = Path().apply {
        addOutline(CircleShape.createOutline(bounds.size, layoutDirection, density))
        translate(bounds.topLeft)
    }
}

/**
 * Quicker than the default shared-bounds spring. The keyboard rises as the field lands, and while
 * the morph runs the field is drawn at the morph's bounds, not its own: a slow spring leaves it
 * chasing the bar up behind the keyboard.
 */
private val SearchMorphBounds = BoundsTransform { _, _ ->
    spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
        visibilityThreshold = Rect.VisibilityThreshold,
    )
}

/** Marks either end of the morph. [scope] defaults to the nav entry's own. */
@Composable
internal fun Modifier.searchMorph(scope: AnimatedVisibilityScope? = null): Modifier =
    sharedBoundsTransition(
        key = SearchMorphKey,
        animatedVisibilityScope = scope,
        boundsTransform = SearchMorphBounds,
        clipInOverlayDuringTransition = PillOverlayClip,
    )

/**
 * Bar chrome that hands over to Search. Outside Search it is simply shown; while Search is
 * composed it follows the Search entry's transition (see [SearchMorph.chromeVisible]).
 */
@Composable
internal fun SearchMorphChrome(
    morph: SearchMorph,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val transition = morph.transition
    if (transition == null) {
        AnimatedVisibility(visible = true, modifier = modifier, content = content)
    } else {
        transition.AnimatedVisibility(
            visible = morph::chromeVisible,
            modifier = modifier,
            enter = fadeIn(),
            exit = fadeOut(),
            content = content,
        )
    }
}
