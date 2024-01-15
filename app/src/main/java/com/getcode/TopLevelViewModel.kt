package com.getcode

import android.app.Activity
import com.getcode.manager.AuthManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TopLevelViewModel @Inject constructor(
    private val authManager: AuthManager,
    resources: ResourceHelper,
) : BaseViewModel(resources) {

    fun logout(activity: Activity, onComplete: () -> Unit = {}) =
        authManager.logout(activity, onComplete)

}
