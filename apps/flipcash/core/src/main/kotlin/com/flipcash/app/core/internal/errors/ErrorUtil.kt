package com.flipcash.app.core.internal.errors

import android.content.Context
import com.flipcash.core.R
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.ErrorUtils

fun ErrorUtils.showNetworkError(context: Context) {
    BottomBarManager.showError(
        title = context.getString(R.string.error_title_noInternet),
        message = context.getString(R.string.error_description_noInternet)
    )
}

fun ErrorUtils.showNetworkError(resources: ResourceHelper) {
    BottomBarManager.showError(
        title = resources.getString(R.string.error_title_noInternet),
        message = resources.getString(R.string.error_description_noInternet)
    )
}