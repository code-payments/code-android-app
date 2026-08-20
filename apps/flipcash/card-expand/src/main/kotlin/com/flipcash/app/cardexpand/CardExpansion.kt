package com.flipcash.app.cardexpand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.withContext

/**
 * Coordinates an "expand a card into a full-screen overlay" presentation (Apple-Wallet / iOS #587 style)
 * between the screen that owns the source card (e.g. the wallet deck) and an app-level host that renders
 * the expanded content as an overlay above the nav content.
 *
 * It is deliberately **key-agnostic**: [expandedKey] is an opaque token (the owner passes whatever
 * identifies the card — a mint, an id, …) and the host matches on it to decide what to render. No domain
 * types leak into this module.
 *
 * A single [progress] scalar (0 = collapsed into the deck, 1 = fully expanded detail) drives everything:
 * the deck reorganisation, the hero card's travel from [sourceBounds] to its expanded frame, and the
 * detail fade. Tapping animates it to 1; a dismiss (button or interactive drag) drives it back to 0.
 */
@Stable
class CardExpansionController {

    /** The opaque key of the expanding card, or null when fully collapsed / at rest. */
    var expandedKey: Any? by mutableStateOf(null)
        private set

    /** Window-space bounds of the tapped source card — the hero's start frame. */
    var sourceBounds: Rect? by mutableStateOf(null)
        private set

    /**
     * Window-space bounds of the expanded hero frame (reported by the overlay once measured). The deck's
     * source card flies to exactly this frame in step with the overlay's hero, so the two coincide and
     * the deck card — which stays at its natural z-order, under its neighbours — carries the hand-off on
     * collapse with no z snap.
     */
    var heroBounds: Rect? by mutableStateOf(null)

    fun reportHeroBounds(bounds: Rect) { heroBounds = bounds }

    /**
     * The overlay hero's current pull-to-close translation (px, downward), published so the deck's own
     * card (drawn in the wallet) can add the same offset and stay coincident with the overlay hero
     * throughout the pull + release — otherwise the two separate and read as two cards.
     */
    var pullOffset: Float by mutableStateOf(0f)

    /**
     * Supplies the user's real animation-duration scale (0 = "animations off"). Defaults to 1; the
     * host wires this to the live system setting (`Settings.Global.ANIMATOR_DURATION_SCALE`). We read
     * the setting explicitly rather than relying on the ambient [MotionDurationScale], because in some
     * hosting scopes that ambient value is stale/0 even when the user has animations enabled — which
     * would snap the expand instantly. Reading the setting keeps us honest both ways: a user who truly
     * disabled animations still gets an instant (scale 0) transition.
     */
    var animationScale: () -> Float = { 1f }

    /**
     * 0 = collapsed (in the deck), 1 = fully expanded (detail).
     *
     * Sub-pixel visibility threshold (vs the 0.01 default). The tween specs drive the settle cleanly on
     * their own, but the tiny threshold keeps any interruption/retarget (e.g. an interactive drag handed to
     * an animation) from ending a few-px short of the target — which, given the hero's several-hundred-px
     * travel, would read as a jump right before it rests.
     */
    val progress: Animatable<Float, *> = Animatable(0f, visibilityThreshold = 0.0001f)

    val isExpanded: Boolean get() = expandedKey != null

    /**
     * Begin expanding [key] from the on-screen [bounds] of its source card. The caller then animates
     * [progress] toward 1 (see [animateTo]).
     */
    fun begin(key: Any, bounds: Rect) {
        expandedKey = key
        sourceBounds = bounds
        // Clear per-open transient state so a new expansion never inherits the PREVIOUS card's fly target
        // or a residual pull. (Progress is snapped to 0 by the caller before animating — see the open path
        // in the wallet — so opening a card mid-collapse of another starts from a clean deck, not a
        // half-reorganised one.)
        heroBounds = null
        pullOffset = 0f
    }

    /**
     * Begin expanding [key] with **no source card** — there is nothing on screen to fly from, so the
     * expansion has no travel and simply lands already open. Used by deeplinks, which arrive with the
     * wallet not yet on screen (and for a token the user may not even hold, so no deck card exists).
     * Mirrors iOS `WalletScreen.openCardImmediately`.
     *
     * The host snaps [progress] to 1 once the expanded hero frame has been measured — see
     * `CardExpandHost`.
     */
    fun beginExpanded(key: Any) {
        expandedKey = key
        sourceBounds = null
        // [heroBounds] is deliberately NOT cleared here (unlike [begin]): with no flight it is not a
        // per-card fly target but the overlay's fixed hero slot, identical for every card. Keeping a
        // previous measurement lets the host snap open on the very first frame instead of waiting for
        // a re-measure — and when there is none (cold start) it is null anyway.
        pullOffset = 0f
    }

