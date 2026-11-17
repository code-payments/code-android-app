package com.flipcash.app.balance.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * One of the Money tab's two action buttons (node 13729:744): Add Money is [primary], Withdraw is
 * not. Android draws the app's `CodeButton`, which is Android-only, so this is a seam like
 * [ReportDrawnWhenSeam].
 */
@Composable
internal expect fun MoneyActionButton(
    text: String,
    primary: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
)
