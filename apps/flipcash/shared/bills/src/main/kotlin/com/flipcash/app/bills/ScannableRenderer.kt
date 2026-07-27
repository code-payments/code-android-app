package com.flipcash.app.bills

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import com.flipcash.app.bills.components.bills.CashBill
import com.flipcash.app.bills.components.bills.GoldBar
import com.flipcash.app.bills.components.cards.TipCard
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.theme.CodeTheme

@Composable
fun ScannableRenderer(
    modifier: Modifier = Modifier,
    scannable: Scannable,
    // Vertical placement of the card within its area — used to pin a tip card just above its
    // bottom modal; ignored by bill types that always center.
    contentAlignment: Alignment = Alignment.Center,
    // Explicit tip-card width. Null lets the card size itself off the available canvas (the
    // scanner/camera); the "My Tip Card" screen pins a fixed, device-independent width. Ignored by
    // non-tip-card scannables.
    tipCardWidth: Dp? = null,
) {
    when (scannable) {
        is Scannable.CashBill -> CashBill(
            modifier = modifier,
            payloadData = scannable.data,
            amount = scannable.amount,
            token = scannable.token
        )
        is Scannable.GoldBar -> GoldBar(
            modifier = modifier.padding(horizontal = CodeTheme.dimens.inset),
            payloadData = scannable.data,
            amount = scannable.amount.underlyingTokenAmount,
        )
        is Scannable.TipCard -> {
            TipCard(
                modifier = modifier,
                payloadData = scannable.data,
                user = scannable.user,
                cardWidth = tipCardWidth,
                contentAlignment = contentAlignment,
            )
        }
    }
}

private val PREVIEW_CODE_DATA = listOf(
    0xA5, 0x3C, 0xD7, 0x8B, 0x14, 0xE9, 0x62, 0xF0,
    0x4D, 0xB6, 0x29, 0x7A, 0xC3, 0x58, 0x91, 0xDE,
    0x6F, 0x03, 0xB4, 0x87, 0x2C, 0xE5, 0x50, 0xA9,
    0x1E, 0x73, 0xC6, 0x3F, 0x98, 0x41, 0xDA, 0x65,
    0x0B, 0xF2, 0x7D, 0xAE, 0x53, 0xC0, 0x19,
).map { it.toByte() }

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
fun Preview_CashBill() {
    // $3 USD
    val usdcBase = Fiat(3.00, CurrencyCode.USD)
    val cadRate = Rate(1.4, CurrencyCode.CAD)
    CashBill(
        amount = LocalFiat(
            usdf = usdcBase,
            nativeAmount = usdcBase.convertingTo(cadRate),
        ),
        mint = "5AMAA9JV9H97YYVxx8F6FsCMmTwXSuTTQneiup4RYAUQ",
        payloadData = PREVIEW_CODE_DATA,
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
fun Preview_TipCard() {
    TipCard(
        payloadData = PREVIEW_CODE_DATA,
        user = UserProfile.Empty.copy(
            displayName = "Flipcash User",
        )
    )
}