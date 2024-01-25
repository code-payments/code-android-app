package com.getcode.view.main.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.getcode.model.KinAmount
import com.getcode.model.PaymentRequest
import com.getcode.models.Bill
import com.getcode.network.repository.Request
import com.getcode.util.AnimationUtils
import com.getcode.utils.FormatUtils
import com.getcode.view.components.CustomSwipeToDismiss
import com.getcode.view.main.bill.Bill
import com.getcode.view.main.home.HomeUiModel
import timber.log.Timber

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeBill(
    modifier: Modifier = Modifier,
    dismissState: DismissState,
    dismissed: Boolean,
    transitionSpec: AnimatedContentTransitionScope<Bill?>.() -> ContentTransform,
    bill: Bill?,
) {
    AnimatedContent(
        modifier = modifier,
        targetState = bill,
        label = "animate bill",
        transitionSpec = transitionSpec
    ) { b ->
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            CustomSwipeToDismiss(
                modifier = Modifier.align(Alignment.Center),
                state = dismissState,
                dismissContent = {
                    if (b != null && !dismissed) {
                        Bill(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            bill = b
                        )
                    }
                },
                directions = setOf(DismissDirection.EndToStart, DismissDirection.StartToEnd),
            )
        }
    }
}