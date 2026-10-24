package com.flipcash.app.balance.internal

import androidx.compose.runtime.Composable

/** Reports the screen as fully drawn once [predicate] is true. Android forwards to `ReportDrawnWhen`. */
@Composable
internal expect fun ReportDrawnWhenSeam(predicate: () -> Boolean)