    /** Animate [progress] to [target] (0 collapse / 1 expand) with the shared card-expand spring. */
    suspend fun animateTo(target: Float, spec: AnimationSpec<Float> = ExpandSpring) {
        // Drive the animation under the user's real duration scale (read once per run) rather than the
        // ambient MotionDurationScale, which can be a stale 0 in this scope and would snap the whole
        // transition. A genuinely-disabled setting (0) still snaps — as it should.
        val scale = animationScale().coerceAtLeast(0f)
        withContext(object : MotionDurationScale { override val scaleFactor: Float = scale }) {
            progress.animateTo(target, spec)
        }
    }

    /** Directly set [progress] — for scrubbing with an interactive drag. */
    suspend fun snapTo(target: Float) {
        progress.snapTo(target.coerceIn(0f, 1f))
    }

    /** Drop the expansion once fully collapsed (progress back at 0), returning the deck to its own screen. */
    fun clear() {
        expandedKey = null
        sourceBounds = null
    }

    companion object {
        /**
         * Timed curve for the expand — a **tween, deliberately not a physics spring**. A `spring()`
         * approaches its target asymptotically and terminates on a velocity/threshold condition; that final
         * approach, amplified by the hero card's several-hundred-px travel, reads as a small settle JITTER
         * right before it rests (and again on reinsert). A tween eases to the target and lands EXACTLY on it
         * on its final frame — a clean, definitive stop with no tail. This is the spec the transition shipped
         * smooth with; the later spring swap is what introduced the both-ends settle jitter. [FastOutSlowInEasing]
         * gives the responsive-start-into-gentle-settle that matches the iOS reference. Tune the duration for
         * speed — it does not affect settle cleanliness.
         */
        val ExpandSpring: AnimationSpec<Float> =
            tween(durationMillis = 520, easing = FastOutSlowInEasing)

        /**
         * Collapse/dismiss curve — a tween like [ExpandSpring] (same clean, definitive settle), just shorter
         * so the exit is snappier than the enter (matches iOS, where the dismiss is quicker).
         */
        val CollapseSpring: AnimationSpec<Float> =
            tween(durationMillis = 440, easing = FastOutSlowInEasing)

        /**
         * Split point between the two phases. Phase A [0→HeroPhase]: the hero card rises to the top while
         * the deck COMPRESSES into a tight, still-visible stack beneath it. Phase B [HeroPhase→1]: only
         * then does the detail (background + content) fade in, tucking over the squeezed deck. Running them
         * sequentially — rather than fading the detail in over the still-compressing deck — is what keeps
         * the compressed deck VISIBLE under the rising card (the iOS look) instead of the background
         * erasing it mid-transition. Because the compressed deck is an on-screen tight stack (not off the
         * edges), the collapse still crossfades cleanly: the detail fades to reveal the compressed deck,
         * which then re-opens — no flash of black.
         *
         * Set fairly late (0.70) so phase A owns most of the transition: the hero visibly rises and the
         * deck compresses into a tight, still-uncovered stack before the detail background (which ramps
         * opaque over the first third of phase B) covers it. Too early a split and the background erased
         * the deck while it was still mid-compression, so the open read as a crossfade rather than the
         * iOS lift-then-cover.
         */
        const val HeroPhase = 0.70f

        /**
         * Sub-progress (0→1) for the hero rise + deck compression — tracks the FULL progress so the spring
         * drives the visible motion across its whole curve (fluid, and stiffness actually reads). The detail
         * still starts fading only at [HeroPhase]; its opaque background (which ramps in fast) covers the
         * still-finishing deck, so the sequential "card then detail" read holds without clipping the motion.
         */
        fun heroProgress(progress: Float): Float = progress.coerceIn(0f, 1f)

        /** Sub-progress (0→1) for the detail (background + content) fade — starts at [HeroPhase]. */
        fun detailProgress(progress: Float): Float =
            ((progress - HeroPhase) / (1f - HeroPhase)).coerceIn(0f, 1f)
    }
}

/** Provided by the app-level host; read by the source screen (to request/scrub) and the overlay host. */
val LocalCardExpansion = staticCompositionLocalOf<CardExpansionController?> { null }
