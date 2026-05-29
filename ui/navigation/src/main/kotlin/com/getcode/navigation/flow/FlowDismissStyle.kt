package com.getcode.navigation.flow

import androidx.compose.runtime.compositionLocalOf

enum class FlowDismissStyle { Default, BackArrow, Close }

val LocalFlowDismissStyle = compositionLocalOf { FlowDismissStyle.Default }
