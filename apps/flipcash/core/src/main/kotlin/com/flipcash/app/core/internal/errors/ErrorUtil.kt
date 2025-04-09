package com.flipcash.app.core.internal.errors

import android.content.Context
import com.flipcash.core.R
import com.getcode.manager.TopBarManager
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.ErrorUtils

fun ErrorUtils.showNetworkError(context: Context) = TopBarManager.TopBarMessage(
    title = context.getString(R.string.error_title_noInternet),
    message = context.getString(R.string.error_description_noInternet),
    type = TopBarManager.TopBarMessageType.ERROR_NETWORK
).let { TopBarManager.showMessage(it) }

fun ErrorUtils.showNetworkError(resources: ResourceHelper) = TopBarManager.TopBarMessage(
    title = resources.getString(R.string.error_title_noInternet),
    message =resources.getString(R.string.error_description_noInternet),
    type = TopBarManager.TopBarMessageType.ERROR_NETWORK
).let { TopBarManager.showMessage(it) }