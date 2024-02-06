package com.getcode.view.main.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.Gray50
import com.getcode.theme.White
import com.getcode.ui.utils.rememberedClickable
import com.getcode.ui.components.CodeCircularProgressIndicator

@Composable
internal fun BillManagementOptions(
    modifier: Modifier = Modifier,
    showSend: Boolean = true,
    isSending: Boolean = false,
    showCancel: Boolean = true,
    canCancel: Boolean = true,
    onSend: () -> Unit,
    onCancel: () -> Unit,
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
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset)
        ) {
            if (showSend) {
                Row(
                    modifier = Modifier
                        .background(Gray50, CircleShape)
                        .clip(CircleShape)
                        .rememberedClickable(enabled = !isSending) { onSend() }
                        .padding(vertical = 15.dp, horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Row(
                            modifier = Modifier.alpha(if (!isSending) 1f else 0f)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_remote_send),
                                contentDescription = "",
                                modifier = Modifier.width(22.dp)
                            )
                            Text(
                                modifier = Modifier.padding(start = 10.dp),
                                text = stringResource(R.string.action_send)
                            )
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
            if (showCancel) {
                Row(
                    modifier = Modifier
                        .background(Gray50, CircleShape)
                        .clip(CircleShape)
                        .rememberedClickable(enabled = canCancel) { onCancel() }
                        .padding(vertical = 15.dp, horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_bill_close),
                        contentDescription = "",
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        modifier = Modifier.padding(start = 10.dp),
                        text = stringResource(R.string.action_cancel)
                    )
                }
            }
        }
    }
}