package com.getcode.view.main.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.getcode.model.KinAmount
import com.getcode.model.PaymentRequest
import com.getcode.network.repository.Request
import com.getcode.utils.FormatUtils
import com.getcode.view.components.CustomSwipeToDismiss
import com.getcode.view.main.bill.Bill
import timber.log.Timber

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeBill(
    modifier: Modifier = Modifier,
    dismissState: DismissState,
    billAmount: KinAmount?,
    paymentRequest: Request? = null,
    payloadData: List<Byte> = listOf()
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        CustomSwipeToDismiss(
            state = dismissState,
            dismissContent = {
                Bill(
                    amount = FormatUtils.formatWholeRoundDown(
                        billAmount?.kin?.toKin()?.toDouble() ?: 0.0
                    ),
                    payloadData = payloadData,
                    paymentRequest = paymentRequest
                )
            },
            directions = setOf(DismissDirection.EndToStart, DismissDirection.StartToEnd),
        )
    }
}