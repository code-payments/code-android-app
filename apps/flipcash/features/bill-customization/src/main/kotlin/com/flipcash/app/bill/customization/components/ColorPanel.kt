package com.flipcash.app.bill.customization.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.features.bill.playground.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.utils.Hsv
import com.getcode.ui.utils.color
import com.getcode.ui.utils.hsv

@Composable
internal fun ColorPanel(
    hsv: Hsv,
    modifier: Modifier = Modifier,
    onChange: (Hsv, isDragging: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SaturationBrightnessGrid(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            hsv = hsv,
        ) { hsv, isDragging ->
            onChange(hsv, isDragging)
        }

        HueScroller(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            hsv = hsv,
        ) { hsv, isDragging ->
            onChange(hsv, isDragging)
        }

        Box(
            modifier = Modifier
                .width(50.dp)
                .fillMaxHeight()
                .presenceBorder()
                .background(
                    color = Color.White.copy(0.22f),
                    shape = CodeTheme.shapes.small
                )
                .clip(CodeTheme.shapes.small)
                .rememberedClickable { onClose() }
                .padding(CodeTheme.dimens.grid.x3),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.close_right_arrow),
                contentDescription = "Color manipulation",
                tint = Color.White
            )
        }
    }
}

@Composable
@Preview
private fun ColorPanelPreview() {
    FlipcashDesignSystem {
        val hsv = CodeTheme.colors.cashBillColor.hsv
        var selectedHsv by remember { mutableStateOf(hsv) }

        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .padding(20.dp)
                    .background(Color.hsv(selectedHsv.h, selectedHsv.s, selectedHsv.v))
            )
            Spacer(Modifier.weight(1f))

            ColorPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CodeTheme.dimens.grid.x2)
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .height(114.dp),
                hsv = selectedHsv,
                onChange = { hsv, isDragging ->
                    selectedHsv = hsv
                },
                onClose = {}
            )
        }
    }
}

