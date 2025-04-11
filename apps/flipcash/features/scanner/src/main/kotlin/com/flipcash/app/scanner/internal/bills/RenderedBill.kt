package com.flipcash.app.scanner.internal.bills

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.app.core.bill.Bill
import com.getcode.opencode.model.core.CurrencyCode
import com.getcode.opencode.model.core.Fiat
import com.getcode.opencode.model.core.LocalFiat
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.core.Rate
import com.getcode.theme.DesignSystem

@Composable
internal fun RenderedBill(
    modifier: Modifier = Modifier,
    bill: Bill,
) {
    when (bill) {
        is Bill.Cash -> CashBill(
            modifier = modifier,
            payloadData = bill.data,
            amount = bill.amount
        )

        is Bill.Payment -> Receipt(
            modifier = modifier,
            data = bill.data,
            currencyCode = bill.payload.fiat?.currencyCode,
            amount = bill.amount
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
            PayloadKind.Cash,
            value = usdcBase,
            nonce = listOf(
                -85, -37, -27, -38, 37, -1, -4, -128, 102, 123, -35
            ).map { it.toByte() }
        )

        CashBill(
            amount = LocalFiat(
                usdc = usdcBase,
                converted = usdcBase.convertingTo(cadRate),
                rate = cadRate
            ),
            payloadData = payload.codeData.toList(),
        )
    }
}

@Preview
@Composable
fun Preview_PaymentBill() {
    DesignSystem {
        // $3 USD
        val usdcBase = Fiat(3.00, CurrencyCode.USD)
        val cadRate = Rate(1.4, CurrencyCode.CAD)

        val payload = OpenCodePayload(
            PayloadKind.RequestPaymentV2,
            value = usdcBase,
            nonce = listOf(
                -85, -37, -27, -38, 37, -1, -4, -128, 102, 123, -35
            ).map { it.toByte() }
        )


        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Receipt(
                amount = LocalFiat(
                    usdc = usdcBase,
                    converted = usdcBase.convertingTo(cadRate),
                    rate = cadRate
                ),
                data = payload.codeData.toList(),
                currencyCode = payload.fiat?.currencyCode
            )
        }
    }
}