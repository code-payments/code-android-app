package com.getcode.util

import com.getcode.App
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.utils.ErrorUtils

fun ErrorUtils.showNetworkError() = TopBarManager.TopBarMessage(
    title = App.getInstance().getString(R.string.error_title_noInternet),
    message = App.getInstance().getString(R.string.error_description_noInternet),
    type = TopBarManager.TopBarMessageType.ERROR_NETWORK
).let { TopBarManager.showMessage(it) }