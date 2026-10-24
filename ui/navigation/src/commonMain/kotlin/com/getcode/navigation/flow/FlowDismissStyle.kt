package com.getcode.navigation.flow

import androidx.compose.runtime.staticCompositionLocalOf

enum class FlowDismissStyle { Default, BackArrow, Close }

val LocalFlowDismissStyle = staticCompositionLocalOf { FlowDismissStyle.Default }
