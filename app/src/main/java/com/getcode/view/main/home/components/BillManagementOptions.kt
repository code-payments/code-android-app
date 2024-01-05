package com.getcode.view.main.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.getcode.theme.Gray50
import com.getcode.theme.White
import com.getcode.util.rememberedClickable
import com.getcode.view.components.CodeCircularProgressIndicator

@Composable
internal fun BillManagementOptions(
    modifier: Modifier = Modifier,
    showSend: Boolean = true,
    isSending: Boolean = false,
    sendEnabled: Boolean = true,
    showCancel: Boolean = true,
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
                .align(Alignment.BottomCenter)
        ) {
            if (showSend) {
                Row(
                    modifier = Modifier
                        .background(Gray50, RoundedCornerShape(30.dp))
                        .clip(RoundedCornerShape(30.dp))
                        .rememberedClickable(enabled = !isSending) {
                            onSend()
                        }
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

                Spacer(modifier = Modifier.width(16.dp))
            }
            if (showCancel) {
                Row(
                    modifier = Modifier
                        .background(Gray50, RoundedCornerShape(30.dp))
                        .clip(RoundedCornerShape(30.dp))
                        .rememberedClickable {
                            onCancel()
                        }
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