package com.getcode.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

val LocalTopBarPadding: ProvidableCompositionLocal<PaddingValues> = staticCompositionLocalOf { PaddingValues() }
