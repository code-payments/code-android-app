package com.flipcash.app.balance.internal

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.runtime.Composable

@Composable
internal actual fun ReportDrawnWhenSeam(predicate: () -> Boolean) = ReportDrawnWhen(predicate)
