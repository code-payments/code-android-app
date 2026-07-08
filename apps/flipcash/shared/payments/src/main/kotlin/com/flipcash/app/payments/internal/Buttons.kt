package com.flipcash.app.payments.internal

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.core.money.formatted
import com.flipcash.app.payments.PaymentAction
import com.flipcash.app.payments.PurchaseMethod
import com.flipcash.app.payments.PurchaseMethodMetadata
import com.flipcash.app.payments.PurchaseMethodState
import com.flipcash.shared.payments.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.util.resources.ResourceHelper

internal fun purchaseOptions(
    state: PurchaseMethodState,
    metadata: PurchaseMethodMetadata,
    resources: ResourceHelper,
    onClick: (PurchaseMethod) -> Unit
): List<BottomBarAction> {
    return buildList {
        if (state.coinbaseOnRampAvailable) {
            add(
                buildButtonAction(
                    prefix = null,
                    suffix = null,
                    iconRes = when (metadata.paymentAction) {
                        PaymentAction.Buy -> R.drawable.ic_buy_with_google_pay
                        PaymentAction.Pay -> R.drawable.ic_pay_with_google_pay
                        PaymentAction.Plain -> R.drawable.ic_google_pay
                    },
                    width = 150.sp,
                    height = 20.sp,
                    tintIcon = false,
                    onClick = { onClick(PurchaseMethod.CoinbaseOnRamp) }
                )
            )
        }
        if (state.hasReserves && metadata.showReserves) {
            val minimumAmountNeeded = metadata.purchaseAmount ?: Fiat.MIN_VALUE
            if (state.reservesBalance.nativeAmount >= minimumAmountNeeded) {
                add(
                    BottomBarAction(
                        text = resources.getString(
                            R.string.action_useCashReservesWithBalance,
                            state.reservesBalance.formatted()
                        ),
                        onClick = { onClick(PurchaseMethod.CashReserves(state.reservesBalance)) }
                    )
                )
            }
        }

        add(
            buildButtonAction(
                prefix = null,
                suffix = resources.getString(R.string.label_phantom),
                iconPadding = { PaddingValues() },
                iconRes = R.drawable.ic_phantom_wallet,
                onClick = { onClick(PurchaseMethod.PhantomWallet) }
            )
        )

        if (state.canUseOtherWallets) {
            add(
                BottomBarAction(
                    text = resources.getString(R.string.title_onrampProviderOtherWallet),
                    onClick = { onClick(PurchaseMethod.OtherWallet) }
                )
            )
        }

        add(
            BottomBarAction(
                text = resources.getString(R.string.action_dismiss),
                style = BottomBarManager.BottomBarButtonStyle.Text,
            )
        )
    }
}

private fun buildButtonAction(
    prefix: String?,
    suffix: String?,
    iconRes: Int,
    width: TextUnit = 25.sp,
    height: TextUnit = 14.sp,
    iconPadding: @Composable () -> PaddingValues = {
        PaddingValues(
            start = CodeTheme.dimens.grid.x1 + 2.dp,
            end = CodeTheme.dimens.grid.x1
        )
    },
    tintIcon: Boolean = true,
    onClick: () -> Unit
): BottomBarAction {
    return BottomBarAction(
        text = buildAnnotatedString {
            if (prefix != null) {
                append(prefix)
            }
            appendInlineContent("[icon]", alternateText = " ")
            if (suffix != null) {
                append(suffix)
            }
        },
        inlineContentMap = mapOf(
            "[icon]" to InlineTextContent(
                placeholder = Placeholder(
                    width = width,
                    height = height,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                ),
                children = {
                    val buttonColors = ButtonState.Filled.colors()
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            modifier = Modifier.padding(iconPadding()),
                            painter = painterResource(iconRes),
                            colorFilter = if (tintIcon) {
                                ColorFilter.tint(buttonColors.contentColor(true).value)
                            } else {
                                null
                            },
                            contentDescription = null
                        )
                    }
                }
            )
        ),
        onClick = onClick
    )
}