package com.flipcash.app.bills

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
import com.getcode.theme.DesignSystem

@Composable
fun RenderedBill(
    modifier: Modifier = Modifier,
    bill: Bill,
) {
    when (bill) {
        is Bill.Cash -> CashBill(
            modifier = modifier,
            payloadData = bill.data,
            amount = bill.amount,
            token = bill.token
        )
    }
}

@Preview
@Composable
fun Preview_CashBill() {
    DesignSystem {
        // $3 USD
        val usdcBase = Fiat(3.00, CurrencyCode.USD)
        val cadRate = Rate(1.4, CurrencyCode.CAD)
        val payload = OpenCodePayload(
            PayloadKind.MultiMintCash,
            value = usdcBase,
            nonce = listOf(
                -85, -37, -27, -38, 37, -1, -4, -128, 102, 123, -35
            ).map { it.toByte() }
        )

        CashBill(
            amount = LocalFiat(
                usdf = usdcBase,
                nativeAmount = usdcBase.convertingTo(cadRate),
            ),
            token = Token.usdf,
            payloadData = payload.codeData.toList(),
        )
    }
}