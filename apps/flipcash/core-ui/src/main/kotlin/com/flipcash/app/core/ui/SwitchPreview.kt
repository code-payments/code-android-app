package com.flipcash.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.core.R
import com.getcode.theme.CodeTheme

@Composable
fun SwitchPreview(modifier: Modifier = Modifier, checked: Boolean = false) {
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .background(
                color = Color(0xFF2C2C2C),
                shape = RoundedCornerShape(CodeTheme.dimens.grid.x2),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.Start),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.title_allowNotifications),
            fontSize = 14.sp,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium
        )

        Switch(
            modifier = Modifier
                .height(16.dp)
                .graphicsLayer {
                    scaleX = 0.7f
                    scaleY = 0.7f
                },
            checked = checked,
            onCheckedChange = null,
            thumbContent = {
                Icon(
                    imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
            colors = SwitchDefaults.colors(
                uncheckedBorderColor = Color(0xFF555555),
                uncheckedTrackColor = Color(0xFF333333),
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedIconColor = Color(0xFF333333),
                checkedThumbColor = Color(0xFF333333),
                checkedTrackColor = Color.White,
                checkedBorderColor = Color.Transparent,
                checkedIconColor = Color.White,
            ),
        )
    }
}

@Composable
@Preview
private fun Preview_Switch() {
    FlipcashPreview {
        SwitchPreview(modifier = Modifier, checked = false)
    }
}