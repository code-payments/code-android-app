package com.getcode.oct24.analytics

interface AnalyticsService {
    fun onAppStart()
    fun onAppStarted()
    fun unintentionalLogout()
}

class AnalyticsServiceNull : AnalyticsService {
    override fun onAppStart() = Unit
    override fun onAppStarted() = Unit
    override fun unintentionalLogout() = Unit
}