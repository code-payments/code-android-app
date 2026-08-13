package com.flipcash.app.bills

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.DismissState
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.bills.decor.ScannableDecorator
import com.flipcash.app.bills.decor.ScannableDecoratorContext
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.navigation.asNavBarTab
import com.flipcash.app.core.tipping.LocalTipCoordinator
import com.flipcash.app.session.BillDeterminationResult
import com.flipcash.app.session.Grabbed
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.session.PutInWallet
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.scrim.LocalScrimController
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.AnimationUtils
import com.getcode.ui.utils.ModalAnimationSpeed
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Root-level overlay that renders the active bill (payable / tip card / gold bar) above ALL app
 * content, driven by the app-scoped [com.flipcash.app.session.BillOperations] state exposed through
 * [LocalSessionController]. Hosting it at the app root — rather than inside the scanner — lets a
 * presented bill appear over any screen, and decouples bill presentation from the camera.
 *
 * Owns the swipe-to-dismiss gesture wiring (the [DismissState], the "settle off-screen then remove"
 * latch), the enter/exit animation, and the below-bill decorators. The scanner keeps only its camera
 * and HUD; it no longer draws the bill.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BillOverlay(modifier: Modifier = Modifier) {
    val session = LocalSessionController.current ?: return
    val state by session.state.collectAsStateWithLifecycle()
    val billState by session.billState.collectAsStateWithLifecycle()

    // Tip affordability (min-tip balance check), surfaced through the shared selection state.
    val tipSelection by LocalTipCoordinator.current.selection.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().then(modifier)) {
        val updatedState by rememberUpdatedState(state)
        val updatedBillState by rememberUpdatedState(billState)

        // Scrim behind the bill when it's presented over app content — dims and blocks the content
        // beneath (and tap-to-dismisses) so the bill reads as a focused modal. Driven through the
        // shared root [ScrimController] (its ScrimOverlay sits just below this overlay), so it uses
        // the theme scrim colour. Skipped over the scanner tab, where the live camera stays visible.
        val scrimController = LocalScrimController.current
        // Resolve the tab UNDERNEATH any presented sheet, not the sheet route itself: a sheet opened
        // over the bill (the tip token / amount pickers) pushes a route whose tab is null, which
        // would flip this decision and fade the bill's scrim out/in around the sheet. Keying off the
        // base tab keeps the scrim decision — and thus the scrim — stable across the sheet.
        val baseRoute = LocalCodeNavigator.current.backStack
            .lastOrNull { it !is AppRoute.Main.Sheet } as? AppRoute
        val overCamera = baseRoute?.asNavBarTab() == NavBarButton.Scanner
        val showScrim = billState.bill != null && !overCamera
        LaunchedEffect(showScrim) {
            if (showScrim) {
                scrimController.show(onDismiss = { session.dismissBill(PutInWallet) })
            } else {
                scrimController.hide()
            }
        }

        // Not keyed on the bill: stays true while the swiped-off card is being removed so the outgoing
        // content stays hidden through its exit instead of snapping back to center. Reset when a fresh
        // bill appears.
        var dismissed by remember { mutableStateOf(false) }
        LaunchedEffect(updatedBillState.bill) {
            if (updatedBillState.bill != null) dismissed = false
        }

        // Bill dismiss state, restarted for every bill. Only gate whether the swipe is allowed —
        // removing the bill mid-swipe would recreate this and snap the card back to center before the
        // exit slide; removal happens after the swipe settles the card off-screen (below).
        val billDismissState = remember(updatedBillState.bill) {
            DismissState(
                initialValue = DismissValue.Default,
                confirmStateChange = {
                    it == DismissValue.DismissedToEnd && updatedBillState.canSwipeToDismiss
                }
            )
        }
        LaunchedEffect(billDismissState) {
            snapshotFlow { billDismissState.currentValue }
                .collect { value ->
                    if (value != DismissValue.Default) {
                        dismissed = true
                        session.dismissBill(PutInWallet)
                    }
                }
        }
        LaunchedEffect(dismissed) {
            if (dismissed) {
                delay(500.milliseconds)
                dismissed = false
            }
        }

        var managementHeight by remember { mutableStateOf(0.dp) }
        val showManagementOptions by remember(updatedBillState) {
            derivedStateOf {
                // The tip card always shows, but its modal only slides up when the viewer can afford
                // the minimum tip; otherwise the tip decorator prompts to add money.
                billDismissState.targetValue == DismissValue.Default &&
                    (updatedBillState.valuation != null ||
                        (updatedBillState.bill is Scannable.TipCard && tipSelection.canTip))
            }
        }

        // When the tip modal is up, pin the tip card just above it: reserve the modal's height as
        // bottom inset AND bottom-align the card (bias 0 = centered, 1 = bottom). Both animated so the
        // card slides from centered down to just above the modal, and back, in lockstep with the modal.
        val tipModalUp = managementHeight > 0.dp && updatedBillState.bill is Scannable.TipCard
        val modalSpeed = ModalAnimationSpeed.Normal(updatedBillState.confirmationDelayMillis)
        val offset = if (updatedBillState.bill is Scannable.TipCard) CodeTheme.dimens.grid.x8 else CodeTheme.dimens.grid.x2
        val billBottomInset by animateDpAsState(
            targetValue = managementHeight + offset,
            animationSpec = tween(durationMillis = modalSpeed.duration, delayMillis = modalSpeed.delay),
            label = "billBottomInset",
        )
        val billVerticalBias by animateFloatAsState(
            targetValue = if (tipModalUp) 1f else 0f,
            animationSpec = tween(durationMillis = modalSpeed.duration, delayMillis = modalSpeed.delay),
            label = "billVerticalBias",
        )

        AnimatedScannable(
            modifier = Modifier.fillMaxSize(),
            dismissState = billDismissState,
            dismissed = dismissed,
            contentPadding = PaddingValues(
                start = CodeTheme.dimens.inset,
                end = CodeTheme.dimens.inset,
                top = CodeTheme.dimens.grid.x2,
                bottom = billBottomInset,
            ),
            scannableAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = billVerticalBias),
            bill = updatedBillState.bill,
            transitionSpec = {
                when (updatedState.billResult) {
                    BillDeterminationResult.None -> EnterTransition.None
                    Grabbed -> AnimationUtils.animationBillEnterGrabbed
                    PutInWallet -> AnimationUtils.animationBillEnterGive
                } togetherWith when (updatedState.billResult) {
                    BillDeterminationResult.None -> ExitTransition.None
                    Grabbed -> AnimationUtils.animationBillExitGrabbed
                    PutInWallet -> AnimationUtils.animationBillExitReturned
                }
            }
        )

        // Below-bill content, owned by the scannable type. `displayedScannable` retains the last shown
        // scannable so an overlay can still animate OUT as `bill` returns to null on dismiss.
        var displayedScannable by remember { mutableStateOf<Scannable?>(null) }
        LaunchedEffect(updatedBillState.bill) {
            updatedBillState.bill?.let { displayedScannable = it }
        }
        displayedScannable?.let { ScannableDecorator.forScannable(it) }?.let { overlays ->
            val overlayContext = ScannableDecoratorContext(
                liveBill = updatedBillState.bill,
                billState = updatedBillState,
                isRemoteSendLoading = updatedState.isRemoteSendLoading,
                showManagementOptions = showManagementOptions,
                onManagementHeightMeasured = { managementHeight = it },
                onDismiss = { session.dismissBill(PutInWallet) },
            )
            with(overlays) { Content(overlayContext) }
        }
    }
}
