package com.flipcash.app.bills

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.app.core.bill.Bill
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem

@Composable
fun RenderedBill(
    modifier: Modifier = Modifier,
    bill: Bill,
) {
    if (bill.token.address == Mint.usdf) {
        GoldBar(
            modifier = modifier
                .padding(horizontal = CodeTheme.dimens.inset),
            payloadData = bill.data,
            amount = bill.amount.underlyingTokenAmount,
        )
    } else {
        CashBill(
            modifier = modifier,
            payloadData = bill.data,
            amount = bill.amount,
            token = bill.token
        )
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
@Composable
fun Preview_CashBill() {
    DesignSystem {
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
}