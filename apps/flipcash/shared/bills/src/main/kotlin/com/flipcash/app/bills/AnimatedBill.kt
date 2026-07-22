package com.flipcash.app.bills

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
import com.flipcash.app.core.bill.Scannable
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CustomSwipeToDismiss

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AnimatedBill(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = CodeTheme.dimens.inset,
        vertical = CodeTheme.dimens.grid.x2
    ),
    dismissState: DismissState,
    dismissed: Boolean,
    transitionSpec: AnimatedContentTransitionScope<Scannable.Payable?>.() -> ContentTransform,
    contentKey: (Scannable.Payable?) -> Any? = { it },
    bill: Scannable.Payable?,
) {
    AnimatedContent(
        modifier = modifier,
        targetState = bill,
        label = "animate bill",
        transitionSpec = transitionSpec,
        contentKey = contentKey,
    ) { b ->
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            CustomSwipeToDismiss(
                modifier = Modifier.align(Alignment.Center),
                state = dismissState,
                dismissContent = {
                    if (b != null && !dismissed) {
                        RenderedBill(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(contentPadding),
                            bill = b
                        )
                    }
                },
                directions = if (b?.disableGestures ?: false) {
                    emptySet()
                } else {
                    setOf(DismissDirection.EndToStart, DismissDirection.StartToEnd)
                },
            )
        }
    }
}

