package com.getcode.opencode.compose

import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.opencode.controllers.TransactionController

val LocalTransactionController = staticCompositionLocalOf<TransactionController?> { null }