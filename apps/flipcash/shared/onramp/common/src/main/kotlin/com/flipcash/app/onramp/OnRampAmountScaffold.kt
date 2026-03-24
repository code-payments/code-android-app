package com.flipcash.app.onramp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun OnRampAmountScaffold(content: @Composable () -> Unit) {
//    val onramp = LocalOnRampAmountController.current
//
//    val state by onramp.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        content()

//        val scrimDetails by rememberScrim(state)
//
//        val scrimAlpha by animateFloatAsState(
//            if (scrimDetails.show) 1f else 0f,
//            label = "scrim visibility"
//        )
//
//        if (scrimDetails.show) {
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .alpha(scrimAlpha)
//                    .background(CodeTheme.colors.scrim)
//                    .rememberedClickable(
//                        indication = null,
//                        interactionSource = remember { MutableInteractionSource() }
//                    ) {
//                        if (scrimDetails.cancellable) {
//                            onramp.cancel(fromUser = true)
//                        }
//                    }
//            )
//        }
//
//        AnimatedContent(
//            modifier = Modifier.align(BottomCenter),
//            targetState = state.provider,
//            transitionSpec = AnimationUtils.modalAnimationSpec(speed = ModalAnimationSpeed.Fast()),
//            label = "onramp amount selection",
//        ) { provider ->
//            if (provider != null) {
//                Box(
//                    modifier = Modifier.fillMaxWidth(),
//                    contentAlignment = BottomCenter
//                ) {
//                    OnRampAmountSelectionModal(
//                        provider = provider,
//                        selectedAmount = state.selectedAmount,
//                        onAmountSelected = {
//                            onramp.selectAmount(it?.let { OnRampAmount.Predefined(it) })
//                        },
//                        onCustomSelected = { onramp.selectAmount(OnRampAmount.Custom) },
//                        onConfirm = { onramp.confirmSelection() }
//                    )
//                }
//            } else {
//                Spacer(Modifier.fillMaxWidth())
//            }
//        }
    }
}

data class ScrimDetails(val show: Boolean, val cancellable: Boolean)

@Composable
private fun rememberScrim(state: com.flipcash.app.onramp.State): State<ScrimDetails> {
    return remember(state) {
        derivedStateOf {
            val provider = state.provider ?: return@derivedStateOf ScrimDetails(
                show = false,
                cancellable = false
            )
            ScrimDetails(show = true, cancellable = true)
        }
    }
}