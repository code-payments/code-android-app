package com.flipcash.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.core.R
import com.getcode.theme.BrandAction
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import kotlin.time.Clock

@Composable
fun NotificationPreview(
    modifier: Modifier = Modifier,
    title: String,
    body: String,
    timestamp: String = "now",
) {
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .wrapContentWidth(unbounded = true)
            .background(
                color = Color(0xFF2C2C2C),
                shape = RoundedCornerShape(CodeTheme.dimens.grid.x2),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            modifier = Modifier
                .fillMaxHeight()
                .requiredSize(32.dp)
                .aspectRatio(1f),
            painter = painterResource(R.drawable.ic_notification_preview_icon),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = " · ",
                    fontSize = 12.sp,
                    color = CodeTheme.colors.textSecondary,
                    fontFamily = FontFamily.Default,
                )
                Text(
                    text = timestamp,
                    fontSize = 11.sp,
                    color = CodeTheme.colors.textSecondary,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                )
            }
            Text(
                text = body,
                fontSize = 11.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                color = CodeTheme.colors.textMain,
            )
        }
    }
}

@Composable
@Preview
fun Preview_Notification() {
    FlipcashPreview {
        NotificationPreview(
            title = stringResource(R.string.notification_preview_title),
            body = stringResource(R.string.notification_preview_body),
        )
    }
}