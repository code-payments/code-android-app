package com.flipcash.app.navigation

import androidx.compose.runtime.staticCompositionLocalOf

interface NavigationFlowStep

interface FlowNavigator<S: NavigationFlowStep> {
    fun exit(success: Boolean)
    fun continueFlowFrom(step: S)
}

val LocalFlowNavigator = staticCompositionLocalOf<FlowNavigator<*>> { error("No flow navigator provided") }