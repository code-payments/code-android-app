package com.flipcash.shared.chat.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun systemIs24HourClock(): Boolean = DateFormat.is24HourFormat(LocalContext.current)
