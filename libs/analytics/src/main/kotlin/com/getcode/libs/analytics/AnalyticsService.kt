package com.getcode.libs.analytics

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

interface AnalyticsService {
    fun onAppStart()
    fun onAppStarted()
    fun unintentionalLogout()
    fun action(action: AppAction, source: AppActionSource? = null)
}

interface AppAction {
    val value: String
}

interface AppActionSource {
    val value: String
}

private class AnalyticsServiceNull: AnalyticsService {
    override fun onAppStart() = Unit
    override fun onAppStarted() = Unit
    override fun unintentionalLogout() = Unit
    override fun action(action: AppAction, source: AppActionSource?) = Unit
}

val LocalAnalytics: ProvidableCompositionLocal<AnalyticsService> = staticCompositionLocalOf { AnalyticsServiceNull() }