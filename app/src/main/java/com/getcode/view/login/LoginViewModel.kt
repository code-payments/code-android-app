package com.getcode.view.login

import com.getcode.manager.AnalyticsManager
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


@HiltViewModel
class LoginViewModel @Inject constructor(
    private val analyticsManager: AnalyticsManager
) : BaseViewModel() {
    fun onInit() = analyticsManager.onAppStarted()
}