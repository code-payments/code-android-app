package com.getcode.view.main.bill

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.Geometry
import com.getcode.view.main.bill.LoginBillDefaults.DecorColor


private object LoginBillDefaults {
    val DecorColor: Color = Color(0xFFA9A9B1)
}

private class LoginBillGeometry(width: Dp, height: Dp): Geometry(width, height) {
    val brandWidth: Dp
        get() = size.width * 0.18f

    val codeWidth: Dp
        get() = size.width * 0.65f
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginBill(
    modifier: Modifier = Modifier,
    data: List<Byte>,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    BoxWithConstraints(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .padding(horizontal = CodeTheme.dimens.inset),
        contentAlignment = Alignment.Center
    ) {
        val mW = maxWidth
        val codeSize = remember { mW * 0.65f }

        BoxWithConstraints(
            modifier = Modifier
                .padding(bottom = screenHeight * 0.10f)
                .clip(CodeTheme.shapes.small)
                .padding(top = CodeTheme.dimens.grid.x12)
                .heightIn(0.dp, 800.dp)
                .aspectRatio(0.68f),
        ) {
            val geometry = remember(maxWidth, maxHeight) {
                LoginBillGeometry(maxWidth, maxHeight)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(loginBillGradient(geometry = geometry), CodeTheme.shapes.small)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                if (data.isNotEmpty()) {
                    ScannableCode(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(geometry.codeWidth),
                        data = data
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Image(
                    modifier = Modifier
                        .align(Alignment.Start)
                        .width(geometry.brandWidth)
                        .padding(CodeTheme.dimens.grid.x2),
                    contentScale = ContentScale.FillWidth,
                    painter = painterResource(
                        R.drawable.ic_code_logo_offwhite_small
                    ),
                    colorFilter = ColorFilter.tint(DecorColor),
                    contentDescription = "",
                )
            }
        }
    }
}

@Composable
private fun loginBillGradient(geometry: Geometry): Brush {
    return Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color(31, 35, 35),
            1f to Color(18, 21, 20)
        ),
        start = Offset(x = geometry.size.width.value * 0.5f, y = 0f),
        end = Offset(x = geometry.size.width.value * 0.5f, y = geometry.size.height.value)
    )
}