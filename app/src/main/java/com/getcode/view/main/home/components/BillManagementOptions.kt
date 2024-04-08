package com.getcode.view.main.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import com.getcode.models.ShareAction
import com.getcode.theme.CodeTheme
import com.getcode.theme.Gray50
import com.getcode.theme.White
import com.getcode.ui.utils.rememberedClickable
import com.getcode.ui.components.CodeCircularProgressIndicator
import com.getcode.ui.components.Pill

@Composable
internal fun BillManagementOptions(
    modifier: Modifier = Modifier,
    shareAction: ShareAction? = null,
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
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x8)
        ) {
            if (shareAction != null) {
                Pill(
                    modifier = Modifier
                        .rememberedClickable(enabled = !isSending) { onSend() }
                        .padding(vertical = 15.dp, horizontal = 20.dp),
                    contentPadding = PaddingValues(),
                    backgroundColor = Gray50,
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
                                text = stringResource(shareAction.label)
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
                Pill(
                    modifier = Modifier
                        .rememberedClickable(enabled = canCancel) { onCancel() }
                        .padding(vertical = 15.dp, horizontal = 20.dp),
                    contentPadding = PaddingValues(),
                    backgroundColor = Gray50,
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