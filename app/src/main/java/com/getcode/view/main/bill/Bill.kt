package com.getcode.view.main.bill

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.model.CodePayload
import com.getcode.model.CurrencyCode
import com.getcode.model.Fiat
import com.getcode.model.KinAmount
import com.getcode.model.Kind
import com.getcode.network.repository.Request
import com.getcode.theme.CodeTheme
import timber.log.Timber

@Composable
fun Bill(
    modifier: Modifier = Modifier,
    payloadData: List<Byte> = listOf(),
    paymentRequest: Request? = null,
    amount: String = ""
) {
    if (paymentRequest == null) {
        CashBill(modifier.padding(bottom = 70.dp), payloadData = payloadData, amount)
    } else {
        PaymentBill(modifier, request = paymentRequest)
    }
}

@Preview
@Composable
fun Preview_CashBill() {
    CodeTheme {
        CashBill(payloadData = emptyList(), amount = "15,760")
    }
}

@Preview
@Composable
fun Preview_PaymentBill() {
    CodeTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PaymentBill(
                request = Request(
                    amount = KinAmount.fromFiatAmount(
                        fiat = 0.25,
                        fx = 0.00001585,
                        CurrencyCode.USD
                    ),
                    payload = CodePayload(
                        Kind.RequestPayment,
                        value = Fiat(CurrencyCode.USD, 0.25),
                        nonce = listOf(
                            -85, -37, -27, -38, 37, -1, -4, -128, 102, 123, -35
                        ).map { it.toByte() }
                    )
                )
            )
        }
    }
}