package com.getcode.view.main.bill

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.getcode.model.CodePayload
import com.getcode.model.CurrencyCode
import com.getcode.model.Fiat
import com.getcode.model.KinAmount
import com.getcode.model.Kind
import com.getcode.models.Bill
import com.getcode.theme.CodeTheme

@Composable
fun Bill(
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
            currencyCode = bill.payload.fiat?.currency,
            amount = bill.amount
        )

        is Bill.Login -> LoginBill(
            modifier = modifier,
            data = bill.data,
        )

        is Bill.Tip -> TipCard(
            modifier = modifier,
            username = bill.payload.username.orEmpty(),
            data = bill.data,
        )
    }
}

@Preview
@Composable
fun Preview_CashBill() {
    CodeTheme {
        val payload = CodePayload(
            Kind.Cash,
            value = Fiat(CurrencyCode.USD, 3.00),
            nonce = listOf(
                -85, -37, -27, -38, 37, -1, -4, -128, 102, 123, -35
            ).map { it.toByte() }
        )

        CashBill(
            amount = KinAmount.fromFiatAmount(
                fiat = 3.00,
                fx = 0.00001585,
                CurrencyCode.USD
            ),
            payloadData = payload.codeData.toList(),
        )
    }
}

@Preview
@Composable
fun Preview_PaymentBill() {
    CodeTheme {
        val payload = CodePayload(
            Kind.RequestPayment,
            value = Fiat(CurrencyCode.USD, 0.25),
            nonce = listOf(
                -85, -37, -27, -38, 37, -1, -4, -128, 102, 123, -35
            ).map { it.toByte() }
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Receipt(
                amount = KinAmount.fromFiatAmount(
                    fiat = 0.25,
                    fx = 0.00001585,
                    CurrencyCode.USD
                ),
                data = payload.codeData.toList(),
                currencyCode = payload.fiat?.currency
            )
        }
    }
}