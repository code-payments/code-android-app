package com.getcode.navigation.screens

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber

interface MainScreen {
    val seed: String?
}