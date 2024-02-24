package com.getcode.view.main.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.ZeroCornerSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.model.CodePayload
import com.getcode.model.CurrencyCode
import com.getcode.model.Fiat
import com.getcode.model.Kin.Companion.fromFiat
import com.getcode.model.KinAmount
import com.getcode.model.Kind
import com.getcode.model.Rate
import com.getcode.models.PaymentConfirmation
import com.getcode.models.PaymentState
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
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
        derivedStateOf { state is PaymentState.Sending }
    }

    val requestedAmount by remember(confirmation?.localAmount?.kin?.quarks) {
        derivedStateOf { confirmation?.localAmount }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(
                CodeTheme.shapes.medium.copy(
                    bottomStart = ZeroCornerSize,
                    bottomEnd = ZeroCornerSize
                )
            )
            .background(Brand)
            .padding(horizontal = 20.dp, vertical = 30.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
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
            val enabled = !isSending && state !is PaymentState.Sent
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

private fun confirmationWithState(state: PaymentState) = PaymentConfirmation(
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
    CodeTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            PaymentConfirmation(
                modifier = Modifier.align(Alignment.BottomCenter),
                confirmation = confirmationWithState(PaymentState.AwaitingConfirmation),
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
    CodeTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            PaymentConfirmation(
                modifier = Modifier.align(Alignment.BottomCenter),
                confirmation = confirmationWithState(PaymentState.Sending),
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
    CodeTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            PaymentConfirmation(
                modifier = Modifier.align(Alignment.BottomCenter),
                confirmation = confirmationWithState(PaymentState.Sent),
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
    CodeTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            var confirmation by remember {
                mutableStateOf<PaymentConfirmation?>(
                    PaymentConfirmation(
                        state = PaymentState.AwaitingConfirmation,
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
                                confirmation = confirmation?.copy(state = PaymentState.Sending)
                            },
                            onCancel = { confirmation = null }
                        )
                    }
                }
            }

            LaunchedEffect(confirmation?.state) {
                val state = confirmation?.state
                if (state is PaymentState.Sending) {
                    delay(1_500)
                    confirmation = confirmation?.copy(state = PaymentState.Sent)
                } else if (state is PaymentState.Sent) {
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
    state: PaymentState?,
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
            style = CodeTheme.typography.h1
        )
    }
    SlideToConfirm(
        isLoading = isSending,
        trackColor = SlideToConfirmDefaults.BlueTrackColor,
        isSuccess = state is PaymentState.Sent,
        onConfirm = { onApproved() },
    )
}

@Composable
private fun InsufficientFundsModalContent(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.sdk_payments_insufficient_funds_title),
        color = Color.White,
        style = CodeTheme.typography.h3
    )
    Text(
        text = stringResource(R.string.sdk_payments_insufficient_funds_description),
        color = Color.White,
        style = CodeTheme.typography.body2
    )
    CodeButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        text = stringResource(R.string.button_get_more_kin),
        buttonState = ButtonState.Filled
    )
}