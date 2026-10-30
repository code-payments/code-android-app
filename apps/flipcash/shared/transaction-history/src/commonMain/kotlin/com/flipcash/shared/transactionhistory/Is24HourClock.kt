package com.flipcash.shared.transactionhistory

import androidx.compose.runtime.Composable

/** Whether the user's clock setting is 24-hour. */
@Composable
internal expect fun is24HourClock(): Boolean
