package com.getcode.view.login

import com.getcode.manager.AnalyticsManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


@HiltViewModel
class LoginViewModel @Inject constructor(
    private val analyticsManager: AnalyticsManager,
    resources: ResourceHelper,
) : BaseViewModel(resources) {
    fun onInit() = analyticsManager.onAppStarted()
}