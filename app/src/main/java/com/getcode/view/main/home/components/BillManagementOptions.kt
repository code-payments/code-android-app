package com.getcode.view.main.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.getcode.models.BillState
import com.getcode.theme.CodeTheme
import com.getcode.theme.Gray50
import com.getcode.theme.White
import com.getcode.ui.components.CodeCircularProgressIndicator
import com.getcode.ui.components.Pill
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.rememberedClickable

@Composable
internal fun BillManagementOptions(
    modifier: Modifier = Modifier,
    isSending: Boolean = false,
    isInteractable: Boolean = true,
    primaryAction: BillState.Action? = null,
    secondaryAction: BillState.Action? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
    ) {
        Row(
            modifier = Modifier
                .padding(bottom = 30.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x8)
        ) {
            if (primaryAction != null) {
                Pill(
                    modifier = Modifier
                        .rememberedClickable(enabled = !isSending) { primaryAction.action() },
                    contentPadding = PaddingValues(15.dp),
                    backgroundColor = CodeTheme.colors.action,
                ) {
                    Box {
                        Row(
                            modifier = Modifier.alpha(if (!isSending) 1f else 0f)
                        ) {
                            Image(
                                painter = primaryAction.asset,
                                contentDescription = "",
                                modifier = Modifier.width(22.dp)
                            )
                            primaryAction.label?.let { label ->
                                Text(
                                    modifier = Modifier.padding(start = 10.dp),
                                    text = label
                                )
                            }
                        }

                        if (isSending) {
                            CodeCircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }
                }
            }
            if (secondaryAction != null) {
                Pill(
                    modifier = Modifier
                        .rememberedClickable(enabled = isInteractable) { secondaryAction.action() },
                    contentPadding = PaddingValues(15.dp),
                    backgroundColor = CodeTheme.colors.action,
                ) {
                    Image(
                        painter = secondaryAction.asset,
                        contentDescription = "",
                        modifier = Modifier.size(18.dp)
                    )
                    secondaryAction.label?.let { label ->
                        Text(
                            modifier = Modifier.padding(start = 10.dp),
                            text = label
                        )
                    }
                }
            }
        }
    }
}