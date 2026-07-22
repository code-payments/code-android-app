package com.flipcash.app.currencycreator.internal.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.bill.customization.Event
import com.flipcash.app.bill.customization.LocalBillPlaygroundController
import com.flipcash.app.bill.customization.PlaygroundContext
import com.flipcash.app.bill.customization.components.BillPlayground
import com.flipcash.app.bills.RenderedBill
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.ui.transitions.SharedTransition
import com.flipcash.app.core.ui.transitions.sharedBoundsTransition
import com.flipcash.app.currencycreator.internal.CurrencyCreatorViewModel
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.theme.CodeTheme

@Composable
internal fun BillCustomizationScreen() {
    val viewModel = flowSharedViewModel<CurrencyCreatorViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val controller = LocalBillPlaygroundController.current

    BillCustomizationContent(state, viewModel::dispatchEvent)

    // Seed the playground from the VM's state, but ONLY for genuinely external
    // changes — the arrival of a (restored) customization set and changes to the
    // purchase amount. We must NOT key this on `state.customizations` itself:
    // BillCustomizationContent feeds the playground's edits back into the VM via
    // CustomizationsChanged, so keying on `state.customizations` created an infinite
    // Load -> CustomizationsChanged -> Load feedback loop that repainted the bill on
    // every pass (the "spastic" color flicker). Keying on the null/non-null
    // transition seeds once when customizations first appear and then leaves the
    // playground as the source of truth.
    val hasCustomizations = state.customizations != null
    LaunchedEffect(hasCustomizations, state.purchaseAmount) {
        controller.dispatchEvent(
            Event.Load(
                customizations = state.customizations,
                amount = state.purchaseAmount,
                context = PlaygroundContext.CurrencyCreator,
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterialApi::class)
@Composable
internal fun BillCustomizationContent(
    state: CurrencyCreatorViewModel.State,
    dispatch: (CurrencyCreatorViewModel.Event) -> Unit
) {
    val controller = LocalBillPlaygroundController.current

    val playgroundState by controller.state.collectAsStateWithLifecycle()

    val augmentedBill by remember(
        playgroundState.bill,
        state.customizations
    ) {
        derivedStateOf {
            val bill = playgroundState.bill ?: return@derivedStateOf null
            bill.copy(
                token = bill.token.copy(
                    billCustomizations = state.customizations
                )
            )
        }
    }

    LaunchedEffect(playgroundState.customizations) {
        dispatch(CurrencyCreatorViewModel.Event.CustomizationsChanged(playgroundState.customizations))
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        augmentedBill?.let { bill ->
            RenderedBill(
                modifier = Modifier
                    .padding(top = CodeTheme.dimens.grid.x3)
                    .fillMaxWidth()
                    .weight(1f)
                    .sharedBoundsTransition(
                        transition = SharedTransition.CurrencyBill
                    ),
                bill = bill,
            )
        }

        BillPlayground(
            modifier = Modifier.fillMaxWidth(),
            state = playgroundState,
        ) { event ->
            controller.dispatchEvent(event)
        }
    }
}
