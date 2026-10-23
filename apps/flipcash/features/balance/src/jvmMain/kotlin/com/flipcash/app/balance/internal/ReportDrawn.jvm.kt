package com.flipcash.app.balance.internal

import androidx.compose.runtime.Composable

// Desktop has no fully-drawn reporter.
@Composable
internal actual fun ReportDrawnWhenSeam(predicate: () -> Boolean) = Unit
