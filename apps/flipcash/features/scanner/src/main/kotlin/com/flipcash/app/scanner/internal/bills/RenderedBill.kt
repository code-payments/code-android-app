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
        val payload = OpenCodePayload(
            PayloadKind.Cash,
            value = Fiat(currencyCode = CurrencyCode.USD, quarks = 300u),
            nonce = listOf(
                -85, -37, -27, -38, 37, -1, -4, -128, 102, 123, -35
            ).map { it.toByte() }
        )

        CashBill(
            amount = LocalFiat(
                fiat = Fiat(currencyCode = CurrencyCode.USD, quarks = 300u),
                rate = Rate(1.0, CurrencyCode.USD)
            ),
            payloadData = payload.codeData.toList(),
        )
    }
}

@Preview
@Composable
fun Preview_PaymentBill() {
    DesignSystem {
        val payload = OpenCodePayload(
            PayloadKind.RequestPaymentV2,
            value = Fiat(currencyCode = CurrencyCode.USD, quarks = 300u),
            nonce = listOf(
                -85, -37, -27, -38, 37, -1, -4, -128, 102, 123, -35
            ).map { it.toByte() }
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Receipt(
                amount = LocalFiat(
                    fiat = Fiat(currencyCode = CurrencyCode.USD, quarks = 300u),
                    rate = Rate(1.0, CurrencyCode.USD)
                ),
                data = payload.codeData.toList(),
                currencyCode = payload.fiat?.currencyCode
            )
        }
    }
}