package com.getcode.view.components

import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import com.getcode.theme.White

@Composable
fun CodeCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = White,
    strokeWidth: Dp = ProgressIndicatorDefaults.StrokeWidth,
    backgroundColor: Color = Color.Transparent,
    strokeCap: StrokeCap = StrokeCap.Round,
) = CircularProgressIndicator(modifier, color, strokeWidth, backgroundColor, strokeCap)