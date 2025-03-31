package com.getcode.view.main.bill

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.getcode.models.Bill
import com.getcode.ui.theme.CustomSwipeToDismiss

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeBill(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
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
                                .weight(1f)
                                .padding(contentPadding),
                            bill = b
                        )
                    }
                },
                directions = setOf(DismissDirection.EndToStart, DismissDirection.StartToEnd),
            )
        }
    }
}