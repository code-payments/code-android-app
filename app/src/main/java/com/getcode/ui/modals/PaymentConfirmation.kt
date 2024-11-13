package com.getcode.ui.modals

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.services.model.CodePayload
import com.getcode.model.CurrencyCode
import com.getcode.model.Fiat
import com.getcode.model.Kin.Companion.fromFiat
import com.getcode.model.KinAmount
import com.getcode.services.model.Kind
import com.getcode.model.Rate
import com.getcode.model.fromFiatAmount
import com.getcode.models.PaymentConfirmation
import com.getcode.models.ConfirmationState
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import com.getcode.theme.bolded
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.components.Modal
import com.getcode.ui.components.PriceWithFlag
import com.getcode.ui.components.SlideToConfirm
import com.getcode.ui.components.SlideToConfirmDefaults
import kotlinx.coroutines.delay

@Composable
internal fun PaymentConfirmation(
    modifier: Modifier = Modifier,
    balance: KinAmount?,
    confirmation: PaymentConfirmation?,
    onAddKin: () -> Unit = { },
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    val state by remember(confirmation?.state) {
        derivedStateOf { confirmation?.state }
    }

    val isSending by remember(state) {
        derivedStateOf { state is ConfirmationState.Sending }
    }

    val requestedAmount by remember(confirmation?.localAmount?.kin?.quarks) {
        derivedStateOf { confirmation?.localAmount }
    }

    Modal(modifier) {
        val amount = requestedAmount
        if (state != null && amount != null && balance != null) {
            val balanceAmount = remember {
                balance.kin
            }

            if (balanceAmount >= amount.kin) {
                PaymentConfirmationContent(
                    amount = amount,
                    isSending = isSending,
                    state = state,
                    onApproved = onSend
                )
            } else {
                InsufficientFundsModalContent(onAddKin)
            }
            val enabled = !isSending && state !is ConfirmationState.Sent
            val alpha by animateFloatAsState(targetValue = if (enabled) 1f else 0f, label = "alpha")
            CodeButton(
                modifier = Modifier.fillMaxWidth().alpha(alpha),
                enabled = enabled,
                buttonState = ButtonState.Subtle,
                onClick = onCancel,
                text = stringResource(id = android.R.string.cancel),
            )
        }
    }
}

private val usd_fx = 0.00001585
private val USD_Rate = Rate(usd_fx, CurrencyCode.USD)

private val payload = CodePayload(
    Kind.RequestPayment,
    value = Fiat(CurrencyCode.USD, 0.25),
    nonce = listOf(
        -85, -37, -27, -38, 37, -1, -4, -128, 102, 123, -35
    ).map { it.toByte() }
)

private fun confirmationWithState(state: ConfirmationState) = PaymentConfirmation(
    state = state,
    payload = payload,
    requestedAmount = KinAmount.fromFiatAmount(
        fiat = 0.25,
        fx = usd_fx,
        CurrencyCode.USD
    ),
    localAmount = KinAmount.fromFiatAmount(
        fiat = 0.25,
        fx = usd_fx,
        CurrencyCode.USD
    ),
)

@Preview(showBackground = true)
@Composable
fun Preview_PaymentConfirmModal_Awaiting() {
    DesignSystem {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            PaymentConfirmation(
                modifier = Modifier.align(Alignment.BottomCenter),
                confirmation = confirmationWithState(ConfirmationState.AwaitingConfirmation),
                balance = KinAmount.newInstance(fromFiat(1_000.0, usd_fx), USD_Rate),
                onSend = { }
            ) {

            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun Preview_PaymentConfirmModal_Sending() {
    DesignSystem {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            PaymentConfirmation(
                modifier = Modifier.align(Alignment.BottomCenter),
                confirmation = confirmationWithState(ConfirmationState.Sending),
                balance = KinAmount.newInstance(fromFiat(1_000.0, usd_fx), USD_Rate),
                onSend = { }
            ) {

            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun Preview_PaymentConfirmModal_Sent() {
    DesignSystem {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            PaymentConfirmation(
                modifier = Modifier.align(Alignment.BottomCenter),
                confirmation = confirmationWithState(ConfirmationState.Sent),
                balance = KinAmount.newInstance(fromFiat(1_000.0, usd_fx), USD_Rate),
                onSend = { }
            ) {

            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun Preview_PaymentConfirmModal_Interactive() {
    DesignSystem {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            var confirmation by remember {
                mutableStateOf<PaymentConfirmation?>(
                    PaymentConfirmation(
                        state = ConfirmationState.AwaitingConfirmation,
                        payload = payload,
                        requestedAmount = KinAmount.fromFiatAmount(
                            fiat = 0.25,
                            fx = usd_fx,
                            CurrencyCode.USD
                        ),
                        localAmount = KinAmount.fromFiatAmount(
                            fiat = 0.25,
                            fx = usd_fx,
                            CurrencyCode.USD
                        ),
                    )
                )
            }

            AnimatedContent(
                modifier = Modifier.align(Alignment.BottomCenter),
                targetState = confirmation?.payload,
                transitionSpec = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(durationMillis = 600, delayMillis = 450)
                    ) togetherWith slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down)
                },
                label = "payment confirmation",
            ) {
                // uses static payload for animation criteria; renders off state
                if (it != null) {
                    Box(
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        PaymentConfirmation(
                            confirmation = confirmation,
                            balance = KinAmount.newInstance(fromFiat(1_000.0, usd_fx), USD_Rate),
                            onSend = {
                                confirmation = confirmation?.copy(state = ConfirmationState.Sending)
                            },
                            onCancel = { confirmation = null }
                        )
                    }
                }
            }

            LaunchedEffect(confirmation?.state) {
                val state = confirmation?.state
                if (state is ConfirmationState.Sending) {
                    delay(1_500)
                    confirmation = confirmation?.copy(state = ConfirmationState.Sent)
                } else if (state is ConfirmationState.Sent) {
                    delay(500)
                    confirmation = null
                }
            }
        }
    }
}

@Composable
private fun PaymentConfirmationContent(
    amount: KinAmount,
    isSending: Boolean,
    state: ConfirmationState?,
    onApproved: () -> Unit
) {
    PriceWithFlag(
        currencyCode = amount.rate.currency,
        amount = amount,
        iconSize = 24.dp
    ) {
        Text(
            text = it,
            color = Color.White,
            style = CodeTheme.typography.displayMedium.bolded()
        )
    }
    SlideToConfirm(
        isLoading = isSending,
        trackColor = SlideToConfirmDefaults.BlueTrackColor,
        isSuccess = state is ConfirmationState.Sent,
        onConfirm = { onApproved() },
    )
}

@Composable
private fun InsufficientFundsModalContent(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.title_insufficientFunds),
        color = Color.White,
        style = CodeTheme.typography.displaySmall
    )
    Text(
        text = stringResource(R.string.subtitle_insufficientFundsDescription),
        color = Color.White,
        style = CodeTheme.typography.textSmall
    )
    CodeButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        text = stringResource(R.string.title_getMoreKin),
        buttonState = ButtonState.Filled
    )
}