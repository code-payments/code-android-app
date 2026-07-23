package com.flipcash.app.scanner.internal.bills.decor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.flipcash.app.core.bill.BillState
import com.flipcash.app.core.bill.Scannable

/**
 * Below-bill decor content owned by a [Scannable] type rather than decided by
 * `ScannableContainer` in a `when` branch. Each Scannable variant supplies its own decorators
 * — management options, confirmation modals, and (future) the tip card modal — via [Content].
 *
 * The container still owns the card animation/gesture wiring (the `DismissState`, the
 * `AnimatedContent` enter/exit, the retained-scannable latch). Decorators only decide what
 * renders below the card. Each decorator captures the concrete retained scannable for its
 * content (so it survives the exit animation) and gates its `visible` on
 * [ScannableDecoratorContext.liveBill].
 */
internal sealed interface ScannableDecorator {
    @Composable
    fun BoxScope.Content(context: ScannableDecoratorContext)

    /**
     * An [AnimatedVisibility] that every decorator gets for free — it also runs its **enter**
     * animation on first composition. A plain `visible = true` on an [AnimatedVisibility]'s first
     * frame skips the enter transition, so the first scannable overlay of a session (management
     * options, confirmation modals, tip modal, …) popped in instantly while later ones animated.
     * Driving visibility through a [MutableTransitionState] seeded to `false` makes the very first
     * appearance animate too. Use this instead of a bare [AnimatedVisibility] inside [Content].
     */
    @Composable
    fun BoxScope.AnimatedScannableDecorator(
        visible: Boolean,
        enter: EnterTransition,
        exit: ExitTransition,
        modifier: Modifier = Modifier,
        content: @Composable AnimatedVisibilityScope.() -> Unit,
    ) {
        val visibleState = remember { MutableTransitionState(false) }
        visibleState.targetState = visible
        AnimatedVisibility(
            visibleState = visibleState,
            modifier = modifier,
            enter = enter,
            exit = exit,
            content = content,
        )
    }

    companion object {
        /** Resolves the decor that own the below-bill content for [scannable]. */
        fun forScannable(scannable: Scannable): ScannableDecorator = when (scannable) {
            is Scannable.Payable -> PayableDecorator(scannable)
            is Scannable.TipCard -> TipCardDecorator(scannable)
        }
    }
}

/**
 * Container-owned state the decorator read. Rebuilt on each recomposition so decorators gate
 * their visibility on the live bill without reaching into container internals.
 *
 * @param liveBill the current bill (null once dismissed); used to gate `visible`.
 * @param billState the current bill state (actions, valuation, confirmation delay).
 * @param isRemoteSendLoading whether a remote send is in flight.
 * @param showManagementOptions whether the management options row should be shown.
 * @param onManagementHeightMeasured reports the measured management-row height back to the
 *   container so the card floats above it.
 * @param onDismiss dismisses the current bill (equivalent to `session.dismissBill(PutInWallet)`).
 */
internal data class ScannableDecoratorContext(
    val liveBill: Scannable?,
    val billState: BillState,
    val isRemoteSendLoading: Boolean,
    val showManagementOptions: Boolean,
    val onManagementHeightMeasured: (Dp) -> Unit,
    val onDismiss: () -> Unit,
)
